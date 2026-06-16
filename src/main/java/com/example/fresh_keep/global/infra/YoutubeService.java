package com.example.fresh_keep.global.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
public class YoutubeService {

    @Value("${youtube.api-key:}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String YOUTUBE_SEARCH_URL = "https://www.googleapis.com/youtube/v3/search";

    @Getter
    public static class YoutubeVideoInfo {
        private final String videoId;
        private final String title;
        private final String channelName;

        @Builder
        public YoutubeVideoInfo(String videoId, String title, String channelName) {
            this.videoId = videoId;
            this.title = title;
            this.channelName = channelName;
        }
    }

    public YoutubeVideoInfo searchVideo(String query) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn("YouTube API Key is not configured. Skipping YouTube search.");
            return null;
        }

        // 쿼리 안전화 및 보정: 검색어에 "보관"이나 "손질"이 포함되어 있지 않으면 " 보관법"을 뒤에 붙여 관련 영상만 유도
        String searchQuery = (query != null) ? query.trim() : "";
        if (searchQuery.isEmpty()) {
            searchQuery = "식재료 보관법";
        } else if (!searchQuery.contains("보관") && !searchQuery.contains("손질")) {
            searchQuery = searchQuery + " 보관법";
        }

        try {
            String url = UriComponentsBuilder.fromUriString(YOUTUBE_SEARCH_URL)
                    .queryParam("part", "snippet")
                    .queryParam("q", searchQuery)
                    .queryParam("type", "video")
                    .queryParam("order", "viewCount") // 조회수 높은 순 정렬
                    .queryParam("maxResults", 1)
                    .queryParam("key", apiKey)
                    .build()
                    .toUriString();

            log.info("Requesting YouTube Data API for query: '{}' (original query: '{}')", searchQuery, query);
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.error("Failed to query YouTube API. Status: {}", response.getStatusCode());
                return null;
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode items = root.path("items");

            if (items.isArray() && items.size() > 0) {
                JsonNode firstItem = items.get(0);
                String videoId = firstItem.path("id").path("videoId").asText();
                String title = firstItem.path("snippet").path("title").asText();
                String channelName = firstItem.path("snippet").path("channelTitle").asText();

                log.info("Successfully fetched video from YouTube API: videoId='{}', title='{}'", videoId, title);
                return YoutubeVideoInfo.builder()
                        .videoId(videoId)
                        .title(title)
                        .channelName(channelName)
                        .build();
            } else {
                log.warn("No video search results found on YouTube for query: '{}'", query);
                return null;
            }

        } catch (Exception e) {
            log.error("Error occurred while searching YouTube video: ", e);
            // YouTube API 장애로 인해 전체 기능이 마비되지 않도록 예외를 잡아서 null을 리턴
            return null;
        }
    }
}
