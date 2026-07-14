package com.example.fresh_keep.domain.ingredient.dto;

import com.example.fresh_keep.domain.ingredient.enums.ExpirationType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class UpdateIngredientRequest {
    private Long compartmentId;
    private String name;
    private Double quantity;
    private String unit;
    private LocalDate expirationDate;
    private ExpirationType expirationType;
    private String memo;
}
