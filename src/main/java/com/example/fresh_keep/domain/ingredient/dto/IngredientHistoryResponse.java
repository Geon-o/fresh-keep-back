package com.example.fresh_keep.domain.ingredient.dto;

import com.example.fresh_keep.domain.ingredient.entity.HistoryActionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngredientHistoryResponse {
    private Long id;
    private HistoryActionType actionType;
    private String ingredientName;
    private String actorName;
    private String summary;
    private LocalDateTime occurredAt;
}
