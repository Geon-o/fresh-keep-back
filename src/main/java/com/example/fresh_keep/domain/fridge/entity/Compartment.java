package com.example.fresh_keep.domain.fridge.entity;

import com.example.fresh_keep.domain.fridge.enums.StorageType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "compartments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Compartment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fridge_id", nullable = false)
    private Fridge fridge;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StorageType storageType;

    @Column(name = "sequence_order", nullable = false)
    private Integer sequenceOrder;

    @Column(name = "inside_shelves", columnDefinition = "TEXT")
    @Builder.Default
    private String insideShelves = "[{\"id\":\"shelf_1\",\"label\":\"선반 1단\"},{\"id\":\"shelf_2\",\"label\":\"선반 2단\"},{\"id\":\"shelf_3\",\"label\":\"선반 3단\"}]";

    @Column(name = "door_shelves", columnDefinition = "TEXT")
    @Builder.Default
    private String doorShelves = "[{\"id\":\"pocket_1\",\"label\":\"선반 1단\"},{\"id\":\"pocket_2\",\"label\":\"선반 2단\"}]";

    @Column(name = "has_door_storage", nullable = false)
    @Builder.Default
    private Boolean hasDoorStorage = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public void updateShelves(String insideShelves, String doorShelves, Boolean hasDoorStorage) {
        this.insideShelves = insideShelves;
        this.doorShelves = doorShelves;
        this.hasDoorStorage = hasDoorStorage;
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
