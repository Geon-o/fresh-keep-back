package com.example.fresh_keep.domain.fridge.entity;

import com.example.fresh_keep.domain.fridge.enums.FridgeType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "fridges")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Fridge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FridgeType type;

    @Column(unique = true, nullable = false)
    private String uuid;

    // null이 아니면 주인이 삭제를 요청해 다른 멤버들의 동의를 기다리는 중임을 의미한다.
    private LocalDateTime deletionRequestedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void update(String name, FridgeType type) {
        this.name = name;
        this.type = type;
    }

    public void requestDeletion() {
        this.deletionRequestedAt = LocalDateTime.now();
    }

    public void cancelDeletionRequest() {
        this.deletionRequestedAt = null;
    }

    public boolean isDeletionRequested() {
        return this.deletionRequestedAt != null;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (uuid == null || uuid.trim().isEmpty()) {
            uuid = java.util.UUID.randomUUID().toString();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
