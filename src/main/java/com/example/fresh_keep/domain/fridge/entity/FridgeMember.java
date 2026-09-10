package com.example.fresh_keep.domain.fridge.entity;

import com.example.fresh_keep.domain.fridge.enums.MemberRole;
import com.example.fresh_keep.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "fridge_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class FridgeMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fridge_id", nullable = false)
    private Fridge fridge;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberRole role;

    // 주인의 삭제 요청에 대한 본인(멤버)의 동의 여부. 새 삭제 요청이 시작될 때마다 초기화된다.
    @Builder.Default
    @Column(nullable = false)
    private boolean deletionApproved = false;

    private LocalDateTime createdAt;

    // 메모 안읽음 배지 판정 기준 시각. null이면 "한 번도 메모 목록을 연 적 없음"으로 취급한다.
    private LocalDateTime lastMemoViewedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public void approveDeletion() {
        this.deletionApproved = true;
    }

    public void resetDeletionApproval() {
        this.deletionApproved = false;
    }

    public void markMemoViewed() {
        this.lastMemoViewedAt = LocalDateTime.now();
    }

    // 소셜 로그인 시 게스트(익명) 데이터를 기존 계정으로 합칠 때, 이 멤버십의 소유 사용자를 옮긴다.
    public void transferTo(User newUser) {
        this.user = newUser;
    }
}
