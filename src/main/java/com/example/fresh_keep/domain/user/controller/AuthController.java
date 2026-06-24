package com.example.fresh_keep.domain.user.controller;

import com.example.fresh_keep.domain.user.dto.RefreshRequest;
import com.example.fresh_keep.domain.user.entity.User;
import com.example.fresh_keep.domain.user.repository.UserRepository;
import com.example.fresh_keep.global.security.jwt.JwtProvider;
import com.example.fresh_keep.global.security.jwt.dto.TokenResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtProvider jwtProvider;
    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;

    @Value("${jwt.refresh-token-expiration:604800000}")
    private long refreshTokenExpiration;

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @RequestBody(required = false) RefreshRequest refreshRequest,
            HttpServletRequest request,
            HttpServletResponse response) {

        String incomingRefreshToken = null;
        if (refreshRequest != null && refreshRequest.getRefreshToken() != null) {
            incomingRefreshToken = refreshRequest.getRefreshToken();
        }

        if (incomingRefreshToken == null || incomingRefreshToken.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Refresh Token is missing.");
        }

        if (!jwtProvider.validateToken(incomingRefreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid or expired Refresh Token.");
        }

        Long userId = jwtProvider.getUserId(incomingRefreshToken);
        String redisKey = "RT:" + userId;
        String storedValue = redisTemplate.opsForValue().get(redisKey);

        if (storedValue == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Refresh Token session not found in server.");
        }

        String[] parts = storedValue.split("\\|", 3);
        String storedToken = parts[0];
        String storedIp = parts.length > 1 ? parts[1] : "";
        String storedUserAgent = parts.length > 2 ? parts[2] : "";

        // Replay attack check
        if (!incomingRefreshToken.equals(storedToken)) {
            redisTemplate.delete(redisKey);
            log.error("Replay attack detected for User: {}! Revoking all sessions.", userId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Security alert: Session replayed and revoked.");
        }

        // Client context verification
        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        if (!storedUserAgent.equals(userAgent != null ? userAgent : "UNKNOWN")) {
            log.warn("User-Agent mismatch detected for User: {}. Expected: {}, Actual: {}", userId, storedUserAgent, userAgent);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Session context changed.");
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not found.");
        }

        String userSubject = user.getDeviceUuid() != null ? user.getDeviceUuid() + "@freshkeep.anonymous" : user.getEmail();
        String newAccessToken = jwtProvider.generateAccessToken(user.getId(), userSubject, user.getName());
        String newRefreshToken = jwtProvider.generateRefreshToken(user.getId(), userSubject);

        // Save new Refresh Token (RTR)
        String newValue = newRefreshToken + "|" + clientIp + "|" + (userAgent != null ? userAgent : "UNKNOWN");
        redisTemplate.opsForValue().set(redisKey, newValue, refreshTokenExpiration, TimeUnit.MILLISECONDS);

        // Update cookie
        setAccessTokenCookie(response, newAccessToken);

        return ResponseEntity.ok(TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build());
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        String token = resolveToken(request);
        if (token != null && jwtProvider.validateToken(token)) {
            Long userId = jwtProvider.getUserId(token);
            redisTemplate.delete("RT:" + userId);

            long expiryTime = jwtProvider.getClaims(token).getExpiration().getTime();
            long now = System.currentTimeMillis();
            long remainingTime = expiryTime - now;

            if (remainingTime > 0) {
                redisTemplate.opsForValue().set("BL:" + token, "logout", remainingTime, TimeUnit.MILLISECONDS);
            }
        }

        Cookie cookie = new Cookie("accessToken", null);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        return ResponseEntity.noContent().build();
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if ("accessToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private void setAccessTokenCookie(HttpServletResponse response, String accessToken) {
        Cookie cookie = new Cookie("accessToken", accessToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge((int) (jwtProvider.getClaims(accessToken).getExpiration().getTime() - System.currentTimeMillis()) / 1000);
        response.addCookie(cookie);
        response.addHeader("Set-Cookie", String.format("accessToken=%s; Max-Age=%d; Path=/; Secure; HttpOnly; SameSite=Strict", 
                accessToken, cookie.getMaxAge()));
    }

    private String getClientIp(HttpServletRequest request) {
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
        return ip;
    }
}
