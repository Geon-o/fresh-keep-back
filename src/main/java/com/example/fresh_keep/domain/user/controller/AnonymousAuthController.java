package com.example.fresh_keep.domain.user.controller;
 
import com.example.fresh_keep.domain.fridge.entity.FridgeMember;
import com.example.fresh_keep.domain.fridge.repository.FridgeMemberRepository;
import com.example.fresh_keep.domain.fridge.repository.FridgeRepository;
import com.example.fresh_keep.domain.user.entity.User;
import com.example.fresh_keep.domain.user.repository.UserRepository;
import com.example.fresh_keep.global.security.jwt.JwtProvider;
import com.example.fresh_keep.global.security.jwt.RefreshTokenSessionService;
import com.example.fresh_keep.global.security.jwt.dto.TokenResponse;
import com.example.fresh_keep.global.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AnonymousAuthController {

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final FridgeMemberRepository fridgeMemberRepository;
    private final FridgeRepository fridgeRepository;
    private final StringRedisTemplate redisTemplate;
    private final SecurityUtil securityUtil;
    private final RefreshTokenSessionService refreshTokenSessionService;

    // 백업 키 복구 브루트포스 방어: 동일 IP 기준 10분 내 최대 시도 횟수
    private static final int RESTORE_MAX_ATTEMPTS = 10;
    private static final long RESTORE_WINDOW_MINUTES = 10;
 
    @PostMapping("/anonymous")
    public ResponseEntity<?> authenticateAnonymous(@RequestBody DeviceRegisterRequest request, HttpServletRequest httpRequest) {
        if (request.getDeviceUuid() == null || request.getDeviceUuid().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Device UUID is required."));
        }
 
        String rawDeviceUuid = request.getDeviceUuid().trim();
        String hashedDeviceUuid = securityUtil.hash(rawDeviceUuid);
        Optional<User> existingUser = userRepository.findByDeviceUuid(hashedDeviceUuid);
        User user;
        String plainBackupKey = null;
 
        try {
            if (existingUser.isPresent()) {
                user = existingUser.get();
            } else {
                // Create a new anonymous user
                plainBackupKey = generateUniqueBackupKey();
                String hashedBackupKey = securityUtil.hash(plainBackupKey);
                user = User.builder()
                        .name(generateRandomNickname())
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
        refreshTokenSessionService.save(user.getId(), refreshToken, httpRequest);

        return ResponseEntity.ok(TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .backupKey(plainBackupKey)
                .build());
    }

    /**
     * 백업 키 재발급.
     * 백업 키는 해시로만 저장되므로 원본을 되돌려줄 수 없다. 따라서 이 엔드포인트는
     * 새 키를 발급(로테이션)하여 평문을 "이번 응답에서 한 번만" 반환한다.
     * 이전 키는 즉시 무효화되므로 클라이언트는 반환된 키를 안전하게 보관하도록 안내해야 한다.
     */
    @Transactional
    @PostMapping("/backup-key/reissue")
    public ResponseEntity<?> reissueBackupKey(@AuthenticationPrincipal Object principal) {
        if (!(principal instanceof Long userId)) {
            return ResponseEntity.status(401).body(Map.of("message", "Unauthorized"));
        }

        return userRepository.findById(userId)
                .map(user -> {
                    String newKey = generateUniqueBackupKey();
                    user.updateBackupKey(securityUtil.hash(newKey));
                    userRepository.save(user);
                    // 해시가 아닌 평문 키를 1회 반환한다.
                    return ResponseEntity.ok(Map.of("backupKey", newKey));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Transactional
    @PostMapping("/restore")
    public ResponseEntity<?> restoreSession(@RequestBody RestoreRequest request, HttpServletRequest httpRequest) {
        // 브루트포스 방어: IP 기준 시도 횟수 제한
        String clientIp = refreshTokenSessionService.resolveClientIp(httpRequest);
        String rlKey = "RL:restore:" + clientIp;
        Long attempts = redisTemplate.opsForValue().increment(rlKey);
        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(rlKey, RESTORE_WINDOW_MINUTES, TimeUnit.MINUTES);
        }
        if (attempts != null && attempts > RESTORE_MAX_ATTEMPTS) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "복구 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요."));
        }

        if (request.getBackupKey() == null || request.getBackupKey().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "백업 키를 입력해 주세요."));
        }
        if (request.getDeviceUuid() == null || request.getDeviceUuid().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Device UUID is required."));
        }

        String rawBackupKey = request.getBackupKey().trim();
        String hashedBackupKey = securityUtil.hash(rawBackupKey);
        String rawDeviceUuid = request.getDeviceUuid().trim();
        String hashedDeviceUuid = securityUtil.hash(rawDeviceUuid);

        Optional<User> targetUserOpt = userRepository.findByBackupKey(hashedBackupKey);
        if (targetUserOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "유효하지 않은 백업 키입니다."));
        }
 
        User user = targetUserOpt.get();
 
        // 중복 가입 방지용 unique 제약조건(deviceUuid) 충돌을 회피하고 DB 리소스 낭비를 막기 위해
        // 동일한 deviceUuid를 이미 점유 중인 임시 사용자와 그에 연관된 무의미한 데이터를 완전히 삭제합니다.
        Optional<User> tempUserOpt = userRepository.findByDeviceUuid(hashedDeviceUuid);
        if (tempUserOpt.isPresent()) {
            User tempUser = tempUserOpt.get();
            if (!tempUser.getId().equals(user.getId())) {
                // 1. Unique 제약 조건 충돌 방지를 위해 임시 유저의 deviceUuid를 null로 우선 변경 후 즉시 DB 반영
                tempUser.updateDeviceUuid(null);
                userRepository.saveAndFlush(tempUser);

                // 2. 임시 사용자가 포함된 냉장고 관계 확인 및 수동 제거 (외래키 제약 회피)
                List<FridgeMember> members = fridgeMemberRepository.findByUserId(tempUser.getId());
                for (FridgeMember member : members) {
                    fridgeMemberRepository.delete(member);
                    
                    // 3. 만약 해당 냉장고에 다른 공동 관리자가 없다면, 쓰레기 데이터 방지를 위해 냉장고 자체도 함께 완전 제거
                    long fridgeId = member.getFridge().getId();
                    if (fridgeMemberRepository.findByFridgeId(fridgeId).isEmpty()) {
                        fridgeRepository.deleteById(fridgeId);
                    }
                }
                
                // 4. 임시 유저 자체를 DB에서 완벽하게 제거
                userRepository.delete(tempUser);
                userRepository.flush();
            }
        }
 
        // Update device UUID to map this new device (using hashed UUID for privacy)
        user.updateDeviceUuid(hashedDeviceUuid);
        userRepository.save(user);

        String subject = user.getDeviceUuid() != null ? user.getDeviceUuid() + "@freshkeep.anonymous" : "anonymous_" + user.getId() + "@freshkeep.anonymous";
        String accessToken = jwtProvider.generateAccessToken(user.getId(), subject, user.getName());
        String refreshToken = jwtProvider.generateRefreshToken(user.getId(), subject);
        refreshTokenSessionService.save(user.getId(), refreshToken, httpRequest);

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
        } while (userRepository.findByBackupKey(securityUtil.hash(key)).isPresent());
        return key;
    }

    private String generateRandomNickname() {
        String[] adjectives = {
            "뽀송뽀송", "파릇파릇", "쫀득쫀득", "아삭아삭", "탱글탱글",
            "새콤달콤", "고소한", "달콤한", "시원한", "말랑말랑",
            "바삭바삭", "포근한", "싱싱한", "상큼한", "달달한",
            "부드러운", "갓구운", "갓수확한", "촉촉한", "따끈따끈"
        };
        String[] nouns = {
            "브라우니", "방울토마토", "아보카도", "브로콜리", "마카롱",
            "푸딩", "젤리", "머핀", "샐러드", "샤베트",
            "샌드위치", "바나나", "블루베리", "복숭아", "망고",
            "멜론", "치즈케이크", "사과", "딸기", "체리"
        };
        SecureRandom random = new SecureRandom();
        String adj = adjectives[random.nextInt(adjectives.length)];
        String noun = nouns[random.nextInt(nouns.length)];
        return adj + noun;
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
