package com.example.fresh_keep.domain.user.service;

import com.example.fresh_keep.domain.fridge.entity.Compartment;
import com.example.fresh_keep.domain.fridge.entity.Fridge;
import com.example.fresh_keep.domain.fridge.entity.FridgeMember;
import com.example.fresh_keep.domain.fridge.enums.MemberRole;
import com.example.fresh_keep.domain.fridge.repository.CompartmentRepository;
import com.example.fresh_keep.domain.fridge.repository.FridgeMemberRepository;
import com.example.fresh_keep.domain.fridge.repository.FridgeRepository;
import com.example.fresh_keep.domain.ingredient.entity.Ingredient;
import com.example.fresh_keep.domain.ingredient.repository.IngredientRepository;
import com.example.fresh_keep.domain.user.entity.User;
import com.example.fresh_keep.domain.user.enums.NicknameAdjective;
import com.example.fresh_keep.domain.user.enums.NicknameNoun;
import com.example.fresh_keep.domain.user.repository.UserRepository;
import com.example.fresh_keep.global.security.jwt.JwtProvider;
import com.example.fresh_keep.global.security.jwt.RefreshTokenSessionService;
import com.example.fresh_keep.global.security.jwt.dto.TokenResponse;
import com.example.fresh_keep.global.security.oauth.SocialProfileClient;
import com.example.fresh_keep.global.util.SecurityUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class SocialAuthService {

    private final UserRepository userRepository;
    private final FridgeMemberRepository fridgeMemberRepository;
    private final FridgeRepository fridgeRepository;
    private final IngredientRepository ingredientRepository;
    private final CompartmentRepository compartmentRepository;
    private final JwtProvider jwtProvider;
    private final RefreshTokenSessionService refreshTokenSessionService;
    private final SecurityUtil securityUtil;
    private final StringRedisTemplate redisTemplate;
    private final SocialProfileClient socialProfileClient;

    private static final String CONFLICT_PREFIX = "SOCIAL_CONFLICT:";
    private static final long CONFLICT_TTL_SECONDS = 300; // 5분

    /** 성공(tokens)이거나 충돌(conflictToken) 둘 중 하나. */
    public record SocialAuthResult(TokenResponse tokens, String conflictToken) {
        public boolean isConflict() { return conflictToken != null; }
    }

    @Transactional
    public SocialAuthResult loginGoogle(String idToken, String rawDeviceUuid, HttpServletRequest req) {
        String providerId = socialProfileClient.verifyGoogleAndGetSub(idToken);
        return authenticate("google", providerId, rawDeviceUuid, req);
    }

    @Transactional
    public SocialAuthResult loginNaver(String accessToken, String rawDeviceUuid, HttpServletRequest req) {
        String providerId = socialProfileClient.fetchNaverId(accessToken);
        return authenticate("naver", providerId, rawDeviceUuid, req);
    }

    private SocialAuthResult authenticate(String provider, String providerId, String rawDeviceUuid, HttpServletRequest req) {
        String hashedDeviceUuid = (rawDeviceUuid != null && !rawDeviceUuid.isBlank())
                ? securityUtil.hash(rawDeviceUuid.trim()) : null;

        Optional<User> socialOpt = userRepository.findByProviderAndProviderId(provider, providerId);
        Optional<User> anonOpt = hashedDeviceUuid != null
                ? userRepository.findByDeviceUuid(hashedDeviceUuid) : Optional.empty();

        // 1) 기존 소셜 계정이 있는 경우
        if (socialOpt.isPresent()) {
            User social = socialOpt.get();
            if (anonOpt.isPresent() && !anonOpt.get().getId().equals(social.getId())) {
                User anon = anonOpt.get();
                if (anonHasData(anon.getId())) {
                    // 충돌: 이 기기 게스트 데이터 vs 기존 계정 데이터 → 사용자에게 물어본다(B안)
                    String token = UUID.randomUUID().toString().replace("-", "");
                    redisTemplate.opsForValue().set(
                            CONFLICT_PREFIX + token,
                            social.getId() + "|" + anon.getId() + "|" + hashedDeviceUuid,
                            CONFLICT_TTL_SECONDS, TimeUnit.SECONDS);
                    return new SocialAuthResult(null, token);
                }
                // 게스트에 의미있는 데이터 없음 → 조용히 정리
                purgeAnonUser(anon);
            }
            mapDeviceToUser(social, hashedDeviceUuid);
            return new SocialAuthResult(issueTokens(social, req, null), null);
        }

        // 2) 소셜 계정이 없고, 이 기기에 게스트 계정이 있으면 → 승격(데이터 보존)
        if (anonOpt.isPresent()) {
            User anon = anonOpt.get();
            anon.linkSocial(provider, providerId);
            userRepository.save(anon);
            return new SocialAuthResult(issueTokens(anon, req, null), null);
        }

        // 3) 완전 신규 → 새 소셜 계정 생성 (+ 백업키 1회 반환)
        String backupKey = generateUniqueBackupKey();
        User created = User.builder()
                .name(generateRandomNickname())
                .provider(provider)
                .providerId(providerId)
                .deviceUuid(hashedDeviceUuid)
                .backupKey(securityUtil.hash(backupKey))
                .build();
        created = userRepository.save(created);
        return new SocialAuthResult(issueTokens(created, req, backupKey), null);
    }

    @Transactional
    public TokenResponse resolveConflict(String conflictToken, String strategy, HttpServletRequest req) {
        String key = CONFLICT_PREFIX + conflictToken;
        String stored = redisTemplate.opsForValue().get(key);
        if (stored == null) {
            throw new IllegalArgumentException("만료되었거나 유효하지 않은 요청입니다. 다시 로그인해 주세요.");
        }
        redisTemplate.delete(key); // 1회성

        String[] parts = stored.split("\\|", 3);
        Long socialId = Long.valueOf(parts[0]);
        Long anonId = Long.valueOf(parts[1]);
        String hashedDeviceUuid = parts.length > 2 ? parts[2] : null;

        User social = userRepository.findById(socialId)
                .orElseThrow(() -> new IllegalArgumentException("계정을 찾을 수 없습니다."));
        User anon = userRepository.findById(anonId).orElse(null);

        if (anon != null && !anon.getId().equals(social.getId())) {
            if ("merge".equals(strategy)) {
                transferAnonDataTo(anon, social);
            } else {
                purgeAnonUser(anon); // "account": 게스트 데이터 버림
            }
        }
        mapDeviceToUser(social, hashedDeviceUuid);
        return issueTokens(social, req, null);
    }

    private boolean anonHasData(Long userId) {
        return !fridgeMemberRepository.findByUserId(userId).isEmpty();
    }

    // 게스트의 냉장고 멤버십을 소셜 계정으로 이전(데이터 합치기) 후 게스트 계정 삭제.
    private void transferAnonDataTo(User anon, User social) {
        List<FridgeMember> members = fridgeMemberRepository.findByUserId(anon.getId());
        for (FridgeMember m : members) {
            m.transferTo(social);
            fridgeMemberRepository.save(m);
        }
        anon.updateDeviceUuid(null);
        userRepository.saveAndFlush(anon);
        userRepository.delete(anon);
        userRepository.flush();
    }

    // 게스트 계정과 그 소유 데이터를 완전 삭제(회원탈퇴와 동일한 정리).
    private void purgeAnonUser(User anon) {
        List<FridgeMember> members = fridgeMemberRepository.findByUserId(anon.getId());
        for (FridgeMember member : members) {
            Fridge fridge = member.getFridge();
            if (member.getRole() == MemberRole.OWNER) {
                List<Ingredient> ingredients = ingredientRepository.findByFridgeId(fridge.getId());
                ingredientRepository.deleteAll(ingredients);
                List<Compartment> compartments = compartmentRepository.findByFridgeIdOrderBySequenceOrderAsc(fridge.getId());
                compartmentRepository.deleteAll(compartments);
                List<FridgeMember> fridgeMembers = fridgeMemberRepository.findByFridgeId(fridge.getId());
                fridgeMemberRepository.deleteAll(fridgeMembers);
                fridgeRepository.delete(fridge);
            } else {
                fridgeMemberRepository.delete(member);
            }
        }
        anon.updateDeviceUuid(null);
        userRepository.saveAndFlush(anon);
        userRepository.delete(anon);
        userRepository.flush();
    }

    // 이 기기를 소셜 계정에 매핑(재설치/기기변경 후에도 같은 계정으로 연결). deviceUuid는 unique이므로
    // 앞선 purge/transfer로 게스트가 이미 놓아준 상태에서만 호출된다.
    private void mapDeviceToUser(User social, String hashedDeviceUuid) {
        if (hashedDeviceUuid == null || hashedDeviceUuid.equals(social.getDeviceUuid())) {
            return;
        }
        social.updateDeviceUuid(hashedDeviceUuid);
        userRepository.saveAndFlush(social);
    }

    private TokenResponse issueTokens(User user, HttpServletRequest req, String plainBackupKey) {
        String subject = user.getDeviceUuid() != null
                ? user.getDeviceUuid() + "@freshkeep.anonymous"
                : "user_" + user.getId() + "@freshkeep.social";
        String accessToken = jwtProvider.generateAccessToken(user.getId(), subject, user.getName());
        String refreshToken = jwtProvider.generateRefreshToken(user.getId(), subject);
        refreshTokenSessionService.save(user.getId(), refreshToken, req);
        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .backupKey(plainBackupKey)
                .build();
    }

    private String generateRandomNickname() {
        SecureRandom random = new SecureRandom();
        NicknameAdjective[] adjectives = NicknameAdjective.values();
        NicknameNoun[] nouns = NicknameNoun.values();
        String adj = adjectives[random.nextInt(adjectives.length)].getLabel();
        String noun = nouns[random.nextInt(nouns.length)].getLabel();
        return adj + noun;
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
}
