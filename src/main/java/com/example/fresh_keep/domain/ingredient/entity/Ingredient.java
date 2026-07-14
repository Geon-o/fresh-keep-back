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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "compartment_id", nullable = false)
    private Compartment compartment;

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

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void updateCompartment(Compartment compartment) {
        this.compartment = compartment;
    }

    public void update(String name, Double quantity, String unit, LocalDate expirationDate, ExpirationType expirationType, String memo) {
        this.name = name;
        this.quantity = quantity;
        this.unit = unit;
        this.expirationDate = expirationDate;
        this.expirationType = expirationType;
        this.memo = memo;
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
