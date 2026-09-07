package com.example.fresh_keep.domain.memo.service;

import com.example.fresh_keep.domain.fridge.entity.FridgeMember;
import com.example.fresh_keep.domain.fridge.repository.FridgeMemberRepository;
import com.example.fresh_keep.domain.memo.dto.ChecklistItemDto;
import com.example.fresh_keep.domain.memo.dto.CreateMemoRequest;
import com.example.fresh_keep.domain.memo.dto.MemoResponse;
import com.example.fresh_keep.domain.memo.dto.UpdateMemoRequest;
import com.example.fresh_keep.domain.memo.entity.FridgeMemo;
import com.example.fresh_keep.domain.memo.entity.MemoType;
import com.example.fresh_keep.domain.memo.repository.FridgeMemoRepository;
import com.example.fresh_keep.domain.user.entity.User;
import com.example.fresh_keep.domain.user.repository.UserRepository;
import com.example.fresh_keep.global.notification.PushNotificationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemoService {

    private final FridgeMemoRepository fridgeMemoRepository;
    private final FridgeMemberRepository fridgeMemberRepository;
    private final UserRepository userRepository;
    private final CacheManager cacheManager;
    private final PushNotificationService pushNotificationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<MemoResponse> list(Long fridgeId, Long userId) {
        requireMember(fridgeId, userId);
        return fridgeMemoRepository.findByFridgeIdOrderByCreatedAtDesc(fridgeId).stream()
                .map(memo -> toResponse(memo, userId))
                .collect(Collectors.toList());
    }

    @Transactional
    public MemoResponse create(Long fridgeId, Long userId, CreateMemoRequest request) {
        requireMember(fridgeId, userId);

        FridgeMemo memo = FridgeMemo.builder()
                .fridgeId(fridgeId)
                .authorUserId(userId)
                .type(request.getType())
                .content(request.getContent())
                .build();
        fridgeMemoRepository.save(memo);

        notifyOtherMembers(fridgeId, userId, request.getType(), request.getContent());
        evictFridgesCacheForMembers(fridgeId);

        return toResponse(memo, userId);
    }

    @Transactional
    public MemoResponse update(Long fridgeId, Long memoId, Long userId, UpdateMemoRequest request) {
        FridgeMemo memo = getOwnedMemo(fridgeId, memoId, userId, "수정");
        memo.updateContent(request.getContent());
        return toResponse(memo, userId);
    }

    @Transactional
    public void delete(Long fridgeId, Long memoId, Long userId) {
        FridgeMemo memo = getOwnedMemo(fridgeId, memoId, userId, "삭제");
        fridgeMemoRepository.delete(memo);
    }

    // 체크리스트 항목 체크/해제는 작성자가 아니어도 같은 냉장고 멤버면 누구나 할 수 있다
    // (실제로 장 보러 가는 사람이 작성자가 아닐 수 있으므로). 문구/순서는 건드리지 않는다.
    @Transactional
    public MemoResponse toggleItem(Long fridgeId, Long memoId, String itemId, Long userId) {
        requireMember(fridgeId, userId);

        FridgeMemo memo = fridgeMemoRepository.findById(memoId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 메모입니다."));
        if (!memo.getFridgeId().equals(fridgeId)) {
            throw new IllegalArgumentException("해당 냉장고의 메모가 아닙니다.");
        }
        if (memo.getType() != MemoType.CHECKLIST) {
            throw new IllegalArgumentException("체크리스트 메모가 아닙니다.");
        }

        List<ChecklistItemDto> items = parseChecklist(memo.getContent());
        ChecklistItemDto target = items.stream()
                .filter(item -> itemId.equals(item.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 항목입니다."));
        target.setChecked(!target.isChecked());
        memo.updateContent(writeChecklist(items));

        return toResponse(memo, userId);
    }

    @Transactional
    public void markRead(Long fridgeId, Long userId) {
        FridgeMember member = fridgeMemberRepository.findByFridgeIdAndUserId(fridgeId, userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 냉장고에 접근할 권한이 없습니다."));
        member.markMemoViewed();

        Cache cache = cacheManager.getCache("fridges");
        if (cache != null) {
            cache.evict(userId);
        }
    }

    private void requireMember(Long fridgeId, Long userId) {
        if (!fridgeMemberRepository.existsByFridgeIdAndUserId(fridgeId, userId)) {
            throw new IllegalArgumentException("해당 냉장고에 접근할 권한이 없습니다.");
        }
    }

    private FridgeMemo getOwnedMemo(Long fridgeId, Long memoId, Long userId, String action) {
        FridgeMemo memo = fridgeMemoRepository.findById(memoId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 메모입니다."));
        if (!memo.getFridgeId().equals(fridgeId)) {
            throw new IllegalArgumentException("해당 냉장고의 메모가 아닙니다.");
        }
        if (!memo.getAuthorUserId().equals(userId)) {
            throw new IllegalArgumentException("본인이 작성한 메모만 " + action + "할 수 있습니다.");
        }
        return memo;
    }

    private List<ChecklistItemDto> parseChecklist(String content) {
        try {
            return objectMapper.readValue(content, new TypeReference<List<ChecklistItemDto>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("메모 형식이 올바르지 않습니다.");
        }
    }

    private String writeChecklist(List<ChecklistItemDto> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) {
            throw new IllegalStateException("메모 저장 중 오류가 발생했습니다.");
        }
    }

    // 새 메모를 다른 멤버들(본인 제외)에게 푸시로 알린다. IngredientService.notifyOtherMembers와 같은 패턴.
    private void notifyOtherMembers(Long fridgeId, Long actorUserId, MemoType type, String content) {
        List<FridgeMember> others = fridgeMemberRepository.findByFridgeId(fridgeId).stream()
                .filter(m -> !m.getUser().getId().equals(actorUserId))
                .collect(Collectors.toList());
        if (others.isEmpty()) return;

        String actorName = userRepository.findById(actorUserId).map(User::getName).orElse(null);
        String snippet = buildSnippet(type, content);
        String title = "새 메모";
        String body = (actorName != null ? actorName + "님이 " : "") + "메모를 남겼어요: " + snippet;
        Map<String, Object> data = Map.of("type", "memo_created");

        others.forEach(m -> pushNotificationService.send(m.getUser().getExpoPushToken(), title, body, data));
    }

    // 푸시 본문에 넣을 짧은 미리보기. CHECKLIST는 content가 JSON이라 그대로 자르면 안 되고
    // 파싱해서 사람이 읽을 문구로 바꿔야 한다 (getMemoPreview 프론트 로직과 같은 방식).
    private String buildSnippet(MemoType type, String content) {
        if (type == MemoType.CHECKLIST) {
            List<ChecklistItemDto> items = parseChecklistQuietly(content);
            if (items.isEmpty()) return "체크리스트";
            String rest = items.size() > 1 ? " 외 " + (items.size() - 1) + "개" : "";
            return items.get(0).getText() + rest;
        }
        String singleLine = content.replace("\n", " ");
        return singleLine.length() > 30 ? singleLine.substring(0, 30) + "..." : singleLine;
    }

    private List<ChecklistItemDto> parseChecklistQuietly(String content) {
        try {
            return objectMapper.readValue(content, new TypeReference<List<ChecklistItemDto>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    // 안읽음 배지가 fridges 응답(getFridges)에 실려 캐시되므로, 새 메모가 생기면 그 냉장고
    // 멤버 전원의 캐시를 지워야 다음 조회 때 배지가 즉시 반영된다 (닉네임 변경과 동일한 패턴).
    private void evictFridgesCacheForMembers(Long fridgeId) {
        Cache cache = cacheManager.getCache("fridges");
        if (cache == null) return;
        fridgeMemberRepository.findByFridgeId(fridgeId)
                .forEach(m -> cache.evict(m.getUser().getId()));
    }

    private MemoResponse toResponse(FridgeMemo memo, Long viewerUserId) {
        String authorName = userRepository.findById(memo.getAuthorUserId()).map(User::getName).orElse(null);
        return MemoResponse.builder()
                .id(memo.getId())
                .fridgeId(memo.getFridgeId())
                .authorUserId(memo.getAuthorUserId())
                .authorName(authorName)
                .type(memo.getType())
                .content(memo.getContent())
                .mine(memo.getAuthorUserId().equals(viewerUserId))
                .createdAt(memo.getCreatedAt())
                .updatedAt(memo.getUpdatedAt())
                .build();
    }
}
