package com.example.fresh_keep.domain.user.controller;

import com.example.fresh_keep.domain.user.dto.RefreshRequest;
import com.example.fresh_keep.domain.user.entity.User;
import com.example.fresh_keep.domain.user.repository.UserRepository;
import com.example.fresh_keep.global.security.jwt.JwtProvider;
import com.example.fresh_keep.global.security.jwt.RefreshTokenSessionService;
import com.example.fresh_keep.global.security.jwt.dto.TokenResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final RefreshTokenSessionService refreshTokenSessionService;

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(
            @RequestBody(required = false) RefreshRequest refreshRequest,
            HttpServletRequest request) {

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

        String storedToken = storedValue.split("\\|", 2)[0];

        // Replay attack check
        // 앱 UA는 앱/OS 업데이트마다 바뀌므로 UA 일치 검사는 하지 않는다. 탈취 방어는 RTR 재사용 감지로 충분.
        if (!incomingRefreshToken.equals(storedToken)) {
            redisTemplate.delete(redisKey);
            log.error("Replay attack detected for User: {}! Revoking all sessions.", userId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Security alert: Session replayed and revoked.");
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("User not found.");
        }

        String userSubject = user.getDeviceUuid() != null ? user.getDeviceUuid() + "@freshkeep.anonymous" : user.getEmail();
        String newAccessToken = jwtProvider.generateAccessToken(user.getId(), userSubject, user.getName());
        String newRefreshToken = jwtProvider.generateRefreshToken(user.getId(), userSubject);

        // Save new Refresh Token (RTR)
        refreshTokenSessionService.save(userId, newRefreshToken, request);

        return ResponseEntity.ok(TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build());
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request) {
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

        return ResponseEntity.noContent().build();
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
