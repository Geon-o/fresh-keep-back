package com.example.fresh_keep.domain.ingredient.repository;

import com.example.fresh_keep.domain.ingredient.entity.IngredientUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IngredientUnitRepository extends JpaRepository<IngredientUnit, Long> {
    List<IngredientUnit> findByUserIdOrderByCreatedAtAsc(Long userId);
    boolean existsByUserIdAndName(Long userId, String name);
}
