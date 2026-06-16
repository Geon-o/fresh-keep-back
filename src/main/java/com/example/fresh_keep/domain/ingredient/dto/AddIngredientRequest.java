package com.example.fresh_keep.domain.ingredient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class AddIngredientRequest {
    @NotNull(message = "구획 ID는 필수입니다.")
    private Long compartmentId;

    @NotBlank(message = "식재료 이름은 필수입니다.")
    private String name;

    @NotNull(message = "수량은 필수입니다.")
    private Double quantity;

    @NotBlank(message = "단위는 필수입니다.")
    private String unit;

    @NotNull(message = "유통기한은 필수입니다.")
    private LocalDate expirationDate;

    private String memo;
}
