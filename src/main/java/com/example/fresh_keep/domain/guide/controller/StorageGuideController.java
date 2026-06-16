package com.example.fresh_keep.domain.guide.controller;

import com.example.fresh_keep.domain.guide.dto.StorageGuideResponse;
import com.example.fresh_keep.domain.guide.service.StorageGuideService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/guide")
@RequiredArgsConstructor
public class StorageGuideController {

    private final StorageGuideService storageGuideService;

    @GetMapping("/search")
    public ResponseEntity<?> searchGuides(
            @RequestParam("query") String query,
            @AuthenticationPrincipal Object principal) {

        if (!(principal instanceof Long userId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            List<StorageGuideResponse> results = storageGuideService.searchGuides(query, userId);
            return ResponseEntity.ok(results);
        } catch (IllegalArgumentException | IllegalStateException e) {
            // 비즈니스 예외 (글자수 초과, Rate Limiting 제한 도달 등)는 400 Bad Request로 처리하고 메시지를 전달
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "보관법 조회 중 서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."));
        }
    }
}
