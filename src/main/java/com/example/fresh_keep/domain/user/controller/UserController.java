package com.example.fresh_keep.domain.user.controller;

import com.example.fresh_keep.domain.user.dto.UserProfileResponse;
import com.example.fresh_keep.domain.user.repository.UserRepository;
import com.example.fresh_keep.domain.fridge.entity.Compartment;
import com.example.fresh_keep.domain.fridge.entity.Fridge;
import com.example.fresh_keep.domain.fridge.entity.FridgeMember;
import com.example.fresh_keep.domain.fridge.enums.MemberRole;
import com.example.fresh_keep.domain.fridge.repository.CompartmentRepository;
import com.example.fresh_keep.domain.fridge.repository.FridgeMemberRepository;
import com.example.fresh_keep.domain.fridge.repository.FridgeRepository;
import com.example.fresh_keep.domain.ingredient.entity.HistoryActionType;
import com.example.fresh_keep.domain.ingredient.entity.Ingredient;
import com.example.fresh_keep.domain.ingredient.repository.IngredientRepository;
import com.example.fresh_keep.domain.ingredient.service.IngredientService;
import lombok.RequiredArgsConstructor;
import lombok.Data;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.example.fresh_keep.domain.user.entity.User;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final FridgeMemberRepository fridgeMemberRepository;
    private final FridgeRepository fridgeRepository;
    private final CompartmentRepository compartmentRepository;
    private final IngredientRepository ingredientRepository;
    private final IngredientService ingredientService;
    private final CacheManager cacheManager;
    private final com.example.fresh_keep.global.security.jwt.RefreshTokenSessionService refreshTokenSessionService;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> me(@AuthenticationPrincipal Object principal) {
        if (!(principal instanceof Long userId)) {
            return ResponseEntity.status(401).build();
        }

        return userRepository.findById(userId)
                .map(user -> ResponseEntity.ok(UserProfileResponse.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .name(user.getName())
                        .provider(user.getProvider())
                        .build()))
                .orElse(ResponseEntity.notFound().build());
    }

    @Transactional
    @PatchMapping("/me")
    public ResponseEntity<?> updateProfile(
            @AuthenticationPrincipal Object principal,
            @RequestBody NicknameUpdateRequest request) {
        if (!(principal instanceof Long userId)) {
            return ResponseEntity.status(401).body(Map.of("message", "UNAUTHORIZED"));
        }

        if (request.getName() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "NICKNAME_REQUIRED"));
        }

        String name = request.getName().trim();

        // 1. 특수문자, 공백, 이모지 방지 및 2~8자 길이 검증
        // 한글, 영문, 숫자만 허용
        if (!name.matches("^[a-zA-Z0-9가-힣]{2,8}$")) {
            return ResponseEntity.badRequest().body(Map.of("message", "INVALID_NICKNAME"));
        }

        // 2. 중복 검사
        if (userRepository.existsByNameAndIdNot(name, userId)) {
            return ResponseEntity.status(409).body(Map.of("message", "DUPLICATE_NICKNAME"));
        }

        return userRepository.findById(userId)
                .map(user -> {
                    String oldName = user.getName();
                    user.updateName(name);
                    User savedUser = userRepository.save(user);

                    if (!name.equals(oldName)) {
                        onNicknameChanged(userId, oldName, name);
                    }

                    return ResponseEntity.ok(UserProfileResponse.builder()
                            .id(savedUser.getId())
                            .email(savedUser.getEmail())
                            .name(savedUser.getName())
                            .provider(savedUser.getProvider())
                            .build());
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // 닉네임이 공유 냉장고에 노출되는 두 군데(멤버 목록 캐시, 기록 이력)를 갱신한다.
    // "fridges"/"fridgeLayout" 캐시는 조회자 관점으로 키가 잡혀 있어, 이 유저가 속한 냉장고를
    // 함께 쓰는 모든 멤버(본인 포함)의 캐시를 지워야 다음 조회 때 새 닉네임이 반영된다.
    private void onNicknameChanged(Long userId, String oldName, String newName) {
        List<FridgeMember> myMemberships = fridgeMemberRepository.findByUserId(userId);
        if (myMemberships.isEmpty()) {
            return;
        }

        List<Long> fridgeIds = myMemberships.stream()
                .map(m -> m.getFridge().getId())
                .distinct()
                .toList();

        List<Long> affectedUserIds = fridgeMemberRepository.findByFridgeIdIn(fridgeIds).stream()
                .map(m -> m.getUser().getId())
                .distinct()
                .toList();

        Cache fridgesCache = cacheManager.getCache("fridges");
        if (fridgesCache != null) {
            affectedUserIds.forEach(fridgesCache::evict);
        }
        Cache fridgeLayoutCache = cacheManager.getCache("fridgeLayout");
        if (fridgeLayoutCache != null) {
            fridgeIds.forEach(fridgeLayoutCache::evict);
        }

        for (FridgeMember membership : myMemberships) {
            ingredientService.saveHistory(membership.getFridge().getId(), membership.getFridge().getName(),
                    HistoryActionType.NICKNAME_CHANGED, userId, oldName + "에서 " + newName + "으로 변경했습니다.");
        }
    }

    @Transactional
    @PatchMapping("/me/push-token")
    public ResponseEntity<?> updatePushToken(
            @AuthenticationPrincipal Object principal,
            @RequestBody PushTokenUpdateRequest request) {
        if (!(principal instanceof Long userId)) {
            return ResponseEntity.status(401).body(Map.of("message", "UNAUTHORIZED"));
        }

        return userRepository.findById(userId)
                .map(user -> {
                    user.updateExpoPushToken(request.getExpoPushToken());
                    userRepository.save(user);
                    return ResponseEntity.ok().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Transactional
    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal Object principal) {
        if (!(principal instanceof Long userId)) {
            return ResponseEntity.status(401).build();
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        // 1. 해당 유저가 속한 모든 냉장고 관계 조회
        List<FridgeMember> members = fridgeMemberRepository.findByUserId(userId);

        // 소유 냉장고가 통째로 삭제되면 공유받던 다른 멤버 화면에서도 즉시 사라져야 하므로,
        // 삭제 전에 영향받는 냉장고/공유자 목록을 모아 두었다가 아래에서 캐시를 무효화한다.
        List<Long> deletedFridgeIds = new java.util.ArrayList<>();
        java.util.Set<Long> affectedMemberUserIds = new java.util.HashSet<>();

        for (FridgeMember member : members) {
            Fridge fridge = member.getFridge();
            if (member.getRole() == MemberRole.OWNER) {
                List<FridgeMember> fridgeMembers = fridgeMemberRepository.findByFridgeId(fridge.getId());
                fridgeMembers.forEach(m -> affectedMemberUserIds.add(m.getUser().getId()));
                deletedFridgeIds.add(fridge.getId());

                // 2. 소유주인 경우 냉장고와 연관 데이터 완전 삭제
                List<Ingredient> ingredients = ingredientRepository.findByFridgeId(fridge.getId());
                ingredientRepository.deleteAll(ingredients);

                List<Compartment> compartments = compartmentRepository.findByFridgeIdOrderBySequenceOrderAsc(fridge.getId());
                compartmentRepository.deleteAll(compartments);

                fridgeMemberRepository.deleteAll(fridgeMembers);

                fridgeRepository.delete(fridge);
            } else {
                // 3. 공동 관리 멤버인 경우 본인의 멤버 관계만 삭제
                fridgeMemberRepository.delete(member);
            }
        }

        // 4. 유저 삭제 (provider·providerId·deviceUuid 등 소셜 연동 정보는 users 컬럼이라 함께 제거됨)
        userRepository.delete(user);

        // 5. Redis refresh 세션(IP·UA 포함) 제거 — 처리방침상 탈퇴 시 잔여 데이터를 남기지 않는다.
        refreshTokenSessionService.revoke(userId);

        // 6. 삭제된 냉장고를 보고 있던 공유자들의 목록/레이아웃 캐시를 비워 유령 냉장고가 남지 않게 한다.
        Cache fridgesCache = cacheManager.getCache("fridges");
        if (fridgesCache != null) {
            affectedMemberUserIds.forEach(fridgesCache::evict);
        }
        Cache fridgeLayoutCache = cacheManager.getCache("fridgeLayout");
        if (fridgeLayoutCache != null) {
            deletedFridgeIds.forEach(fridgeLayoutCache::evict);
        }

        return ResponseEntity.noContent().build();
    }

    @Data
    public static class NicknameUpdateRequest {
        private String name;
    }

    @Data
    public static class PushTokenUpdateRequest {
        private String expoPushToken;
    }
}
