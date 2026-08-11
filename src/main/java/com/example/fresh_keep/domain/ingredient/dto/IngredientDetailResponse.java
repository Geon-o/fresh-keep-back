package com.example.fresh_keep.domain.ingredient.dto;

import com.example.fresh_keep.domain.ingredient.enums.ExpirationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
    private String createdByName;
    private LocalDateTime createdAt;
    // 실제로 수정된 적이 있을 때만 값이 채워진다 (수정 이력 없음 = null)
    private String updatedByName;
    private LocalDateTime updatedAt;
}
