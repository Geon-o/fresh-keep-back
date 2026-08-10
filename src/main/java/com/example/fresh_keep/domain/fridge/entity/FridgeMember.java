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
}
