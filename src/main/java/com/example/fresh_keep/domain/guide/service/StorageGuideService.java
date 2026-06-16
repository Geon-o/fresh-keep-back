package com.example.fresh_keep.domain.guide.service;

import com.example.fresh_keep.domain.guide.dto.StorageGuideResponse;
import com.example.fresh_keep.domain.guide.entity.StorageGuide;
import com.example.fresh_keep.domain.guide.repository.StorageGuideRepository;
import com.example.fresh_keep.global.infra.GeminiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageGuideService {

    private final StorageGuideRepository storageGuideRepository;
    private final GeminiService geminiService;
    private final ObjectMapper objectMapper;

    // 인메모리 Rate Limiting을 위한 저장소 (유저 ID별 신규 생성 API 호출 시각 기록)
    private final Map<Long, List<Long>> creationTimestamps = new ConcurrentHashMap<>();
    private static final int MAX_CREATIONS_PER_MINUTE = 5;
    private static final long ONE_MINUTE_IN_MS = 60000L;

    /**
     * 식재료 보관 가이드 검색 (하이브리드 캐싱)
     */
    @Transactional
    public List<StorageGuideResponse> searchGuides(String query, Long userId) {
        // 1. 입력값 1차 검증 및 Sanitization
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }
        
        String sanitizedQuery = query.trim().replaceAll("[^a-zA-Z0-9가-힣\\s]", ""); // 특수문자 제거
        if (sanitizedQuery.length() > 15) {
            throw new IllegalArgumentException("검색어는 최대 15자까지만 입력 가능합니다.");
        }

        // 2. DB 캐시 조회 (부분 일치 검색)
        List<StorageGuide> dbResults = storageGuideRepository.findByNameContaining(sanitizedQuery);
        
        if (!dbResults.isEmpty()) {
            log.info("Storage Guide Cache Hit for query: '{}'. Found {} items.", sanitizedQuery, dbResults.size());
            return dbResults.stream()
                    .map(StorageGuideResponse::from)
                    .collect(Collectors.toList());
        }

        // 3. 정확히 일치하는 단일 캐시가 존재하지 않는 경우 실시간 AI 가이드 생성 진입
        log.info("Storage Guide Cache Miss for query: '{}'. Requesting Gemini AI...", sanitizedQuery);
        
        // 4. Rate Limiting 검증
        checkRateLimit(userId);

        // 5. Gemini API 호출
        String aiJsonResponse = geminiService.generateStorageGuide(sanitizedQuery);
        
        // 6. 응답 파싱 및 안전한 엔티티 저장
        StorageGuide savedGuide = parseAndSaveGuide(aiJsonResponse, sanitizedQuery);

        // API 생성 이력 추가 (Rate Limit 용)
        recordCreation(userId);

        return List.of(StorageGuideResponse.from(savedGuide));
    }

    /**
     * AI 응답 JSON을 파싱하고 보안 필터링을 거쳐 저장함
     */
    private StorageGuide parseAndSaveGuide(String jsonContent, String originalQuery) {
        try {
            JsonNode root = objectMapper.readTree(jsonContent);
            
            // XSS 방지를 위한 HTML 이스케이프 적용
            String name = HtmlUtils.htmlEscape(root.path("name").asText(originalQuery).trim());
            String emoji = HtmlUtils.htmlEscape(root.path("emoji").asText("💡").trim());
            String category = HtmlUtils.htmlEscape(root.path("category").asText("기타").trim());
            String tip = HtmlUtils.htmlEscape(root.path("tip").asText("적절한 보관 온도를 유지해 주세요.").trim());
            String youtubeQuery = HtmlUtils.htmlEscape(root.path("youtubeQuery").asText(name + " 보관법").trim());

            // 카테고리 유효성 보정 (정해진 카테고리가 아닐 시 '기타'로 강제 분류)
            List<String> validCategories = Arrays.asList("채소", "과일", "육류/수산", "유제품", "기타");
            if (!validCategories.contains(category)) {
                category = "기타";
            }

            // DB 중복 방지 (동시성 요청에 의한 중복 키 에러 방어)
            Optional<StorageGuide> existing = storageGuideRepository.findByName(name);
            if (existing.isPresent()) {
                return existing.get();
            }

            StorageGuide newGuide = StorageGuide.builder()
                    .name(name)
                    .emoji(emoji)
                    .category(category)
                    .tip(tip)
                    .youtubeQuery(youtubeQuery)
                    .build();

            return storageGuideRepository.save(newGuide);

        } catch (Exception e) {
            log.error("Failed to parse Gemini response: " + jsonContent, e);
            throw new RuntimeException("AI가 생성한 데이터를 저장하는 중 분석 실패가 발생했습니다.", e);
        }
    }

    /**
     * 1분 슬라이딩 윈도우 기반 Rate Limiting 검사
     */
    private void checkRateLimit(Long userId) {
        if (userId == null) return;

        long now = System.currentTimeMillis();
        List<Long> timestamps = creationTimestamps.computeIfAbsent(userId, k -> new ArrayList<>());

        synchronized (timestamps) {
            // 1분 이전의 타임스탬프 제거
            timestamps.removeIf(time -> (now - time) > ONE_MINUTE_IN_MS);

            if (timestamps.size() >= MAX_CREATIONS_PER_MINUTE) {
                log.warn("Rate limit exceeded for user ID: {}. Current count: {}", userId, timestamps.size());
                throw new IllegalStateException("단시간 내에 너무 많은 AI 가이드가 생성되었습니다. 잠시 후(1분 뒤) 다시 시도해 주세요.");
            }
        }
    }

    private void recordCreation(Long userId) {
        if (userId == null) return;

        List<Long> timestamps = creationTimestamps.get(userId);
        if (timestamps != null) {
            synchronized (timestamps) {
                timestamps.add(System.currentTimeMillis());
            }
        }
    }
}
