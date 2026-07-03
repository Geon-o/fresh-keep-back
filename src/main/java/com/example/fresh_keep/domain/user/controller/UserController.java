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
    public ResponseEntity<UserProfileResponse> updateProfile(
            @AuthenticationPrincipal Object principal,
            @RequestBody NicknameUpdateRequest request) {
        if (!(principal instanceof Long userId)) {
            return ResponseEntity.status(401).build();
        }
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        return userRepository.findById(userId)
                .map(user -> {
                    user.updateName(request.getName().trim());
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
