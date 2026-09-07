package com.example.fresh_keep.domain.memo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// CHECKLIST 타입 메모의 content(JSON 문자열) 한 항목. id는 프론트가 생성해서 보내며,
// 순서가 바뀌거나 다른 항목이 추가/삭제돼도 toggle 대상 항목을 안정적으로 찾기 위해 쓴다.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChecklistItemDto {
    private String id;
    private String text;
    private boolean checked;
}
