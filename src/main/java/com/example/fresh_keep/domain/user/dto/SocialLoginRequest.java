package com.example.fresh_keep.domain.user.dto;

import lombok.Data;

/**
 * 소셜 로그인 요청.
 * - 구글: idToken (네이티브 SDK가 발급한 idToken)
 * - 네이버: accessToken (네이티브 SDK가 발급한 accessToken)
 * deviceUuid: 현재 기기의 익명 계정을 찾아 연결(link)하기 위한 키.
 */
@Data
public class SocialLoginRequest {
    private String idToken;
    private String accessToken;
    private String deviceUuid;
}
