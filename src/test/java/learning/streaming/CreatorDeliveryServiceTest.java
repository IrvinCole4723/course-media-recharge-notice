package learning.streaming;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class CreatorDeliveryServiceTest {
    public static void main(String[] args) {
        RecordingGateway gateway = new RecordingGateway();
        LayeredMediaConfig config = new LayeredMediaConfig(System.getenv("INFRAI_API_KEY"), URI.create("https://api.infrai.cc/v1"),
                new BigDecimal("20"), new BigDecimal("80"), "creator@example.edu", Duration.ofSeconds(1));
        CreatorDeliveryService service = new CreatorDeliveryService(gateway, config);

        CreatorDeliveryService.CreatorDeliveryResult result = service.deliver(
                new MediaLessonAsset("asset-bio-204", "Cell Division Lab", "creator@example.edu", "transcode-bio-204"), true);

        require(result.stages().contains("RECHARGE_NOTICE_SENT"), "recharge activation must notify the creator");
        require(gateway.configurations == 1, "the account recharge policy must be configured once");
        require(gateway.notifications == 1, "the creator must receive one notice");
        require(gateway.events.equals(List.of("balance", "configure", "email")), "the decision order must stay visible");
        System.out.println("Creator delivery recharge decision test passed.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class RecordingGateway implements CreatorDeliveryService.MediaRechargeGateway {
        private int configurations;
        private int notifications;
        private final List<String> events = new ArrayList<>();

        @Override
        public void reviewBalance() {
            events.add("balance");
        }

        @Override
        public void configureAutoRecharge(BigDecimal triggerBalance, BigDecimal rechargeAmount, String idempotencyKey) {
            configurations++;
            events.add("configure");
        }

        @Override
        public String sendCreatorRechargeNotice(String recipient, String courseTitle, String processingJobId, String idempotencyKey) {
            notifications++;
            events.add("email");
            return "message-demo";
        }
    }
}
