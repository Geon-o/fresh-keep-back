package com.example.fresh_keep.domain.ingredient.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class IngredientDetailResponse {
    private Long id;
    private String name;
    private Double quantity;
    private String unit;
    private LocalDate expirationDate;
    private Long dday;
    private String memo;
}
