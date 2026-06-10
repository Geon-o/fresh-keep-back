package com.example.fresh_keep.global.config;

import com.example.fresh_keep.global.security.filter.JwtAuthenticationFilter;
import com.example.fresh_keep.global.security.handler.OAuth2SuccessHandler;
import com.example.fresh_keep.global.security.oauth.CustomOAuth2UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final com.example.fresh_keep.global.security.oauth.HttpCookieOAuth2AuthorizationRequestRepository httpCookieOAuth2AuthorizationRequestRepository;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, org.springframework.security.oauth2.client.registration.ClientRegistrationRepository clientRegistrationRepository) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable) // Stateless JWT environment
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)) // Allow H2 Console frames
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/h2-console", "/h2-console/**", "/api/auth/**", "/oauth2/**", "/login/oauth2/**", "/error").permitAll()
                        .anyRequest().authenticated()
                )
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(authorization -> authorization
                                .authorizationRequestRepository(httpCookieOAuth2AuthorizationRequestRepository)
                                .authorizationRequestResolver(customAuthorizationRequestResolver(clientRegistrationRepository)))
                        .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                        .successHandler(oAuth2SuccessHandler)
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver customAuthorizationRequestResolver(
            org.springframework.security.oauth2.client.registration.ClientRegistrationRepository clientRegistrationRepository) {
        
        org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver defaultResolver = 
                new org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver(
                        clientRegistrationRepository, "/oauth2/authorization");
        
        return new org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver() {
            @Override
            public org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest resolve(jakarta.servlet.http.HttpServletRequest request) {
                org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest authRequest = defaultResolver.resolve(request);
                return customize(authRequest, request);
            }

            @Override
            public org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest resolve(jakarta.servlet.http.HttpServletRequest request, String clientRegistrationId) {
                org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest authRequest = defaultResolver.resolve(request, clientRegistrationId);
                return customize(authRequest, clientRegistrationId);
            }

            private org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest customize(
                    org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest authRequest, jakarta.servlet.http.HttpServletRequest request) {
                if (authRequest == null) return null;
                String requestUri = request.getRequestURI();
                String clientRegistrationId = requestUri.substring(requestUri.lastIndexOf('/') + 1);
                return customize(authRequest, clientRegistrationId);
            }

            private org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest customize(
                    org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest authRequest, String clientRegistrationId) {
                if (authRequest == null) return null;
                
                if ("google".equalsIgnoreCase(clientRegistrationId)) {
                    java.util.Map<String, Object> additionalParameters = new java.util.HashMap<>(authRequest.getAdditionalParameters());
                    additionalParameters.put("prompt", "select_account");
                    return org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest.from(authRequest)
                            .additionalParameters(additionalParameters)
                            .build();
                }
                
                return authRequest;
            }
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Allow common frontend development origins (web, local Expo packager, etc.)
        configuration.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:[*]",
                "http://127.0.0.1:[*]",
                "http://192.168.*.*:[*]",
                "https://*.trycloudflare.com"
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Collections.singletonList("*"));
        configuration.setExposedHeaders(Arrays.asList("Set-Cookie", "Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
