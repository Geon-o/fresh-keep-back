package com.example.fresh_keep.domain.guide.dto;

import com.example.fresh_keep.domain.guide.entity.StorageGuide;
import lombok.Builder;
import lombok.Getter;

@Getter
public class StorageGuideResponse {
    private final String name;
    private final String emoji;
    private final String category;
    private final String tip;
    private final String youtubeQuery;
    private final VideoInfo video; // Nullable for dynamically generated tips

    @Builder
    public StorageGuideResponse(String name, String emoji, String category, String tip, String youtubeQuery, VideoInfo video) {
        this.name = name;
        this.emoji = emoji;
        this.category = category;
        this.tip = tip;
        this.youtubeQuery = youtubeQuery;
        this.video = video;
    }

    public static StorageGuideResponse from(StorageGuide guide) {
        return StorageGuideResponse.builder()
                .name(guide.getName())
                .emoji(guide.getEmoji())
                .category(guide.getCategory())
                .tip(guide.getTip())
                .youtubeQuery(guide.getYoutubeQuery())
                .video(null) // Dynamic AI responses don't have hardcoded video details initially
                .build();
    }

    @Getter
    public static class VideoInfo {
        private final String title;
        private final String channelName;
        private final String videoId;
        private final String duration;

        @Builder
        public VideoInfo(String title, String channelName, String videoId, String duration) {
            this.title = title;
            this.channelName = channelName;
            this.videoId = videoId;
            this.duration = duration;
        }
    }
}
