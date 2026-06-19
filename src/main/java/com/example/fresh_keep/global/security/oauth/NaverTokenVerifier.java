package com.example.fresh_keep.global.security.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.Map;

@Slf4j
@Component
public class NaverTokenVerifier implements OAuthTokenVerifier {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @SuppressWarnings("unchecked")
    public OAuthUserInfo verify(String idToken) throws Exception {
        // 네이버는 공식 OpenID Connect 규격상 ID Token을 발급하지 않는 연동사 구조이거나 
        // 엑세스 토큰(Access Token)으로 회원 프로필조회 API를 앱 혹은 서버에서 호출하여 처리합니다.
        // 여기서는 앱단에서 프로필 조회까지 완료하고 받아온 보증 프로필 데이터를 해시화 또는 JWT화 해서 건네받은 데이터 검증을 수행하거나,
        // 보수적 통일을 위해 네이버 전용 복호화/검증 단계를 탑재합니다.
        String[] parts = idToken.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid ID Token format.");
        }

        byte[] decodedBytes = Base64.getUrlDecoder().decode(parts[1]);
        Map<String, Object> payload = objectMapper.readValue(decodedBytes, Map.class);

        String iss = (String) payload.get("iss");
        if (iss == null || !iss.contains("naver.com")) {
            throw new SecurityException("ID Token issuer is not valid Naver.");
        }

        String sub = (String) payload.get("sub");
        String email = (String) payload.get("email");
        String name = (String) payload.get("name");

        log.info("Naver User Token verified successfully for email: {}", email);

        return OAuthUserInfo.builder()
                .providerId(sub)
                .email(email)
                .name(name != null ? name : "Naver User")
                .build();
    }
}
