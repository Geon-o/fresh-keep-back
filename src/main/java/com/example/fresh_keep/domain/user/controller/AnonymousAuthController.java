package com.example.fresh_keep.domain.user.controller;
 
import com.example.fresh_keep.domain.user.entity.User;
import com.example.fresh_keep.domain.user.repository.UserRepository;
import com.example.fresh_keep.global.security.jwt.JwtProvider;
import com.example.fresh_keep.global.security.jwt.dto.TokenResponse;
import com.example.fresh_keep.global.util.SecurityUtil;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
 
import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
 
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AnonymousAuthController {
 
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
 
    @PostMapping("/anonymous")
    public ResponseEntity<?> authenticateAnonymous(@RequestBody DeviceRegisterRequest request) {
        if (request.getDeviceUuid() == null || request.getDeviceUuid().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Device UUID is required."));
        }
 
        String rawDeviceUuid = request.getDeviceUuid().trim();
        String hashedDeviceUuid = SecurityUtil.encryptSHA256(rawDeviceUuid);
        Optional<User> existingUser = userRepository.findByDeviceUuid(hashedDeviceUuid);
        User user;
        String plainBackupKey = null;
 
        try {
            if (existingUser.isPresent()) {
                user = existingUser.get();
            } else {
                // Create a new anonymous user
                plainBackupKey = generateUniqueBackupKey();
                String hashedBackupKey = SecurityUtil.encryptSHA256(plainBackupKey);
                user = User.builder()
                        .name("익명 사용자")
                        .deviceUuid(hashedDeviceUuid)
                        .backupKey(hashedBackupKey)
                        .provider("anonymous")
                        .build();
                user = userRepository.save(user);
            }
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // 거의 동시에 여러 요청이 왔을 때, 이미 DB에 저장되어 있는 경우가 있으므로 다시 한 번 조회해 본다.
            existingUser = userRepository.findByDeviceUuid(hashedDeviceUuid);
            if (existingUser.isPresent()) {
                user = existingUser.get();
                plainBackupKey = null;
            } else {
                throw e;
            }
        }
 
        String subject = user.getDeviceUuid() != null ? user.getDeviceUuid() + "@freshkeep.anonymous" : "anonymous_" + user.getId() + "@freshkeep.anonymous";
        String accessToken = jwtProvider.generateAccessToken(user.getId(), subject, user.getName());
        String refreshToken = jwtProvider.generateRefreshToken(user.getId(), subject);
 
        return ResponseEntity.ok(TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .backupKey(plainBackupKey)
                .build());
    }

    @GetMapping("/backup-key")
    public ResponseEntity<?> getBackupKey(@AuthenticationPrincipal Object principal) {
        if (!(principal instanceof Long userId)) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }

        return userRepository.findById(userId)
                .map(user -> {
                    if (user.getBackupKey() == null) {
                        String newKey = generateUniqueBackupKey();
                        user.updateBackupKey(SecurityUtil.encryptSHA256(newKey));
                        userRepository.save(user);
                    }
                    return ResponseEntity.ok(Map.of("backupKey", user.getBackupKey()));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/restore")
    public ResponseEntity<?> restoreSession(@RequestBody RestoreRequest request) {
        if (request.getBackupKey() == null || request.getBackupKey().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "백업 키를 입력해 주세요."));
        }
        if (request.getDeviceUuid() == null || request.getDeviceUuid().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Device UUID is required."));
        }

        String rawBackupKey = request.getBackupKey().trim();
        String hashedBackupKey = SecurityUtil.encryptSHA256(rawBackupKey);
        String rawDeviceUuid = request.getDeviceUuid().trim();
        String hashedDeviceUuid = SecurityUtil.encryptSHA256(rawDeviceUuid);

        Optional<User> targetUserOpt = userRepository.findByBackupKey(hashedBackupKey);
        if (targetUserOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "유효하지 않은 백업 키입니다."));
        }

        User user = targetUserOpt.get();
        // Update device UUID to map this new device (using hashed UUID for privacy)
        user.updateDeviceUuid(hashedDeviceUuid);
        userRepository.save(user);

        String subject = user.getDeviceUuid() != null ? user.getDeviceUuid() + "@freshkeep.anonymous" : "anonymous_" + user.getId() + "@freshkeep.anonymous";
        String accessToken = jwtProvider.generateAccessToken(user.getId(), subject, user.getName());
        String refreshToken = jwtProvider.generateRefreshToken(user.getId(), subject);

        return ResponseEntity.ok(TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .backupKey(rawBackupKey)
                .build());
    }

    private String generateUniqueBackupKey() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        SecureRandom random = new SecureRandom();
        String key;
        do {
            StringBuilder sb = new StringBuilder("FK-");
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 4; j++) {
                    sb.append(chars.charAt(random.nextInt(chars.length())));
                }
                if (i < 2) sb.append("-");
            }
            key = sb.toString();
        } while (userRepository.findByBackupKey(SecurityUtil.encryptSHA256(key)).isPresent());
        return key;
    }

    @Data
    public static class DeviceRegisterRequest {
        private String deviceUuid;
    }

    @Data
    public static class RestoreRequest {
        private String backupKey;
        private String deviceUuid;
    }
}
