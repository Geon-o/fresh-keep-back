package com.example.fresh_keep.domain.fridge.dto;

import com.example.fresh_keep.domain.fridge.enums.FridgeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateFridgeRequest {
    @NotBlank(message = "냉장고 이름은 필수입니다.")
    private String name;

    @NotNull(message = "냉장고 타입은 필수입니다.")
    private FridgeType type;
}
