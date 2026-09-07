package com.example.fresh_keep.domain.memo.dto;

import com.example.fresh_keep.domain.memo.entity.MemoType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoResponse {
    private Long id;
    private Long fridgeId;
    private Long authorUserId;
    // 닉네임 변경이 항상 최신으로 반영되도록 스냅샷이 아니라 조회 시점에 조회한 이름.
    private String authorName;
    private MemoType type;
    private String content;
    // 내가 작성한 메모인지 (프론트에서 수정/삭제 버튼 노출 여부 판단용)
    private boolean mine;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
