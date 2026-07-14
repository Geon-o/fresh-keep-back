package com.example.fresh_keep.domain.ingredient.service;

import com.example.fresh_keep.domain.ingredient.entity.IngredientUnit;
import com.example.fresh_keep.domain.ingredient.repository.IngredientUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IngredientUnitService {

    private final IngredientUnitRepository ingredientUnitRepository;

    public List<String> getUnits(Long userId) {
        return ingredientUnitRepository.findByUserIdOrderByCreatedAtAsc(userId)
                .stream()
                .map(IngredientUnit::getName)
                .toList();
    }

    @Transactional
    public List<String> addUnit(Long userId, String name) {
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("단위 이름은 비어 있을 수 없습니다.");
        }
        if (!ingredientUnitRepository.existsByUserIdAndName(userId, trimmed)) {
            ingredientUnitRepository.save(IngredientUnit.builder().userId(userId).name(trimmed).build());
        }
        return getUnits(userId);
    }
}
