package com.example.fresh_keep.domain.fridge.dto;

import com.example.fresh_keep.domain.fridge.enums.FridgeType;
import com.example.fresh_keep.domain.fridge.enums.MemberRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FridgeResponse {
    private Long id;
    private String name;
    private FridgeType type;
    private MemberRole role;
    private String uuid;
    private boolean deletionRequested;
    private String ownerName;
    // 이 냉장고를 함께 쓰는 멤버 전원의 닉네임(본인 포함, 주인이 맨 앞). 1명뿐이면 공유 안 하는 상태.
    private List<String> memberNames;
    // QR 공유 응답 전용: 이미 멤버였던 냉장고를 다시 스캔한 경우 true.
    private boolean alreadyMember;
}
