package learning.streaming;

public record MediaLessonAsset(
        String assetId,
        String courseTitle,
        String creatorEmail,
        String processingJobId) {
}
