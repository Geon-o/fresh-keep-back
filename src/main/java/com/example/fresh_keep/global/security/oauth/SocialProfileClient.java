package com.example.fresh_keep.global.security.oauth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * 소셜 제공자 토큰을 검증하고 "불변 고유 ID(providerId)"만 뽑아온다.
 * 데이터 최소화 원칙: 이메일/이름 등은 가져오지 않는다.
 * - 구글: idToken을 Google tokeninfo로 검증(서명·만료·aud 확인) 후 sub 반환.
 * - 네이버: accessToken으로 /v1/nid/me 호출해 response.id 반환.
 */
@Slf4j
@Component
public class SocialProfileClient {

    private final RestClient restClient = RestClient.create();

    // 앱이 idToken을 받을 때 audience로 쓴 "웹 클라이언트 ID". 반드시 이 값과 일치해야 위조 토큰을 막는다.
    @Value("${oauth.google.web-client-id:}")
    private String googleWebClientId;

    /** 구글 idToken 검증 → providerId(sub). 실패 시 IllegalArgumentException. */
    @SuppressWarnings("unchecked")
    public String verifyGoogleAndGetSub(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new IllegalArgumentException("idToken이 비어 있습니다.");
        }
        Map<String, Object> body;
        try {
            body = restClient.get()
                    .uri("https://oauth2.googleapis.com/tokeninfo?id_token={t}", idToken)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            log.warn("Google tokeninfo 호출 실패", e);
            throw new IllegalArgumentException("유효하지 않은 구글 토큰입니다.");
        }
        if (body == null) {
            throw new IllegalArgumentException("유효하지 않은 구글 토큰입니다.");
        }
        Object aud = body.get("aud");
        if (googleWebClientId != null && !googleWebClientId.isBlank() && !googleWebClientId.equals(aud)) {
            log.warn("구글 idToken aud 불일치: {}", aud);
            throw new IllegalArgumentException("구글 토큰의 대상(audience)이 일치하지 않습니다.");
        }
        Object sub = body.get("sub");
        if (sub == null) {
            throw new IllegalArgumentException("구글 토큰에서 사용자 식별자를 찾을 수 없습니다.");
        }
        return sub.toString();
    }

    /** 네이버 accessToken 검증 → providerId(response.id). 실패 시 IllegalArgumentException. */
    @SuppressWarnings("unchecked")
    public String fetchNaverId(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("accessToken이 비어 있습니다.");
        }
        Map<String, Object> body;
        try {
            body = restClient.get()
                    .uri("https://openapi.naver.com/v1/nid/me")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);
        } catch (Exception e) {
            log.warn("네이버 프로필 조회 실패", e);
            throw new IllegalArgumentException("유효하지 않은 네이버 토큰입니다.");
        }
        if (body == null || !"00".equals(String.valueOf(body.get("resultcode")))) {
            throw new IllegalArgumentException("유효하지 않은 네이버 토큰입니다.");
        }
        Object response = body.get("response");
        if (!(response instanceof Map)) {
            throw new IllegalArgumentException("네이버 프로필 응답이 올바르지 않습니다.");
        }
        Object id = ((Map<String, Object>) response).get("id");
        if (id == null) {
            throw new IllegalArgumentException("네이버 토큰에서 사용자 식별자를 찾을 수 없습니다.");
        }
        return id.toString();
    }
}
