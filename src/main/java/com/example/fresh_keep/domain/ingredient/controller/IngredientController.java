package com.example.fresh_keep.domain.ingredient.controller;

import com.example.fresh_keep.domain.ingredient.dto.AddIngredientRequest;
import com.example.fresh_keep.domain.ingredient.dto.IngredientDetailResponse;
import com.example.fresh_keep.domain.ingredient.dto.UpdateIngredientRequest;
import com.example.fresh_keep.domain.ingredient.service.IngredientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ingredients")
@RequiredArgsConstructor
public class IngredientController {

    private final IngredientService ingredientService;

    @PostMapping
    public ResponseEntity<IngredientDetailResponse> addIngredient(
            @Valid @RequestBody AddIngredientRequest request,
            @AuthenticationPrincipal Object principal) {

        if (!(principal instanceof Long userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        IngredientDetailResponse response = ingredientService.addIngredient(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PatchMapping("/{ingredientId}")
    public ResponseEntity<IngredientDetailResponse> updateIngredient(
            @PathVariable("ingredientId") Long ingredientId,
            @Valid @RequestBody UpdateIngredientRequest request,
            @AuthenticationPrincipal Object principal) {

        if (!(principal instanceof Long userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        IngredientDetailResponse response = ingredientService.updateIngredient(ingredientId, request, userId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{ingredientId}")
    public ResponseEntity<Void> deleteIngredient(
            @PathVariable("ingredientId") Long ingredientId,
            @AuthenticationPrincipal Object principal) {

        if (!(principal instanceof Long userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        ingredientService.deleteIngredient(ingredientId, userId);
        return ResponseEntity.noContent().build();
    }
}
