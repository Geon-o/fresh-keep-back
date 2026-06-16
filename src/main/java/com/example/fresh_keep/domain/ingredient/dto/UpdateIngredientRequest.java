package com.example.fresh_keep.domain.ingredient.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class UpdateIngredientRequest {
    private String name;
    private Double quantity;
    private String unit;
    private LocalDate expirationDate;
    private String memo;
}
