package com.example.fresh_keep.global.security.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Map;

@Slf4j
@Component
public class GoogleTokenVerifier implements OAuthTokenVerifier {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @SuppressWarnings("unchecked")
    public OAuthUserInfo verify(String idToken) throws Exception {
        // ID 토큰(JWT) 구조 확인 및 페이로드 디코딩
        String[] parts = idToken.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid ID Token format.");
        }

        // Base64URL 디코딩 수행
        byte[] decodedBytes = Base64.getUrlDecoder().decode(parts[1]);
        Map<String, Object> payload = objectMapper.readValue(decodedBytes, Map.class);

        // iss(issuers) 확인 - google이 발급했는지 검증
        String iss = (String) payload.get("iss");
        if (iss == null || (!iss.contains("accounts.google.com") && !iss.equals("https://accounts.google.com"))) {
            throw new SecurityException("ID Token issuer is not valid Google.");
        }

        // 만료 시간 검증
        Number expVal = (Number) payload.get("exp");
        if (expVal != null) {
            long exp = expVal.longValue() * 1000;
            if (System.currentTimeMillis() > exp) {
                throw new SecurityException("ID Token is expired.");
            }
        }

        // 보수적 로컬 파싱 및 추출 (실사용 환경에서는 google 라이브러리 연동 혹은 공개키 서명(JWK) 검증 필요)
        String sub = (String) payload.get("sub");
        String email = (String) payload.get("email");
        String name = (String) payload.get("name");

        log.info("Google ID Token verified successfully for email: {}", email);

        return OAuthUserInfo.builder()
                .providerId(sub)
                .email(email)
                .name(name != null ? name : "Google User")
                .build();
    }
}
