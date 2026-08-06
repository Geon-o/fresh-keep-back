package com.example.fresh_keep.global.security.jwt;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Refresh Token 세션("RT:{userId}")을 Redis에 저장하는 로직을 한 곳에서 관리한다.
 * 로그인(익명 로그인, 백업 복구, 향후 추가될 OAuth 등)과 AuthController#refresh()의
 * Refresh Token Rotation이 동일한 키 포맷/TTL을 공유해야 하므로, 로그인 경로가 늘어나도
 * 이 서비스만 호출하면 "로그인 직후 refresh가 항상 실패하는" 버그가 재발하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenSessionService {

    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.refresh-token-expiration:604800000}")
    private long refreshTokenExpiration;

    public void save(Long userId, String refreshToken, HttpServletRequest request) {
        save(userId, refreshToken, resolveClientIp(request), request.getHeader("User-Agent"));
    }

    public void save(Long userId, String refreshToken, String clientIp, String userAgent) {
        String redisKey = "RT:" + userId;
        String value = refreshToken + "|" + clientIp + "|" + (userAgent != null ? userAgent : "UNKNOWN");
        redisTemplate.opsForValue().set(redisKey, value, refreshTokenExpiration, TimeUnit.MILLISECONDS);
    }

    public String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }
}
