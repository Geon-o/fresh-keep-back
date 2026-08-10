package com.example.fresh_keep.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

// 푸시 알림 발송처럼 응답을 기다릴 필요 없는 작업을 API 응답 흐름과 분리해 비동기로 실행하기 위한 설정.
@Configuration
@EnableAsync
public class AsyncConfig {
}
