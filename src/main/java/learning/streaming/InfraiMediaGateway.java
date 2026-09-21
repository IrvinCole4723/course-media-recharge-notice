package learning.streaming;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InfraiMediaGateway implements CreatorDeliveryService.MediaRechargeGateway {
    private static final Pattern OK = Pattern.compile("\\\"ok\\\"\\s*:\\s*(true|false)");
    private static final Pattern MESSAGE_ID = Pattern.compile("\\\"message_id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private final HttpClient client;
    private final LayeredMediaConfig config;

    public InfraiMediaGateway(LayeredMediaConfig config) {
        this(HttpClient.newBuilder().connectTimeout(config.requestTimeout()).build(), config);
    }

    InfraiMediaGateway(HttpClient client, LayeredMediaConfig config) {
        this.client = client;
        this.config = config;
    }

    @Override
    public void reviewBalance() {
        // Canonical capability idiom: infrai.account.balance
        send("GET", "/v1/account/balance", null, null);
    }

    @Override
    public void configureAutoRecharge(BigDecimal triggerBalance, BigDecimal rechargeAmount, String idempotencyKey) {
        // Canonical capability idiom: infrai.account.autorecharge.configure
        String body = "{\"trigger_balance\":" + triggerBalance + ",\"recharge_amount\":" + rechargeAmount + "}";
        send("PUT", "/v1/account/autorecharge/configure", body, idempotencyKey);
    }

    @Override
    public String sendCreatorRechargeNotice(String recipient, String courseTitle, String processingJobId, String idempotencyKey) {
        // Canonical capability idiom: infrai.email.send
        String text = "Recharge policy activated while " + courseTitle + " is processing. Job: " + processingJobId;
        String body = "{\"to\":\"" + json(recipient) + "\",\"subject\":\"" + json("Media processing continues")
                + "\",\"body\":\"" + json(text) + "\"}";
        String envelope = send("POST", "/v1/email/send", body, idempotencyKey);
        Matcher messageId = MESSAGE_ID.matcher(envelope);
        if (!messageId.find()) {
            throw new IllegalStateException("Successful email response did not contain message_id");
        }
        return messageId.group(1);
    }

    private String send(String method, String path, String body, String idempotencyKey) {
        for (int attempt = 0; attempt < 3; attempt++) {
            HttpRequest.Builder request = HttpRequest.newBuilder(resolve(path))
                    .timeout(config.requestTimeout())
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Accept", "application/json");
            if (idempotencyKey != null) {
                request.header("Idempotency-Key", idempotencyKey);
            }
            if (body == null) {
                request.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                request.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(body));
            }
            HttpResponse<String> response = execute(request.build());
            Envelope envelope = Envelope.parse(response.body());
            if (response.statusCode() == 429 && attempt < 2) {
                pause(retryDelay(response, attempt));
                continue;
            }
            if (!envelope.ok()) {
                throw new InfraiRejectedException(envelope.error(), response.statusCode());
            }
            if (response.statusCode() >= 500) {
                throw new IllegalStateException("Transport response could not be completed");
            }
            return response.body();
        }
        throw new IllegalStateException("Rate limit retry sequence ended");
    }

    private HttpResponse<String> execute(HttpRequest request) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException error) {
            throw new IllegalStateException("HTTP request could not be sent", error);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HTTP request was interrupted", error);
        }
    }

    private URI resolve(String path) {
        return config.baseUrl().resolve(path);
    }

    private static Duration retryDelay(HttpResponse<?> response, int attempt) {
        Optional<String> retryAfter = response.headers().firstValue("Retry-After");
        try {
            return Duration.ofSeconds(Math.max(1, Long.parseLong(retryAfter.orElse(""))));
        } catch (NumberFormatException ignored) {
            return Duration.ofMillis(250L * (1L << attempt));
        }
    }

    private static void pause(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry was interrupted", error);
        }
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private record Envelope(boolean ok, String error) {
        static Envelope parse(String body) {
            Matcher matcher = OK.matcher(body);
            if (!matcher.find()) {
                throw new IllegalStateException("Response was not an Infrai envelope");
            }
            return new Envelope(Boolean.parseBoolean(matcher.group(1)), "Request was rejected");
        }
    }

    public static final class InfraiRejectedException extends RuntimeException {
        InfraiRejectedException(String message, int status) {
            super(message + " (HTTP " + status + ")");
        }
    }
}
