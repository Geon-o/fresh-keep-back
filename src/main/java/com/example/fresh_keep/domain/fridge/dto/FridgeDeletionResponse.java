package com.example.fresh_keep.domain.fridge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FridgeDeletionResponse {
    // true면 이번 호출로 냉장고가 실제로 삭제됨. false면 요청/동의만 반영되고 아직 삭제되지 않음(다른 멤버 동의 대기 중).
    private boolean deleted;
}
