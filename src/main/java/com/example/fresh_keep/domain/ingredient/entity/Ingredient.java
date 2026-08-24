package com.example.fresh_keep.domain.ingredient.entity;

import com.example.fresh_keep.domain.fridge.entity.Compartment;
import com.example.fresh_keep.domain.ingredient.enums.ExpirationType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "ingredients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 위치(구획) 미지정 등록을 허용하기 위해 nullable. 구획이 없어도 어느 냉장고 소속인지는
    // 알아야 권한 검증/목록 조회가 가능하므로 fridgeId를 별도 컬럼으로 직접 들고 있는다
    // (IngredientHistory의 fridgeId 컬럼과 동일한 패턴).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compartment_id", nullable = true)
    private Compartment compartment;

    @Column(name = "fridge_id", nullable = false)
    private Long fridgeId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Double quantity;

    @Column(nullable = false)
    private String unit; // e.g., "개", "g", "kg"

    @Column(name = "expiration_date", nullable = false)
    private LocalDate expirationDate;

    // 기존 행에는 값이 없을 수 있어 컬럼 자체는 nullable로 두고,
    // 조회 시 IngredientService#mapToResponse에서 SELL_BY로 기본 처리한다.
    @Enumerated(EnumType.STRING)
    @Column(name = "expiration_type")
    private ExpirationType expirationType;

    private String memo;

    // 등록/최종 수정한 사용자. updatedBy는 실제로 수정된 적이 있을 때만 채워진다
    // (생성 시점엔 null로 두어 "수정 이력 없음"과 구분한다).
    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void updateCompartment(Compartment compartment) {
        this.compartment = compartment;
    }

    public void update(String name, Double quantity, String unit, LocalDate expirationDate, ExpirationType expirationType, String memo, Long updatedBy) {
        this.name = name;
        this.quantity = quantity;
        this.unit = unit;
        this.expirationDate = expirationDate;
        this.expirationType = expirationType;
        this.memo = memo;
        this.updatedBy = updatedBy;
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
