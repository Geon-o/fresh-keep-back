package com.example.fresh_keep.domain.fridge.service;

import com.example.fresh_keep.domain.fridge.dto.CreateFridgeRequest;
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
import com.example.fresh_keep.domain.ingredient.repository.IngredientRepository;
import lombok.RequiredArgsConstructor;
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
                .build();
    }

    @Cacheable(value = "fridges", key = "#p0")
    public List<FridgeResponse> getFridges(Long userId) {
        List<FridgeMember> members = fridgeMemberRepository.findByUserId(userId);
        return members.stream()
                .map(m -> FridgeResponse.builder()
                        .id(m.getFridge().getId())
                        .name(m.getFridge().getName())
                        .type(m.getFridge().getType())
                        .role(m.getRole())
                        .build())
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

        // 4. 타입이 변경되었다면 구획 마이그레이션 실행
        if (oldType != newType) {
            List<Compartment> oldCompartments = compartmentRepository.findByFridgeIdOrderBySequenceOrderAsc(fridgeId);
            List<Ingredient> ingredients = ingredientRepository.findByCompartmentFridgeId(fridgeId);

            createDefaultCompartments(fridge);

            List<Compartment> newCompartments = compartmentRepository.findByFridgeIdOrderBySequenceOrderAsc(fridgeId);

            for (Ingredient ingredient : ingredients) {
                StorageType oldStorageType = ingredient.getCompartment().getStorageType();
                Compartment matchedNewCompartment = newCompartments.stream()
                        .filter(comp -> comp.getStorageType() == oldStorageType)
                        .findFirst()
                        .orElse(newCompartments.isEmpty() ? null : newCompartments.get(0));

                if (matchedNewCompartment != null) {
                    ingredient.updateCompartment(matchedNewCompartment);
                }
            }
            ingredientRepository.saveAll(ingredients);
            compartmentRepository.deleteAll(oldCompartments);
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
                .build();
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "fridges", key = "#p1"),
        @CacheEvict(value = "fridgeLayout", key = "#p0")
    })
    public void deleteFridge(Long fridgeId, Long userId) {
        if (!fridgeMemberRepository.existsByFridgeIdAndUserId(fridgeId, userId)) {
            throw new IllegalArgumentException("해당 냉장고에 대한 삭제 권한이 없습니다.");
        }

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
}
