package com.example.fresh_keep.domain.fridge.dto;

import com.example.fresh_keep.domain.fridge.enums.StorageType;
import com.example.fresh_keep.domain.ingredient.dto.IngredientDetailResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompartmentDetailResponse {
    private Long id;
    private String name;
    private StorageType storageType;
    private Integer sequenceOrder;
    private List<IngredientDetailResponse> ingredients;
}
