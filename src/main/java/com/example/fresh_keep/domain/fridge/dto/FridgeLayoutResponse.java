package com.example.fresh_keep.domain.fridge.dto;

import com.example.fresh_keep.domain.fridge.enums.FridgeType;
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
public class FridgeLayoutResponse {
    private Long fridgeId;
    private String fridgeName;
    private FridgeType type;
    private List<CompartmentDetailResponse> compartments;
    // 구획을 아직 정하지 않은 채 등록된 식재료("위치 미정")
    private List<IngredientDetailResponse> unassignedIngredients;
}
