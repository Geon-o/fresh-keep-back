package com.example.fresh_keep.global.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * 연동 코드/기기 UUID 등 식별자를 서버 비밀키 기반 HMAC-SHA256으로 해시한다.
 * 결정적 해시라 동등 비교 조회(findByBackupKey 등)가 가능하면서도,
 * 비밀키(security.hmac-secret)를 모르면 DB가 유출되어도 오프라인 사전계산/레인보우 공격이 불가능하다.
 */
@Component
public class SecurityUtil {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] hmacKey;

    public SecurityUtil(@Value("${security.hmac-secret}") String hmacSecret) {
        if (hmacSecret == null || hmacSecret.isBlank()) {
            throw new IllegalStateException("security.hmac-secret 이(가) 설정되지 않았습니다. 환경변수 HMAC_SECRET 을 확인하세요.");
        }
        this.hmacKey = hmacSecret.getBytes(StandardCharsets.UTF_8);
    }

    public String hash(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(hmacKey, HMAC_ALGORITHM));
            byte[] result = mac.doFinal(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(result.length * 2);
            for (byte b : result) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("HMAC-SHA256 hashing failed", e);
        }
    }
}
