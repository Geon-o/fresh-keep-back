package com.example.fresh_keep.domain.guide.service;

import com.example.fresh_keep.domain.guide.dto.StorageGuideResponse;
import com.example.fresh_keep.domain.guide.entity.StorageGuide;
import com.example.fresh_keep.domain.guide.repository.StorageGuideRepository;
import com.example.fresh_keep.global.infra.GeminiService;
import com.example.fresh_keep.global.infra.YoutubeService;
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
public class StorageGuideService {

    private final StorageGuideRepository storageGuideRepository;
    private final GeminiService geminiService;
    private final YoutubeService youtubeService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StorageGuideService(StorageGuideRepository storageGuideRepository, GeminiService geminiService, YoutubeService youtubeService) {
        this.storageGuideRepository = storageGuideRepository;
        this.geminiService = geminiService;
        this.youtubeService = youtubeService;
    }

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
            
            // 캐시 보완(Lazy Load): 비디오 ID가 없는 가이드는 실시간으로 업데이트
            for (StorageGuide guide : dbResults) {
                if (guide.getYoutubeVideoId() == null || guide.getYoutubeVideoId().trim().isEmpty()) {
                    try {
                        YoutubeService.YoutubeVideoInfo videoInfo = youtubeService.searchVideo(guide.getYoutubeQuery());
                        if (videoInfo != null) {
                            guide.updateYoutubeVideoInfo(videoInfo.getVideoId(), videoInfo.getTitle(), videoInfo.getChannelName());
                            storageGuideRepository.save(guide);
                            log.info("Lazy loaded YouTube video for cached guide '{}': {}", guide.getName(), videoInfo.getVideoId());
                        }
                    } catch (Exception e) {
                        log.error("Failed to lazy load YouTube video for guide '{}'", guide.getName(), e);
                    }
                }
            }

            return dbResults.stream()
                    .map(StorageGuideResponse::from)
                    .collect(Collectors.toList());
        }

        // 2.5. Levenshtein Distance 알고리즘을 활용해 미세한 오타 보정 및 기존 캐시 재활용 처리
        List<StorageGuide> allGuides = storageGuideRepository.findAll();
        StorageGuide bestMatch = null;
        int minDistance = Integer.MAX_VALUE;

        for (StorageGuide guide : allGuides) {
            int distance = getLevenshteinDistance(sanitizedQuery, guide.getName());
            
            // 한 자모 오타 판단 조건 임계치 설정
            // - 1글자 검색어: 보정 안함 (예: "배" vs "뱀"은 1글자 차이지만 별개 단어)
            // - 2~3글자 검색어: 1글자 오타까지만 인정 (예: "딸기" vs "떨기" 거리 1)
            // - 4글자 이상 검색어: 2글자 오타까지 허용 (예: "브로콜리" vs "보로콜리" 거리 1)
            int threshold = 0;
            if (sanitizedQuery.length() >= 4) {
                threshold = 2;
            } else if (sanitizedQuery.length() >= 2) {
                threshold = 1;
            }

            if (distance <= threshold && distance < minDistance) {
                minDistance = distance;
                bestMatch = guide;
            }
        }

        if (bestMatch != null) {
            log.info("Storage Guide Typo Corrected: '{}' mapped to existing cache '{}' (edit distance: {})", 
                    sanitizedQuery, bestMatch.getName(), minDistance);
            
            // 기존 캐시 보완(Lazy Load): 비디오 ID가 유실되어 있는 경우에 한함
            if (bestMatch.getYoutubeVideoId() == null || bestMatch.getYoutubeVideoId().trim().isEmpty()) {
                try {
                    YoutubeService.YoutubeVideoInfo videoInfo = youtubeService.searchVideo(bestMatch.getYoutubeQuery());
                    if (videoInfo != null) {
                        bestMatch.updateYoutubeVideoInfo(videoInfo.getVideoId(), videoInfo.getTitle(), videoInfo.getChannelName());
                        storageGuideRepository.save(bestMatch);
                    }
                } catch (Exception e) {
                    log.error("Failed to lazy load YouTube video for typo-corrected guide '{}'", bestMatch.getName(), e);
                }
            }
            return List.of(StorageGuideResponse.from(bestMatch));
        }

        // 3. 정확히 일치하거나 오타 매핑되는 캐시가 존재하지 않는 경우 실시간 AI 가이드 생성 진입
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
                StorageGuide guide = existing.get();
                // 이미 존재하지만 유튜브 비디오 ID가 없는 경우 유튜브 정보만 보완
                if (guide.getYoutubeVideoId() == null || guide.getYoutubeVideoId().trim().isEmpty()) {
                    YoutubeService.YoutubeVideoInfo videoInfo = youtubeService.searchVideo(guide.getYoutubeQuery());
                    if (videoInfo != null) {
                        guide.updateYoutubeVideoInfo(videoInfo.getVideoId(), videoInfo.getTitle(), videoInfo.getChannelName());
                        return storageGuideRepository.save(guide);
                    }
                }
                return guide;
            }

            // 유튜브 비디오 정보 실시간 검색 및 바인딩
            String youtubeVideoId = null;
            String youtubeVideoTitle = null;
            String youtubeChannelName = null;
            try {
                YoutubeService.YoutubeVideoInfo videoInfo = youtubeService.searchVideo(youtubeQuery);
                if (videoInfo != null) {
                    youtubeVideoId = videoInfo.getVideoId();
                    youtubeVideoTitle = videoInfo.getTitle();
                    youtubeChannelName = videoInfo.getChannelName();
                }
            } catch (Exception e) {
                log.error("Failed to fetch YouTube video info for new guide '{}'", name, e);
            }

            StorageGuide newGuide = StorageGuide.builder()
                    .name(name)
                    .emoji(emoji)
                    .category(category)
                    .tip(tip)
                    .youtubeQuery(youtubeQuery)
                    .youtubeVideoId(youtubeVideoId)
                    .youtubeVideoTitle(youtubeVideoTitle)
                    .youtubeChannelName(youtubeChannelName)
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

    /**
     * 두 문자열 간의 Levenshtein Distance (편집 거리)를 계산하여 차이나는 문자 수를 반환합니다.
     */
    private int getLevenshteinDistance(String s1, String s2) {
        if (s1 == null || s2 == null) return Integer.MAX_VALUE;
        
        // 공백 제거 및 소문자 정규화 처리
        String cleanS1 = s1.trim().replaceAll("\\s", "").toLowerCase();
        String cleanS2 = s2.trim().replaceAll("\\s", "").toLowerCase();
        
        int len1 = cleanS1.length();
        int len2 = cleanS2.length();
        
        int[][] dp = new int[len1 + 1][len2 + 1];
        
        for (int i = 0; i <= len1; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= len2; j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                int cost = (cleanS1.charAt(i - 1) == cleanS2.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[len1][len2];
    }
}
