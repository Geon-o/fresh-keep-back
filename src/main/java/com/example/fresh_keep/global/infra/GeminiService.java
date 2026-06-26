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

    @Value("${gemini.model:gemini-flash-latest}")
    private String model;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();



    public String generateStorageGuide(String ingredientName) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.error("Gemini API Key is not configured!");
            throw new IllegalStateException("Gemini API Key가 구성되지 않았습니다. 백엔드 .env 또는 환경 변수를 확인해 주세요.");
        }


        String url = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s", model, apiKey);

        // 프롬프트 작성 (JSON 형식 엄격 준수하도록 프롬프팅)
        String prompt = String.format(
            "당신은 대한민국 최고의 식품공학 박사이자 신선식품 보관 전문가입니다. 입력된 식재료 '%s'에 대해 과학적이고 정확한 보관 방법을 분석하여 제공하세요.\n\n" +
            "정보 작성 지침:\n" +
            "1. name: 입력된 식재료를 대중적이고 명확한 표준 한글 명칭으로 정규화하세요 (예: '멜론', '돼지고기 삼겹살').\n" +
            "2. category: '채소', '과일', '육류/수산', '유제품', '기타' 중 하나를 가장 어울리는 카테고리로 엄격하게 매핑하세요.\n" +
            "3. tip: 식재료의 보관 방식을 과학적 근거에 기반하여 구체적으로 제시해야 합니다. 단순히 '신선하게 보관' 같은 추상적인 멘트는 금지하며, 다음 내용을 모두 포함하여 100~130자 내외로 상세히 작성하세요:\n" +
            "   - 세척 유무 (예: '씻지 말고', '씻어서 물기 제거 후')\n" +
            "   - 구체적인 포장 및 장소 (예: '키친타월로 감싸 지퍼백에 밀봉 후 냉장 보관')\n" +
            "   - 권장 보관 기간 (예: '냉장 약 1주일, 장기 보관 시 냉동')\n" +
            "   - 온도/습도 민감성 등 핵심 주의사항 (예: '에틸렌 가스 방출 과일과 격리')\n" +
            "4. youtubeQuery: 이 식재료의 가장 효과적인 보관법 및 손질법 영상을 조회할 수 있도록 '[식재료명] 보관법' 또는 '[식재료명] 손질법' 형태로 작성하세요.\n\n" +
            "반드시 아래 JSON 스키마에 맞춰 JSON 데이터 하나만 생성하여 리턴해야 합니다.\n" +
            "마크다운 서식(예: ```json 등)이나 서론, 결론, 개행 기호, 주석 등은 일절 제외하고 순수한 JSON 객체 문자열만 리턴하세요.\n" +
            "{\n" +
            "  \"name\": \"정규화된 식재료 표준 한글 명칭\",\n" +
            "  \"emoji\": \"식재료를 나타내는 어울리는 단일 이모지\",\n" +
            "  \"category\": \"카테고리\",\n" +
            "  \"tip\": \"정확하고 구체적인 보관법 요약 한 줄\",\n" +
            "  \"youtubeQuery\": \"유튜브 검색 키워드\"\n" +
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

            // API 호출 (일시적인 503/429 장애 극복을 위해 최대 3회 자동 재시도 적용)
            ResponseEntity<String> response = null;
            int maxAttempts = 3;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    response = restTemplate.postForEntity(url, entity, String.class);
                    if (response != null && response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                        break; // 성공 시 루프 탈출
                    }
                } catch (org.springframework.web.client.HttpStatusCodeException e) {
                    // 503 (Service Unavailable) 또는 429 (Too Many Requests) 에러 시 점진적 대기 후 재시도
                    if ((e.getStatusCode().value() == 503 || e.getStatusCode().value() == 429) && attempt < maxAttempts) {
                        log.warn("Gemini API returned {}. Retrying attempt {}/{} after delay...", e.getStatusCode(), attempt, maxAttempts);
                        try {
                            Thread.sleep(1500 * attempt); // 1차 1.5초, 2차 3초 대기
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    } else {
                        throw e; // 다른 에러이거나 마지막 재시도 실패 시 상위 catch 절로 던짐
                    }
                }
            }

            if (response == null || !response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.error("Failed to query Gemini API. Status: {}", response != null ? response.getStatusCode() : "NULL");
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

        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            log.error("Gemini API Http Error. Status: {}, Response: {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            if (e.getStatusCode().value() == 503 || e.getStatusCode().value() == 429) {
                throw new IllegalStateException("AI 보관법 안내 서비스에 일시적으로 많은 요청이 몰려 조회가 어렵습니다. 잠시 후 다시 시도해 주세요.", e);
            }
            throw new RuntimeException("AI 보관법 생성 도중 오류가 발생했습니다: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error occurred while generating storage guide via Gemini API: ", e);
            throw new RuntimeException("AI 보관법 생성 도중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }
}
