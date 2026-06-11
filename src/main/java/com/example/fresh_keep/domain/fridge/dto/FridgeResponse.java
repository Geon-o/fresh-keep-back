package com.example.fresh_keep.domain.fridge.dto;

import com.example.fresh_keep.domain.fridge.enums.FridgeType;
import com.example.fresh_keep.domain.fridge.enums.MemberRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FridgeResponse {
    private Long id;
    private String name;
    private FridgeType type;
    private MemberRole role;
}
