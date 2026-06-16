package com.example.fresh_keep.domain.guide.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "storage_guide",
    indexes = {@Index(name = "idx_guide_name", columnList = "name", unique = true)}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StorageGuide {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = true)
    private String emoji;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false, length = 500)
    private String tip;

    @Column(nullable = false)
    private String youtubeQuery;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public StorageGuide(String name, String emoji, String category, String tip, String youtubeQuery) {
        this.name = name;
        this.emoji = emoji;
        this.category = category;
        this.tip = tip;
        this.youtubeQuery = youtubeQuery;
        this.createdAt = LocalDateTime.now();
    }
}
