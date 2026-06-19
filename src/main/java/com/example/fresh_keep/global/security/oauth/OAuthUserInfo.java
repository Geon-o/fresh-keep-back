package com.example.fresh_keep.global.security.oauth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class OAuthUserInfo {
    private final String providerId;
    private final String email;
    private final String name;
}
