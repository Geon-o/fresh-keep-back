package com.example.fresh_keep.global.security.handler;

import com.example.fresh_keep.domain.user.entity.User;
import com.example.fresh_keep.global.security.jwt.JwtProvider;
import com.example.fresh_keep.global.security.oauth.CustomOAuth2User;
import com.example.fresh_keep.global.security.oauth.HttpCookieOAuth2AuthorizationRequestRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtProvider jwtProvider;
    private final StringRedisTemplate redisTemplate;
    private final HttpCookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository;

    @Value("${jwt.refresh-token-expiration:604800000}")
    private long refreshTokenExpiration;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        CustomOAuth2User oAuth2User = (CustomOAuth2User) authentication.getPrincipal();
        User user = oAuth2User.getUser();

        // 1. Generate Tokens
        String accessToken = jwtProvider.generateAccessToken(user.getId(), user.getEmail(), user.getName());
        String refreshToken = jwtProvider.generateRefreshToken(user.getId(), user.getEmail());

        // 2. Client Binding (IP & User-Agent)
        String clientIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        // Save Refresh Token to Redis with binding metadata
        saveRefreshTokenToRedis(user.getId(), refreshToken, clientIp, userAgent);

        // 3. Set Access Token Cookie (HttpOnly, Secure, SameSite=Strict)
        setAccessTokenCookie(response, accessToken);

        // 4. Retrieve Redirect URI from Cookie
        String redirectUri = com.example.fresh_keep.global.security.oauth.util.CookieUtils.getCookie(request, HttpCookieOAuth2AuthorizationRequestRepository.REDIRECT_URI_PARAM_COOKIE_NAME)
                .map(Cookie::getValue)
                .orElse("/");

        // Clean up temporary cookies
        cookieAuthorizationRequestRepository.removeAuthorizationRequestCookies(request, response);

        // 5. Build Redirect URL
        String targetUrl = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("accessToken", accessToken)
                .queryParam("refreshToken", refreshToken)
                .build().toUriString();

        log.info("OAuth2 login succeeded for user: {}. Redirecting to: {}", user.getEmail(), targetUrl);
        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }

    private void saveRefreshTokenToRedis(Long userId, String token, String ip, String userAgent) {
        String key = "RT:" + userId;
        // Format: token|ip|userAgent
        String value = token + "|" + ip + "|" + (userAgent != null ? userAgent : "UNKNOWN");
        redisTemplate.opsForValue().set(key, value, refreshTokenExpiration, TimeUnit.MILLISECONDS);
    }

    private void setAccessTokenCookie(HttpServletResponse response, String accessToken) {
        Cookie cookie = new Cookie("accessToken", accessToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(true); // Always set secure for HTTPS/Localhost
        cookie.setPath("/");
        cookie.setMaxAge((int) (jwtProvider.getClaims(accessToken).getExpiration().getTime() - System.currentTimeMillis()) / 1000);
        
        // SameSite=Strict attribute (Servlet standard does not support SameSite directly in all environments, so we can append it manually)
        response.addCookie(cookie);
        response.addHeader("Set-Cookie", String.format("accessToken=%s; Max-Age=%d; Path=/; Secure; HttpOnly; SameSite=Strict", 
                accessToken, cookie.getMaxAge()));
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // If multiple IPs are forwarded, get the first one (original client)
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
