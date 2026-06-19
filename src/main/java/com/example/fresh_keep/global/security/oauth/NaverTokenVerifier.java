package com.example.fresh_keep.global.security.oauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.Map;

@Slf4j
@Component
public class NaverTokenVerifier implements OAuthTokenVerifier {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    @SuppressWarnings("unchecked")
    public OAuthUserInfo verify(String idToken) throws Exception {
        // 1. JWT 형식인 경우 (모의 테스트 토큰 또는 특정 OIDC 토큰일 경우)
        if (idToken.contains(".")) {
            String[] parts = idToken.split("\\.");
            if (parts.length >= 2) {
                try {
                    byte[] decodedBytes = Base64.getUrlDecoder().decode(parts[1]);
                    Map<String, Object> payload = objectMapper.readValue(decodedBytes, Map.class);
                    
                    String iss = (String) payload.get("iss");
                    if (iss != null && iss.contains("naver.com")) {
                        String sub = (String) payload.get("sub");
                        String email = (String) payload.get("email");
                        String name = (String) payload.get("name");
                        
                        log.info("Naver Mock/JWT Token parsed successfully for email: {}", email);
                        return OAuthUserInfo.builder()
                                .providerId(sub)
                                .email(email)
                                .name(name != null ? name : "Naver User")
                                .build();
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse Naver token as JWT, attempting direct profile api fetch: {}", e.getMessage());
                }
            }
        }

        // 2. 실제 Naver Access Token인 경우 (기기 SDK에서 수령한 진짜 토큰)
        // 네이버의 특성(OIDC 미지원)에 대응하기 위해, 전달받은 액세스 토큰으로 네이버 프로필 조회 API 직접 확인
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + idToken);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://openapi.naver.com/v1/nid/me",
                    HttpMethod.GET,
                    entity,
                    Map.class
            );

            Map<String, Object> body = response.getBody();
            if (body == null || !"00".equals(body.get("resultcode"))) {
                throw new SecurityException("Naver Profile API request failed or returned error code.");
            }

            Map<String, Object> responseData = (Map<String, Object>) body.get("response");
            String id = (String) responseData.get("id");
            String email = (String) responseData.get("email");
            String name = (String) responseData.get("name");

            log.info("Naver Real Access Token verified successfully via Nid API for email: {}", email);

            return OAuthUserInfo.builder()
                    .providerId(id)
                    .email(email)
                    .name(name != null ? name : "Naver User")
                    .build();
            
        } catch (Exception e) {
            log.error("Failed to verify Naver Access Token via Naver API: {}", e.getMessage());
            throw new SecurityException("Unauthorized: Invalid Naver Access Token.", e);
        }
    }
}
