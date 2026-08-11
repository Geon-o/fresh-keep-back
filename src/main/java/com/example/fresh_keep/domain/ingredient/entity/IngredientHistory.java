package com.example.fresh_keep.domain.ingredient.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 공유 냉장고의 "누가 언제 무엇을 추가/수정했는지" 기록. 식재료가 나중에 삭제되더라도
// 이력은 남아야 하므로 이름/작성자명을 당시 값 그대로 스냅샷해서 저장한다(라이브 조회 X).
@Entity
@Table(name = "ingredient_histories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class IngredientHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fridge_id", nullable = false)
    private Long fridgeId;

    @Column(name = "ingredient_name", nullable = false)
    private String ingredientName;

    // columnDefinition으로 VARCHAR를 강제한다: MySQL 방언이 STRING 매핑을 네이티브 ENUM 컬럼으로
    // 만들어버리면 나중에 enum 값을 추가해도 ddl-auto:update가 기존 컬럼의 허용값 목록을 넓혀주지 않아
    // "Data truncated" 에러가 난다. VARCHAR면 이런 문제 자체가 생기지 않는다.
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, columnDefinition = "VARCHAR(20)")
    private HistoryActionType actionType;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_name")
    private String actorName;

    // 수정 시 변경된 항목을 "필드: 이전값 → 새값" 형식으로 이어붙인 요약. 등록 시에는 null.
    @Column(length = 500)
    private String summary;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
