package com.example.fresh_keep.domain.ingredient.controller;

import com.example.fresh_keep.domain.ingredient.dto.AddUnitRequest;
import com.example.fresh_keep.domain.ingredient.service.IngredientUnitService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/units")
@RequiredArgsConstructor
public class IngredientUnitController {

    private final IngredientUnitService ingredientUnitService;

    @GetMapping
    public ResponseEntity<List<String>> getUnits(@AuthenticationPrincipal Object principal) {
        if (!(principal instanceof Long userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(ingredientUnitService.getUnits(userId));
    }

    @PostMapping
    public ResponseEntity<List<String>> addUnit(
            @Valid @RequestBody AddUnitRequest request,
            @AuthenticationPrincipal Object principal) {

        if (!(principal instanceof Long userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<String> units = ingredientUnitService.addUnit(userId, request.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(units);
    }
}
