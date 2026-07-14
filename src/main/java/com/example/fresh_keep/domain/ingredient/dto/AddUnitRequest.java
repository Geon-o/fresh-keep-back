package com.example.fresh_keep.domain.ingredient.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AddUnitRequest {
    @NotBlank(message = "단위 이름은 필수입니다.")
    private String name;
}
