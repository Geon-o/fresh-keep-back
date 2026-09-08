package com.example.fresh_keep.domain.user.dto;

import lombok.Data;

/**
 * 충돌(게스트 데이터 vs 기존 소셜계정 데이터) 발생 후, 사용자가 고른 전략으로 최종 확정.
 * strategy: "merge"(게스트 데이터를 기존 계정에 합침) | "account"(기존 계정 데이터만 사용, 게스트 데이터 버림)
 */
@Data
public class SocialResolveRequest {
    private String conflictToken;
    private String strategy;
}
