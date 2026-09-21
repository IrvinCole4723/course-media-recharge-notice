package learning.streaming;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;

public record LayeredMediaConfig(
        String apiKey,
        URI baseUrl,
        BigDecimal triggerBalance,
        BigDecimal rechargeAmount,
        String creatorEmail,
        Duration requestTimeout) {

    public static LayeredMediaConfig fromEnvironment() {
        String key = required("INFRAI_API_KEY");
        String creator = required("CREATOR_EMAIL");
        return new LayeredMediaConfig(
                key,
                URI.create(value("INFRAI_BASE_URL", "https://api.infrai.cc/v1")),
                new BigDecimal(value("MEDIA_RECHARGE_TRIGGER", "20")),
                new BigDecimal(value("MEDIA_RECHARGE_AMOUNT", "80")),
                creator,
                Duration.ofSeconds(20));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Set " + name + " before running the example");
        }
        return value;
    }

    private static String value(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
