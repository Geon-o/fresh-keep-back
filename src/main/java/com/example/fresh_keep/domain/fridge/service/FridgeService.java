package com.example.fresh_keep.domain.fridge.service;

import com.example.fresh_keep.domain.fridge.dto.CreateFridgeRequest;
import com.example.fresh_keep.domain.fridge.dto.FridgeDeletionResponse;
import com.example.fresh_keep.domain.fridge.dto.FridgeResponse;
import com.example.fresh_keep.domain.fridge.entity.Compartment;
import com.example.fresh_keep.domain.fridge.entity.Fridge;
import com.example.fresh_keep.domain.fridge.entity.FridgeMember;
import com.example.fresh_keep.domain.fridge.enums.FridgeType;
import com.example.fresh_keep.domain.fridge.enums.MemberRole;
import com.example.fresh_keep.domain.fridge.enums.StorageType;
import com.example.fresh_keep.domain.fridge.repository.CompartmentRepository;
import com.example.fresh_keep.domain.fridge.repository.FridgeMemberRepository;
import com.example.fresh_keep.domain.fridge.repository.FridgeRepository;
import com.example.fresh_keep.domain.user.entity.User;
import com.example.fresh_keep.domain.user.repository.UserRepository;
import com.example.fresh_keep.domain.fridge.dto.CompartmentDetailResponse;
import com.example.fresh_keep.domain.fridge.dto.FridgeLayoutResponse;
import com.example.fresh_keep.domain.fridge.dto.UpdateFridgeRequest;
import com.example.fresh_keep.domain.fridge.dto.UpdateShelvesRequest;
import com.example.fresh_keep.domain.ingredient.dto.IngredientDetailResponse;
import com.example.fresh_keep.domain.ingredient.entity.Ingredient;
import com.example.fresh_keep.domain.ingredient.enums.ExpirationType;
import com.example.fresh_keep.domain.ingredient.repository.IngredientRepository;
import com.example.fresh_keep.global.notification.PushNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.stream.Collectors;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FridgeService {

    private final FridgeRepository fridgeRepository;
    private final FridgeMemberRepository fridgeMemberRepository;
    private final CompartmentRepository compartmentRepository;
    private final UserRepository userRepository;
    private final IngredientRepository ingredientRepository;
    private final CacheManager cacheManager;
    private final PushNotificationService pushNotificationService;

    // 삭제 요청/동의/철회처럼 나 아닌 다른 멤버의 화면에도 반영돼야 하는 변경 후,
    // @CacheEvict(key = 현재 유저)만으로는 안 닿는 다른 멤버들의 "fridges" 캐시를 직접 비운다.
    private void evictFridgesCacheFor(Long userId) {
        Cache cache = cacheManager.getCache("fridges");
        if (cache != null) {
            cache.evict(userId);
        }
    }

    @Transactional
    @CacheEvict(value = "fridges", key = "#p1")
    public FridgeResponse createFridge(CreateFridgeRequest request, Long userId) {
        // 1. 유저 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        // 2. 냉장고 생성 및 저장
        Fridge fridge = Fridge.builder()
                .name(request.getName())
                .type(request.getType())
                .build();
        fridgeRepository.save(fridge);

        // 3. 냉장고 멤버(소유주) 등록
        FridgeMember fridgeMember = FridgeMember.builder()
                .user(user)
                .fridge(fridge)
                .role(MemberRole.OWNER)
                .build();
        fridgeMemberRepository.save(fridgeMember);

        // 4. 냉장고 타입별 기본 구획(Compartments) 생성
        createDefaultCompartments(fridge);

        return FridgeResponse.builder()
                .id(fridge.getId())
                .name(fridge.getName())
                .type(fridge.getType())
                .role(MemberRole.OWNER)
                .uuid(fridge.getUuid())
                .build();
    }

    @Cacheable(value = "fridges", key = "#p0")
    public List<FridgeResponse> getFridges(Long userId) {
        List<FridgeMember> members = fridgeMemberRepository.findByUserId(userId);
        return members.stream()
                .map(m -> {
                    List<FridgeMember> fridgeMembers = fridgeMemberRepository.findByFridgeId(m.getFridge().getId());
                    String ownerName = fridgeMembers.stream()
                            .filter(fm -> fm.getRole() == MemberRole.OWNER)
                            .map(fm -> fm.getUser().getName())
                            .findFirst()
                            .orElse(null);
                    // 주인을 맨 앞에 두고 나머지 멤버 이름을 뒤에 붙인다. 1명뿐이면 공유 안 하는 상태.
                    List<String> memberNames = fridgeMembers.stream()
                            .sorted((a, b) -> a.getRole() == MemberRole.OWNER ? -1 : b.getRole() == MemberRole.OWNER ? 1 : 0)
                            .map(fm -> fm.getUser().getName())
                            .collect(Collectors.toList());
                    return FridgeResponse.builder()
                            .id(m.getFridge().getId())
                            .name(m.getFridge().getName())
                            .type(m.getFridge().getType())
                            .role(m.getRole())
                            .uuid(m.getFridge().getUuid())
                            .deletionRequested(m.getFridge().isDeletionRequested())
                            .ownerName(ownerName)
                            .memberNames(memberNames)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Cacheable(value = "fridgeLayout", key = "#p0")
    public FridgeLayoutResponse getFridgeLayout(Long fridgeId, Long userId) {
        // 1. 권한 검증
        if (!fridgeMemberRepository.existsByFridgeIdAndUserId(fridgeId, userId)) {
            throw new IllegalArgumentException("해당 냉장고에 대한 접근 권한이 없습니다.");
        }

        // 2. 냉장고 및 구획 조회
        Fridge fridge = fridgeRepository.findById(fridgeId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 냉장고입니다."));
        List<Compartment> compartments = compartmentRepository.findByFridgeIdOrderBySequenceOrderAsc(fridgeId);

        // 3. 식재료 일괄 조회 및 구획별 그룹화
        List<Ingredient> ingredients = ingredientRepository.findByCompartmentFridgeId(fridgeId);
        Map<Long, List<Ingredient>> ingredientsByCompartment = ingredients.stream()
                .collect(Collectors.groupingBy(ing -> ing.getCompartment().getId()));

        LocalDate now = LocalDate.now();

        // 4. 구획별 상세 DTO 매핑
        List<CompartmentDetailResponse> compartmentResponses = compartments.stream()
                .map(comp -> {
                    List<Ingredient> compIngredients = ingredientsByCompartment.getOrDefault(comp.getId(), new ArrayList<>());
                    List<IngredientDetailResponse> ingredientResponses = compIngredients.stream()
                            .map(ing -> IngredientDetailResponse.builder()
                                    .id(ing.getId())
                                    .name(ing.getName())
                                    .quantity(ing.getQuantity())
                                    .unit(ing.getUnit())
                                    .expirationDate(ing.getExpirationDate())
                                    .expirationType(ing.getExpirationType() != null ? ing.getExpirationType() : ExpirationType.SELL_BY)
                                    .dday(ChronoUnit.DAYS.between(now, ing.getExpirationDate()))
                                    .memo(ing.getMemo())
                                    .build())
                            .collect(Collectors.toList());

                    return CompartmentDetailResponse.builder()
                            .id(comp.getId())
                            .name(comp.getName())
                            .storageType(comp.getStorageType())
                            .sequenceOrder(comp.getSequenceOrder())
                            .insideShelves(comp.getInsideShelves())
                            .doorShelves(comp.getDoorShelves())
                            .hasDoorStorage(comp.getHasDoorStorage())
                            .ingredients(ingredientResponses)
                            .build();
                })
                .collect(Collectors.toList());

        return FridgeLayoutResponse.builder()
                .fridgeId(fridge.getId())
                .fridgeName(fridge.getName())
                .type(fridge.getType())
                .compartments(compartmentResponses)
                .build();
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "fridges", key = "#p2"),
        @CacheEvict(value = "fridgeLayout", key = "#p0")
    })
    public FridgeResponse updateFridge(Long fridgeId, UpdateFridgeRequest request, Long userId) {
        // 1. 권한 검증
        if (!fridgeMemberRepository.existsByFridgeIdAndUserId(fridgeId, userId)) {
            throw new IllegalArgumentException("해당 냉장고에 대한 수정 권한이 없습니다.");
        }

        // 2. 냉장고 조회
        Fridge fridge = fridgeRepository.findById(fridgeId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 냉장고입니다."));

        FridgeType oldType = fridge.getType();
        FridgeType newType = request.getType();

        // 3. 냉장고 이름 및 타입 변경
        fridge.update(request.getName(), newType);

        // 4. 타입이 변경되었다면 기존 내용물(식재료) 및 구획 전부 제거 후 새 구획 생성
        if (oldType != newType) {
            // 기존 식재료 전부 제거
            List<Ingredient> ingredients = ingredientRepository.findByCompartmentFridgeId(fridgeId);
            ingredientRepository.deleteAll(ingredients);

            // 기존 구획 전부 제거
            List<Compartment> oldCompartments = compartmentRepository.findByFridgeIdOrderBySequenceOrderAsc(fridgeId);
            compartmentRepository.deleteAll(oldCompartments);

            // 새 타입에 따른 기본 구획 생성
            createDefaultCompartments(fridge);
        }

        MemberRole role = fridgeMemberRepository.findByUserId(userId).stream()
                .filter(m -> m.getFridge().getId().equals(fridgeId))
                .map(FridgeMember::getRole)
                .findFirst()
                .orElse(MemberRole.OWNER);

        return FridgeResponse.builder()
                .id(fridge.getId())
                .name(fridge.getName())
                .type(fridge.getType())
                .role(role)
                .uuid(fridge.getUuid())
                .deletionRequested(fridge.isDeletionRequested())
                .build();
    }

    // 삭제 시도: 혼자 쓰는 냉장고는 즉시 삭제하지만, 다른 멤버와 공유 중이면 즉시 지우지 않고
    // "삭제 요청" 상태로만 전환해 다른 멤버 전원의 동의를 받아야 실제로 삭제되게 한다.
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "fridges", key = "#p1"),
        @CacheEvict(value = "fridgeLayout", key = "#p0")
    })
    public FridgeDeletionResponse deleteFridge(Long fridgeId, Long userId) {
        FridgeMember requester = fridgeMemberRepository.findByFridgeIdAndUserId(fridgeId, userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 냉장고에 대한 삭제 권한이 없습니다."));

        // OWNER가 아닌 MEMBER는 냉장고 전체를 삭제할 수 없고, 본인 멤버십만 제거(나가기)한다.
        if (requester.getRole() != MemberRole.OWNER) {
            fridgeMemberRepository.delete(requester);
            return FridgeDeletionResponse.builder().deleted(false).build();
        }

        List<FridgeMember> allMembers = fridgeMemberRepository.findByFridgeId(fridgeId);
        List<FridgeMember> otherMembers = allMembers.stream()
                .filter(m -> !m.getUser().getId().equals(userId))
                .collect(Collectors.toList());

        if (otherMembers.isEmpty()) {
            performFullDelete(fridgeId);
            return FridgeDeletionResponse.builder().deleted(true).build();
        }

        Fridge fridge = fridgeRepository.findById(fridgeId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 냉장고입니다."));
        fridge.requestDeletion();
        otherMembers.forEach(FridgeMember::resetDeletionApproval);
        otherMembers.forEach(m -> evictFridgesCacheFor(m.getUser().getId()));

        String ownerName = requester.getUser().getName();
        otherMembers.forEach(m -> pushNotificationService.send(
                m.getUser().getExpoPushToken(),
                "냉장고 삭제 요청",
                ownerName + "님이 '" + fridge.getName() + "' 삭제를 요청했습니다. 확인해주세요.",
                deletionPushData(fridgeId)
        ));

        return FridgeDeletionResponse.builder().deleted(false).build();
    }

    // 공유 중인 멤버 전원이 삭제에 동의하면 그 시점에 실제로 삭제를 실행한다.
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "fridges", key = "#p1"),
        @CacheEvict(value = "fridgeLayout", key = "#p0")
    })
    public FridgeDeletionResponse approveDeletion(Long fridgeId, Long userId) {
        Fridge fridge = fridgeRepository.findById(fridgeId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 냉장고입니다."));
        if (!fridge.isDeletionRequested()) {
            throw new IllegalArgumentException("진행 중인 삭제 요청이 없습니다.");
        }

        FridgeMember requester = fridgeMemberRepository.findByFridgeIdAndUserId(fridgeId, userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 냉장고에 대한 권한이 없습니다."));
        if (requester.getRole() == MemberRole.OWNER) {
            throw new IllegalArgumentException("본인이 요청한 삭제는 동의할 수 없습니다.");
        }

        requester.approveDeletion();

        List<FridgeMember> allMembers = fridgeMemberRepository.findByFridgeId(fridgeId);
        boolean allOthersApproved = allMembers.stream()
                .filter(m -> m.getRole() != MemberRole.OWNER)
                .allMatch(FridgeMember::isDeletionApproved);

        if (!allOthersApproved) {
            return FridgeDeletionResponse.builder().deleted(false).build();
        }

        String fridgeName = fridge.getName();
        FridgeMember owner = allMembers.stream()
                .filter(m -> m.getRole() == MemberRole.OWNER)
                .findFirst()
                .orElse(null);

        performFullDelete(fridgeId);
        // 최종 승인으로 실제 삭제가 확정된 시점이므로, 이미 먼저 동의했던 다른 멤버들의 캐시도 함께 비운다.
        allMembers.forEach(m -> evictFridgesCacheFor(m.getUser().getId()));
        if (owner != null) {
            pushNotificationService.send(
                    owner.getUser().getExpoPushToken(),
                    "냉장고 삭제 완료",
                    "'" + fridgeName + "'이 멤버 전원의 동의로 삭제되었습니다.",
                    deletionPushData(fridgeId)
            );
        }
        return FridgeDeletionResponse.builder().deleted(true).build();
    }

    // 멤버 중 한 명이라도 거절하면 삭제 요청 자체를 취소한다.
    @Transactional
    @CacheEvict(value = "fridges", key = "#p1")
    public void rejectDeletion(Long fridgeId, Long userId) {
        FridgeMember requester = fridgeMemberRepository.findByFridgeIdAndUserId(fridgeId, userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 냉장고에 대한 권한이 없습니다."));

        List<FridgeMember> members = clearDeletionRequestState(fridgeId);
        FridgeMember owner = members.stream()
                .filter(m -> m.getRole() == MemberRole.OWNER)
                .findFirst()
                .orElse(null);
        if (owner != null) {
            pushNotificationService.send(
                    owner.getUser().getExpoPushToken(),
                    "냉장고 삭제 요청 거절",
                    requester.getUser().getName() + "님이 냉장고 삭제 요청을 거절했습니다.",
                    deletionPushData(fridgeId)
            );
        }
    }

    // 주인은 본인이 보낸 삭제 요청을 언제든 철회할 수 있다.
    @Transactional
    @CacheEvict(value = "fridges", key = "#p1")
    public void cancelDeletionRequest(Long fridgeId, Long userId) {
        FridgeMember requester = fridgeMemberRepository.findByFridgeIdAndUserId(fridgeId, userId)
                .orElseThrow(() -> new IllegalArgumentException("해당 냉장고에 대한 권한이 없습니다."));
        if (requester.getRole() != MemberRole.OWNER) {
            throw new IllegalArgumentException("삭제 요청은 냉장고 주인만 철회할 수 있습니다.");
        }

        List<FridgeMember> members = clearDeletionRequestState(fridgeId);
        String ownerName = requester.getUser().getName();
        members.stream()
                .filter(m -> m.getRole() != MemberRole.OWNER)
                .forEach(m -> pushNotificationService.send(
                        m.getUser().getExpoPushToken(),
                        "냉장고 삭제 요청 철회",
                        ownerName + "님이 삭제 요청을 철회했습니다.",
                        deletionPushData(fridgeId)
                ));
    }

    private Map<String, Object> deletionPushData(Long fridgeId) {
        return Map.of("type", "fridge_deletion", "fridgeId", fridgeId);
    }

    // 삭제 요청 상태를 초기화하고, 이후 알림 발송에 쓸 수 있도록 멤버 목록을 반환한다.
    private List<FridgeMember> clearDeletionRequestState(Long fridgeId) {
        Fridge fridge = fridgeRepository.findById(fridgeId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 냉장고입니다."));
        fridge.cancelDeletionRequest();

        List<FridgeMember> members = fridgeMemberRepository.findByFridgeId(fridgeId);
        members.forEach(FridgeMember::resetDeletionApproval);
        members.forEach(m -> evictFridgesCacheFor(m.getUser().getId()));
        return members;
    }

    private void performFullDelete(Long fridgeId) {
        List<Ingredient> ingredients = ingredientRepository.findByCompartmentFridgeId(fridgeId);
        ingredientRepository.deleteAll(ingredients);

        List<Compartment> compartments = compartmentRepository.findByFridgeIdOrderBySequenceOrderAsc(fridgeId);
        compartmentRepository.deleteAll(compartments);

        List<FridgeMember> members = fridgeMemberRepository.findByFridgeId(fridgeId);
        fridgeMemberRepository.deleteAll(members);

        fridgeRepository.deleteById(fridgeId);
    }

    @Transactional
    @CacheEvict(value = "fridgeLayout", key = "#p0")
    public void updateCompartmentShelves(Long fridgeId, Long compartmentId, UpdateShelvesRequest request, Long userId) {
        if (!fridgeMemberRepository.existsByFridgeIdAndUserId(fridgeId, userId)) {
            throw new IllegalArgumentException("해당 냉장고에 대한 수정 권한이 없습니다.");
        }
        Compartment compartment = compartmentRepository.findById(compartmentId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 구획입니다."));
        if (!compartment.getFridge().getId().equals(fridgeId)) {
            throw new IllegalArgumentException("올바르지 않은 구획 정보입니다.");
        }
        compartment.updateShelves(request.getInsideShelves(), request.getDoorShelves(), request.getHasDoorStorage());
    }

    private void createDefaultCompartments(Fridge fridge) {
        List<Compartment> compartments = new ArrayList<>();
        if (fridge.getType() == FridgeType.FOUR_DOOR) {
            compartments.add(Compartment.builder()
                    .fridge(fridge)
                    .name("상단 좌측 냉장실")
                    .storageType(StorageType.REFRIGERATED)
                    .sequenceOrder(1)
                    .build());
            compartments.add(Compartment.builder()
                    .fridge(fridge)
                    .name("상단 우측 냉장실")
                    .storageType(StorageType.REFRIGERATED)
                    .sequenceOrder(2)
                    .build());
            compartments.add(Compartment.builder()
                    .fridge(fridge)
                    .name("하단 좌측 냉동실")
                    .storageType(StorageType.FROZEN)
                    .sequenceOrder(3)
                    .build());
            compartments.add(Compartment.builder()
                    .fridge(fridge)
                    .name("하단 우측 냉동실")
                    .storageType(StorageType.FROZEN)
                    .sequenceOrder(4)
                    .build());
        } else if (fridge.getType() == FridgeType.SIDE_BY_SIDE) {
            compartments.add(Compartment.builder()
                    .fridge(fridge)
                    .name("냉동실")
                    .storageType(StorageType.FROZEN)
                    .sequenceOrder(1)
                    .build());
            compartments.add(Compartment.builder()
                    .fridge(fridge)
                    .name("냉장실")
                    .storageType(StorageType.REFRIGERATED)
                    .sequenceOrder(2)
                    .build());
        } else if (fridge.getType() == FridgeType.TWO_DOOR) {
            compartments.add(Compartment.builder()
                    .fridge(fridge)
                    .name("냉동실")
                    .storageType(StorageType.FROZEN)
                    .sequenceOrder(1)
                    .build());
            compartments.add(Compartment.builder()
                    .fridge(fridge)
                    .name("냉장실")
                    .storageType(StorageType.REFRIGERATED)
                    .sequenceOrder(2)
                    .build());
        }

        compartmentRepository.saveAll(compartments);
    }

    @Transactional
    @CacheEvict(value = "fridges", key = "#p1")
    public FridgeResponse shareFridge(String fridgeUuid, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        Fridge fridge = fridgeRepository.findByUuid(fridgeUuid)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않거나 유효하지 않은 QR 코드의 냉장고입니다."));

        List<FridgeMember> allMembers = fridgeMemberRepository.findByFridgeId(fridge.getId());
        java.util.Optional<FridgeMember> existingMember = allMembers.stream()
                .filter(m -> m.getUser().getId().equals(userId))
                .findFirst();

        if (existingMember.isPresent()) {
            return FridgeResponse.builder()
                    .id(fridge.getId())
                    .name(fridge.getName())
                    .type(fridge.getType())
                    .role(existingMember.get().getRole())
                    .uuid(fridge.getUuid())
                    .deletionRequested(fridge.isDeletionRequested())
                    .build();
        }

        FridgeMember fridgeMember = FridgeMember.builder()
                .user(user)
                .fridge(fridge)
                .role(MemberRole.MEMBER)
                .build();
        fridgeMemberRepository.save(fridgeMember);

        return FridgeResponse.builder()
                .id(fridge.getId())
                .name(fridge.getName())
                .type(fridge.getType())
                .role(MemberRole.MEMBER)
                .uuid(fridge.getUuid())
                .deletionRequested(fridge.isDeletionRequested())
                .build();
    }
}
