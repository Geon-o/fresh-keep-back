package com.example.fresh_keep.global.security.oauth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class OAuthTokenVerifierFactory {

    private final GoogleTokenVerifier googleTokenVerifier;
    private final KakaoTokenVerifier kakaoTokenVerifier;
    private final NaverTokenVerifier naverTokenVerifier;

    public OAuthTokenVerifier getVerifier(String provider) {
        if ("google".equalsIgnoreCase(provider)) {
            return googleTokenVerifier;
        } else if ("kakao".equalsIgnoreCase(provider)) {
            return kakaoTokenVerifier;
        } else if ("naver".equalsIgnoreCase(provider)) {
            return naverTokenVerifier;
        }
        throw new IllegalArgumentException("Unsupported OAuth provider: " + provider);
    }
}
