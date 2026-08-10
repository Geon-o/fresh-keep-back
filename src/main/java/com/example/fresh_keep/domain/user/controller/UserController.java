package com.example.fresh_keep.domain.user.controller;

import com.example.fresh_keep.domain.user.dto.UserProfileResponse;
import com.example.fresh_keep.domain.user.repository.UserRepository;
import com.example.fresh_keep.domain.fridge.entity.Compartment;
import com.example.fresh_keep.domain.fridge.entity.Fridge;
import com.example.fresh_keep.domain.fridge.entity.FridgeMember;
import com.example.fresh_keep.domain.fridge.enums.MemberRole;
import com.example.fresh_keep.domain.fridge.repository.CompartmentRepository;
import com.example.fresh_keep.domain.fridge.repository.FridgeMemberRepository;
import com.example.fresh_keep.domain.fridge.repository.FridgeRepository;
import com.example.fresh_keep.domain.ingredient.entity.Ingredient;
import com.example.fresh_keep.domain.ingredient.repository.IngredientRepository;
import lombok.RequiredArgsConstructor;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.example.fresh_keep.domain.user.entity.User;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final FridgeMemberRepository fridgeMemberRepository;
    private final FridgeRepository fridgeRepository;
    private final CompartmentRepository compartmentRepository;
    private final IngredientRepository ingredientRepository;

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

    @Transactional
    @PatchMapping("/me/push-token")
    public ResponseEntity<?> updatePushToken(
            @AuthenticationPrincipal Object principal,
            @RequestBody PushTokenUpdateRequest request) {
        if (!(principal instanceof Long userId)) {
            return ResponseEntity.status(401).body(Map.of("message", "UNAUTHORIZED"));
        }

        return userRepository.findById(userId)
                .map(user -> {
                    user.updateExpoPushToken(request.getExpoPushToken());
                    userRepository.save(user);
                    return ResponseEntity.ok().build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Transactional
    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal Object principal) {
        if (!(principal instanceof Long userId)) {
            return ResponseEntity.status(401).build();
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 유저입니다."));

        // 1. 해당 유저가 속한 모든 냉장고 관계 조회
        List<FridgeMember> members = fridgeMemberRepository.findByUserId(userId);

        for (FridgeMember member : members) {
            Fridge fridge = member.getFridge();
            if (member.getRole() == MemberRole.OWNER) {
                // 2. 소유주인 경우 냉장고와 연관 데이터 완전 삭제
                List<Ingredient> ingredients = ingredientRepository.findByCompartmentFridgeId(fridge.getId());
                ingredientRepository.deleteAll(ingredients);

                List<Compartment> compartments = compartmentRepository.findByFridgeIdOrderBySequenceOrderAsc(fridge.getId());
                compartmentRepository.deleteAll(compartments);

                List<FridgeMember> fridgeMembers = fridgeMemberRepository.findByFridgeId(fridge.getId());
                fridgeMemberRepository.deleteAll(fridgeMembers);

                fridgeRepository.delete(fridge);
            } else {
                // 3. 공동 관리 멤버인 경우 본인의 멤버 관계만 삭제
                fridgeMemberRepository.delete(member);
            }
        }

        // 4. 유저 삭제
        userRepository.delete(user);

        return ResponseEntity.noContent().build();
    }

    @Data
    public static class NicknameUpdateRequest {
        private String name;
    }

    @Data
    public static class PushTokenUpdateRequest {
        private String expoPushToken;
    }
}
