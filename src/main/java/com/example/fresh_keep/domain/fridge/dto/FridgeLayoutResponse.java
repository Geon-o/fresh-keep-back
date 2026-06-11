package com.example.fresh_keep.domain.fridge.dto;

import com.example.fresh_keep.domain.fridge.enums.FridgeType;
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
}
