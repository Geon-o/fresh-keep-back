package com.example.fresh_keep.domain.memo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 공유 냉장고에 남기는 자유 메모/체크리스트. Ingredient와 동일한 이유로 fridgeId/authorUserId를
// @ManyToOne 대신 순수 컬럼으로 들고 있는다 (권한 검증/목록 조회에 연관관계 로딩이 필요 없음).
@Entity
@Table(name = "fridge_memos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class FridgeMemo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fridge_id", nullable = false)
    private Long fridgeId;

    @Column(name = "author_user_id", nullable = false)
    private Long authorUserId;

    // VARCHAR로 강제하는 이유는 IngredientHistory.actionType과 동일
    // (네이티브 ENUM 컬럼은 나중에 값을 추가해도 ddl-auto:update가 허용값을 넓혀주지 않는다).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "VARCHAR(20)")
    private MemoType type;

    // TEXT면 평문, CHECKLIST면 [{"id":"a1","text":"우유","checked":false}, ...] 형태의 JSON 문자열.
    @Column(nullable = false, length = 1000)
    private String content;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void updateContent(String content) {
        this.content = content;
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
