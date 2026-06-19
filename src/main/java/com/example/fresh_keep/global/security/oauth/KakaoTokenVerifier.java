package com.example.fresh_keep.global.security.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Map;

@Slf4j
@Component
public class KakaoTokenVerifier implements OAuthTokenVerifier {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @SuppressWarnings("unchecked")
    public OAuthUserInfo verify(String idToken) throws Exception {
        String[] parts = idToken.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid ID Token format.");
        }

        byte[] decodedBytes = Base64.getUrlDecoder().decode(parts[1]);
        Map<String, Object> payload = objectMapper.readValue(decodedBytes, Map.class);

        // iss 검증 - kakao가 발급했는지 확인
        String iss = (String) payload.get("iss");
        if (iss == null || !iss.contains("kauth.kakao.com")) {
            throw new SecurityException("ID Token issuer is not valid Kakao.");
        }

        // 만료 검증
        Number expVal = (Number) payload.get("exp");
        if (expVal != null) {
            long exp = expVal.longValue() * 1000;
            if (System.currentTimeMillis() > exp) {
                throw new SecurityException("ID Token is expired.");
            }
        }

        String sub = (String) payload.get("sub");
        String email = (String) payload.get("email");
        String nickname = (String) payload.get("nickname");

        log.info("Kakao ID Token verified successfully for email: {}", email);

        return OAuthUserInfo.builder()
                .providerId(sub)
                .email(email != null ? email : sub + "@kakao.com")
                .name(nickname != null ? nickname : "Kakao User")
                .build();
    }
}
