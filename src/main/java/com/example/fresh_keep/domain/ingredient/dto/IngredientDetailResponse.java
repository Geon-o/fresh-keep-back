package com.example.fresh_keep.domain.ingredient.dto;

import com.example.fresh_keep.domain.ingredient.enums.ExpirationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngredientDetailResponse {
    private Long id;
    private String name;
    private Double quantity;
    private String unit;
    private LocalDate expirationDate;
    private ExpirationType expirationType;
    private Long dday;
    private String memo;
}
