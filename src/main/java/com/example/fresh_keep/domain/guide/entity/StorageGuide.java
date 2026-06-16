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

    @Column(name = "youtube_video_id", nullable = true)
    private String youtubeVideoId;

    @Column(name = "youtube_video_title", nullable = true)
    private String youtubeVideoTitle;

    @Column(name = "youtube_channel_name", nullable = true)
    private String youtubeChannelName;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Builder
    public StorageGuide(String name, String emoji, String category, String tip, String youtubeQuery, 
                        String youtubeVideoId, String youtubeVideoTitle, String youtubeChannelName) {
        this.name = name;
        this.emoji = emoji;
        this.category = category;
        this.tip = tip;
        this.youtubeQuery = youtubeQuery;
        this.youtubeVideoId = youtubeVideoId;
        this.youtubeVideoTitle = youtubeVideoTitle;
        this.youtubeChannelName = youtubeChannelName;
        this.createdAt = LocalDateTime.now();
    }

    public void updateYoutubeVideoInfo(String youtubeVideoId, String youtubeVideoTitle, String youtubeChannelName) {
        this.youtubeVideoId = youtubeVideoId;
        this.youtubeVideoTitle = youtubeVideoTitle;
        this.youtubeChannelName = youtubeChannelName;
    }
}
