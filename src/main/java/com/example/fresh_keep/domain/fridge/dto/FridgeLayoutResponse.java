package com.example.fresh_keep.domain.fridge.dto;

import com.example.fresh_keep.domain.fridge.enums.FridgeType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class FridgeLayoutResponse {
    private Long fridgeId;
    private String fridgeName;
    private FridgeType type;
    private List<CompartmentDetailResponse> compartments;
}
