package com.example.fresh_keep.domain.ingredient.repository;

import com.example.fresh_keep.domain.ingredient.entity.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IngredientRepository extends JpaRepository<Ingredient, Long> {
    List<Ingredient> findByCompartmentFridgeId(Long fridgeId);
}
