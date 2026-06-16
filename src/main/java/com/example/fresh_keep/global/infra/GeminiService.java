package com.example.fresh_keep.global.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GeminiService {

    @Value("${gemini.api-key:}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=";

    public String generateStorageGuide(String ingredientName) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.error("Gemini API Key is not configured!");
            throw new IllegalStateException("Gemini API Key가 구성되지 않았습니다. 백엔드 .env 또는 환경 변수를 확인해 주세요.");
        }

        String url = GEMINI_API_URL + apiKey;

        // 프롬프트 작성 (JSON 형식 엄격 준수하도록 프롬프팅)
        String prompt = String.format(
            "당신은 최고의 신선식품 보관 전문가입니다. 입력된 식재료 '%s'에 대한 보관 방법을 설명하세요.\n" +
            "반드시 아래 JSON 스키마에 맞춰 JSON 데이터 하나만 생성하여 리턴해야 합니다.\n" +
            "마크다운 서식(예: ```json 등)이나 서론, 결론, 개행 기호, 주석 등은 일절 제외하고 순수한 JSON 객체 문자열만 리턴하세요.\n" +
            "{\n" +
            "  \"name\": \"식재료 한글 명칭\",\n" +
            "  \"emoji\": \"식재료를 나타내는 어울리는 단일 이모지\",\n" +
            "  \"category\": \"'채소', '과일', '육류/수산', '유제품', '기타' 중 딱 하나로 매핑\",\n" +
            "  \"tip\": \"식재료의 최적 보관법 핵심 요약 한 줄 (100자 내외. 냉장/냉동 보관 장소, 방법, 권장 기간 필수 포함)\",\n" +
            "  \"youtubeQuery\": \"유튜브에서 이 식재료의 보관/손질법 동영상을 검색할 때 가장 신뢰도 높은 결과가 나오는 핵심 검색어 (예: '[식재료명] 보관법')\"\n" +
            "}",
            ingredientName
        );

        try {
            // Jackson ObjectNode를 직접 사용하여 카멜 케이스 필드명 강제 유지 (Spring MessageConverter의 임의 변환 방지)
            com.fasterxml.jackson.databind.node.ObjectNode requestBody = objectMapper.createObjectNode();
            
            com.fasterxml.jackson.databind.node.ArrayNode contentsArray = requestBody.putArray("contents");
            com.fasterxml.jackson.databind.node.ObjectNode contentObj = objectMapper.createObjectNode();
            com.fasterxml.jackson.databind.node.ArrayNode partsArray = contentObj.putArray("parts");
            com.fasterxml.jackson.databind.node.ObjectNode partObj = objectMapper.createObjectNode();
            partObj.put("text", prompt);
            partsArray.add(partObj);
            contentsArray.add(contentObj);

            // JSON 문자열로 변환
            String jsonPayload = objectMapper.writeValueAsString(requestBody);

            // 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(jsonPayload, headers);

            // API 호출
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.error("Failed to query Gemini API. Status: {}", response.getStatusCode());
                throw new RuntimeException("Gemini API 호출에 실패했습니다.");
            }

            // 응답 JSON 파싱
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode textNode = root.path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text");

            if (textNode.isMissingNode()) {
                log.error("Gemini API response does not contain text node. Raw response: {}", response.getBody());
                throw new RuntimeException("Gemini API 응답에서 가이드 텍스트를 찾을 수 없습니다.");
            }

            return textNode.asText().trim();

        } catch (Exception e) {
            log.error("Error occurred while generating storage guide via Gemini API: ", e);
            throw new RuntimeException("AI 보관법 생성 도중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }
}
