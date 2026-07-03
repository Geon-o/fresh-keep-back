package com.example.fresh_keep.domain.user.controller;

import com.example.fresh_keep.domain.user.dto.UserProfileResponse;
import com.example.fresh_keep.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.example.fresh_keep.domain.user.entity.User;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> me(@AuthenticationPrincipal Object principal) {
        if (!(principal instanceof Long userId)) {
            return ResponseEntity.status(401).build();
        }

        return userRepository.findById(userId)
                .map(user -> ResponseEntity.ok(UserProfileResponse.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .name(user.getName())
                        .provider(user.getProvider())
                        .build()))
                .orElse(ResponseEntity.notFound().build());
    }

    @Transactional
    @PatchMapping("/me")
    public ResponseEntity<?> updateProfile(
            @AuthenticationPrincipal Object principal,
            @RequestBody NicknameUpdateRequest request) {
        if (!(principal instanceof Long userId)) {
            return ResponseEntity.status(401).body(Map.of("message", "UNAUTHORIZED"));
        }

        if (request.getName() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "NICKNAME_REQUIRED"));
        }

        String name = request.getName().trim();

        // 1. 특수문자, 공백, 이모지 방지 및 2~20자 길이 검증
        // 한글, 영문, 숫자만 허용
        if (!name.matches("^[a-zA-Z0-9가-힣]{2,20}$")) {
            return ResponseEntity.badRequest().body(Map.of("message", "INVALID_NICKNAME"));
        }

        // 2. 중복 검사
        if (userRepository.existsByNameAndIdNot(name, userId)) {
            return ResponseEntity.status(409).body(Map.of("message", "DUPLICATE_NICKNAME"));
        }

        return userRepository.findById(userId)
                .map(user -> {
                    user.updateName(name);
                    User savedUser = userRepository.save(user);
                    return ResponseEntity.ok(UserProfileResponse.builder()
                            .id(savedUser.getId())
                            .email(savedUser.getEmail())
                            .name(savedUser.getName())
                            .provider(savedUser.getProvider())
                            .build());
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Data
    public static class NicknameUpdateRequest {
        private String name;
    }
}
