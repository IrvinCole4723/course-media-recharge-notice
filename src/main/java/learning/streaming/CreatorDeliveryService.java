package learning.streaming;

import java.math.BigDecimal;
import java.util.List;

public final class CreatorDeliveryService {
    private final MediaRechargeGateway gateway;
    private final LayeredMediaConfig config;

    public CreatorDeliveryService(MediaRechargeGateway gateway, LayeredMediaConfig config) {
        this.gateway = gateway;
        this.config = config;
    }

    public CreatorDeliveryResult deliver(MediaLessonAsset asset, boolean rechargeActivated) {
        gateway.reviewBalance();
        gateway.configureAutoRecharge(config.triggerBalance(), config.rechargeAmount(), "media-rule-" + asset.assetId());

        List<String> stages = new java.util.ArrayList<>(List.of("INGESTED", "PROCESSING_QUEUED"));
        if (rechargeActivated) {
            gateway.sendCreatorRechargeNotice(asset.creatorEmail(), asset.courseTitle(), asset.processingJobId(),
                    "media-notice-" + asset.assetId());
            stages.add("RECHARGE_NOTICE_SENT");
        }
        stages.add("CREATOR_NOTIFIED");
        return new CreatorDeliveryResult(asset.assetId(), asset.processingJobId(), stages);
    }

    public record CreatorDeliveryResult(String assetId, String processingJobId, List<String> stages) {
    }

    public interface MediaRechargeGateway {
        void reviewBalance();

        void configureAutoRecharge(BigDecimal triggerBalance, BigDecimal rechargeAmount, String idempotencyKey);

        String sendCreatorRechargeNotice(String recipient, String courseTitle, String processingJobId, String idempotencyKey);
    }
}
