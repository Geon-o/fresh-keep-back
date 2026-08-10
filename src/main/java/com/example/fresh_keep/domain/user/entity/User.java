package com.example.fresh_keep.domain.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    private String provider; // e.g., "google", "kakao"

    private String providerId; // ID from OAuth provider

    @Column(unique = true)
    private String deviceUuid;

    @Column(unique = true)
    private String backupKey;

    // Expo 푸시 발송 대상 토큰. 기기에서 발급받아 등록하며, 미발급/미동의 시 null.
    private String expoPushToken;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void updateBackupKey(String backupKey) {
        this.backupKey = backupKey;
    }

    public void updateDeviceUuid(String deviceUuid) {
        this.deviceUuid = deviceUuid;
    }

    public void updateName(String name) {
        this.name = name;
    }

    public void updateExpoPushToken(String expoPushToken) {
        this.expoPushToken = expoPushToken;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
