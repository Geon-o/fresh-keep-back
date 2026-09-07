package com.example.fresh_keep.domain.memo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateMemoRequest {
    @NotBlank(message = "메모 내용은 필수입니다.")
    @Size(max = 1000, message = "메모 내용은 1000자를 넘을 수 없습니다.")
    private String content;
}
