package com.example.fresh_keep.domain.ingredient.service;

import com.example.fresh_keep.domain.fridge.entity.Compartment;
import com.example.fresh_keep.domain.fridge.repository.CompartmentRepository;
import com.example.fresh_keep.domain.fridge.repository.FridgeMemberRepository;
import com.example.fresh_keep.domain.ingredient.dto.AddIngredientRequest;
import com.example.fresh_keep.domain.ingredient.dto.IngredientDetailResponse;
import com.example.fresh_keep.domain.ingredient.dto.UpdateIngredientRequest;
import com.example.fresh_keep.domain.ingredient.entity.Ingredient;
import com.example.fresh_keep.domain.ingredient.repository.IngredientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IngredientService {

    private final IngredientRepository ingredientRepository;
    private final CompartmentRepository compartmentRepository;
    private final FridgeMemberRepository fridgeMemberRepository;
    private final CacheManager cacheManager;

    @Transactional
    public IngredientDetailResponse addIngredient(AddIngredientRequest request, Long userId) {
        // 1. 구획 조회
        Compartment compartment = compartmentRepository.findById(request.getCompartmentId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 보관 구획입니다."));

        // 2. 권한 검증 (해당 구획의 냉장고의 멤버인지)
        Long fridgeId = compartment.getFridge().getId();
        if (!fridgeMemberRepository.existsByFridgeIdAndUserId(fridgeId, userId)) {
            throw new IllegalArgumentException("해당 냉장고에 식재료를 추가할 권한이 없습니다.");
        }

        // 3. 식재료 생성 및 저장
        Ingredient ingredient = Ingredient.builder()
                .compartment(compartment)
                .name(request.getName())
                .quantity(request.getQuantity())
                .unit(request.getUnit())
                .expirationDate(request.getExpirationDate())
                .memo(request.getMemo())
                .build();
        ingredientRepository.save(ingredient);

        // 4. 캐시 무효화
        evictLayoutCache(fridgeId);

        return mapToResponse(ingredient);
    }

    @Transactional
    public IngredientDetailResponse updateIngredient(Long ingredientId, UpdateIngredientRequest request, Long userId) {
        // 1. 식재료 조회
        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 식재료입니다."));

        // 2. 권한 검증
        Long fridgeId = ingredient.getCompartment().getFridge().getId();
        if (!fridgeMemberRepository.existsByFridgeIdAndUserId(fridgeId, userId)) {
            throw new IllegalArgumentException("해당 식재료를 수정할 권한이 없습니다.");
        }

        // 3. 식재료 필드 업데이트
        String newName = request.getName() != null ? request.getName() : ingredient.getName();
        Double newQuantity = request.getQuantity() != null ? request.getQuantity() : ingredient.getQuantity();
        String newUnit = request.getUnit() != null ? request.getUnit() : ingredient.getUnit();
        LocalDate newExpirationDate = request.getExpirationDate() != null ? request.getExpirationDate() : ingredient.getExpirationDate();
        String newMemo = request.getMemo() != null ? request.getMemo() : ingredient.getMemo();

        ingredient.update(newName, newQuantity, newUnit, newExpirationDate, newMemo);

        // 구획 이동 처리
        if (request.getCompartmentId() != null) {
            Compartment newCompartment = compartmentRepository.findById(request.getCompartmentId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 보관 구획입니다."));
            
            // 이동할 구획이 동일한 냉장고 내의 구획인지 검증
            if (!newCompartment.getFridge().getId().equals(fridgeId)) {
                throw new IllegalArgumentException("동일한 냉장고 내의 구획으로만 이동할 수 있습니다.");
            }
            
            ingredient.updateCompartment(newCompartment);
        }

        ingredientRepository.save(ingredient);

        // 4. 캐시 무효화
        evictLayoutCache(fridgeId);

        return mapToResponse(ingredient);
    }

    @Transactional
    public void deleteIngredient(Long ingredientId, Long userId) {
        // 1. 식재료 조회
        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 식재료입니다."));

        // 2. 권한 검증
        Long fridgeId = ingredient.getCompartment().getFridge().getId();
        if (!fridgeMemberRepository.existsByFridgeIdAndUserId(fridgeId, userId)) {
            throw new IllegalArgumentException("해당 식재료를 삭제할 권한이 없습니다.");
        }

        ingredientRepository.delete(ingredient);

        // 3. 캐시 무효화
        evictLayoutCache(fridgeId);
    }

    private void evictLayoutCache(Long fridgeId) {
        if (fridgeId != null) {
            Cache cache = cacheManager.getCache("fridgeLayout");
            if (cache != null) {
                cache.evict(fridgeId);
            }
        }
    }

    private IngredientDetailResponse mapToResponse(Ingredient ingredient) {
        LocalDate now = LocalDate.now();
        return IngredientDetailResponse.builder()
                .id(ingredient.getId())
                .name(ingredient.getName())
                .quantity(ingredient.getQuantity())
                .unit(ingredient.getUnit())
                .expirationDate(ingredient.getExpirationDate())
                .dday(ChronoUnit.DAYS.between(now, ingredient.getExpirationDate()))
                .memo(ingredient.getMemo())
                .build();
    }
}
