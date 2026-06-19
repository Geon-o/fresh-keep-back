package com.example.fresh_keep.global.security.oauth;

public interface OAuthTokenVerifier {
    // ID 토큰을 검증하고, 연동사 제공 고유 사용자 식별정보(providerId), 이메일, 이름을 추출해 반환합니다.
    OAuthUserInfo verify(String idToken) throws Exception;
}
