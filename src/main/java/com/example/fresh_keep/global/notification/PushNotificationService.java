package com.example.fresh_keep.global.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

// Expo 푸시 API(https://exp.host/--/api/v2/push/send)로 알림을 보낸다.
// 응답을 기다리지 않아도 되는 부가 기능이라 API 요청/DB 트랜잭션과 분리해 비동기로 실행한다.
@Slf4j
@Service
public class PushNotificationService {

    private static final URI EXPO_PUSH_URL = URI.create("https://exp.host/--/api/v2/push/send");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void send(String expoPushToken, String title, String body) {
        send(expoPushToken, title, body, null);
    }

    // data: 클라이언트가 포그라운드에서 알림을 수신했을 때(addNotificationReceivedListener)
    // 어떤 화면/쿼리를 갱신해야 하는지 구분할 수 있도록 담아 보내는 부가 정보.
    @Async
    public void send(String expoPushToken, String title, String body, Map<String, Object> data) {
        if (!StringUtils.hasText(expoPushToken)) {
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("to", expoPushToken);
            payload.put("title", title);
            payload.put("body", body);
            if (data != null) {
                payload.put("data", data);
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(EXPO_PUSH_URL)
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Expo push send failed: status={}, body={}", response.statusCode(), response.body());
            }
        } catch (Exception e) {
            // 푸시 발송 실패가 본 기능(냉장고 삭제 요청/동의 등)을 막으면 안 되므로 로그만 남긴다.
            log.warn("Failed to send Expo push notification", e);
        }
    }
}
