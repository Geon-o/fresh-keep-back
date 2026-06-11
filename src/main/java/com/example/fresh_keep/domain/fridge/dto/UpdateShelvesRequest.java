package com.example.fresh_keep.domain.fridge.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateShelvesRequest {

    @NotNull(message = "내부 선반 정보는 필수입니다.")
    private String insideShelves;

    @NotNull(message = "문쪽 선반 정보는 필수입니다.")
    private String doorShelves;

    @NotNull(message = "문쪽 보관실 사용 설정값은 필수입니다.")
    private Boolean hasDoorStorage;
}
