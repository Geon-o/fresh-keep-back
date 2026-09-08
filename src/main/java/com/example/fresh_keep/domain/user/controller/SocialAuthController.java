package com.example.fresh_keep.domain.user.controller;

import com.example.fresh_keep.domain.user.dto.SocialLoginRequest;
import com.example.fresh_keep.domain.user.dto.SocialResolveRequest;
import com.example.fresh_keep.domain.user.service.SocialAuthService;
import com.example.fresh_keep.global.security.jwt.dto.TokenResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 소셜 로그인(구글/네이버). 익명 우선 구조 위에 "원할 때 계정 연결"을 얹는다.
 * - /google, /naver: 제공자 토큰 검증 → 익명계정 링크 또는 소셜계정 로그인. 데이터 충돌 시 conflictToken 반환.
 * - /resolve: 사용자가 고른 전략(merge|account)으로 충돌 확정.
 * (/api/auth/** 는 SecurityConfig에서 permitAll)
 */
@Slf4j
@RestController
@RequestMapping("/api/auth/social")
@RequiredArgsConstructor
public class SocialAuthController {

    private final SocialAuthService socialAuthService;

    @PostMapping("/google")
    public ResponseEntity<?> google(@RequestBody SocialLoginRequest request, HttpServletRequest httpRequest) {
        try {
            SocialAuthService.SocialAuthResult result =
                    socialAuthService.loginGoogle(request.getIdToken(), request.getDeviceUuid(), httpRequest);
            return toResponse(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/naver")
    public ResponseEntity<?> naver(@RequestBody SocialLoginRequest request, HttpServletRequest httpRequest) {
        try {
            SocialAuthService.SocialAuthResult result =
                    socialAuthService.loginNaver(request.getAccessToken(), request.getDeviceUuid(), httpRequest);
            return toResponse(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/resolve")
    public ResponseEntity<?> resolve(@RequestBody SocialResolveRequest request, HttpServletRequest httpRequest) {
        String strategy = request.getStrategy();
        if (!"merge".equals(strategy) && !"account".equals(strategy)) {
            return ResponseEntity.badRequest().body(Map.of("message", "strategy는 merge 또는 account 여야 합니다."));
        }
        try {
            TokenResponse tokens = socialAuthService.resolveConflict(request.getConflictToken(), strategy, httpRequest);
            return ResponseEntity.ok(tokens);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", e.getMessage()));
        }
    }

    private ResponseEntity<?> toResponse(SocialAuthService.SocialAuthResult result) {
        if (result.isConflict()) {
            return ResponseEntity.ok(Map.of("conflict", true, "conflictToken", result.conflictToken()));
        }
        return ResponseEntity.ok(result.tokens());
    }
}
