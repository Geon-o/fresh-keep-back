package com.example.fresh_keep.domain.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class IdTokenVerificationRequest {
    private String idToken;
    private String provider; // "google", "kakao", "naver"
}
