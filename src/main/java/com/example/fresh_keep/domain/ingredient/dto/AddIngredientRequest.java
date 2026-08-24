package com.example.fresh_keep.domain.ingredient.dto;

import com.example.fresh_keep.domain.ingredient.enums.ExpirationType;
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
    @NotNull(message = "냉장고 ID는 필수입니다.")
    private Long fridgeId;

    // 없으면 "위치 미정" 상태로 등록되고, 나중에 수정(PATCH)으로 구획을 지정할 수 있다.
    private Long compartmentId;

    @NotBlank(message = "식재료 이름은 필수입니다.")
    private String name;

    @NotNull(message = "수량은 필수입니다.")
    private Double quantity;

    @NotBlank(message = "단위는 필수입니다.")
    private String unit;

    @NotNull(message = "유통기한은 필수입니다.")
    private LocalDate expirationDate;

    @NotNull(message = "기한 종류는 필수입니다.")
    private ExpirationType expirationType;

    private String memo;
}
