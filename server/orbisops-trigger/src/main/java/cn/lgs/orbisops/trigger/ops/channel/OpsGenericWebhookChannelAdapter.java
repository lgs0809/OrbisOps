package cn.lgs.orbisops.trigger.ops.channel;

import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class OpsGenericWebhookChannelAdapter {

    private final OpsSecretResolver secretResolver;
    private final OpsOutboundUrlPolicy outboundUrlPolicy;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public OpsGenericWebhookChannelAdapter(OpsSecretResolver secretResolver,
                                           OpsOutboundUrlPolicy outboundUrlPolicy) {
        this.secretResolver = secretResolver;
        this.outboundUrlPolicy = outboundUrlPolicy;
    }

    public String type() {
        return "GENERIC_WEBHOOK";
    }

    public void validateConfiguration(Map<String, Object> channel) {
        String credentialRef = text(channel.get("credentialRef"));
        if (!secretResolver.isReference(credentialRef)) {
            throw new IllegalArgumentException("Webhook 渠道 credentialRef 必须使用 ${env:ENV_NAME} 引用，不能保存明文 secret");
        }
        Map<String, Object> config = objectMap(channel.get("config"));
        String outboundUrl = text(config.get("outboundUrl"));
        if (StringUtils.hasText(outboundUrl)) {
            outboundUrlPolicy.validate(outboundUrl);
        }
    }

    public Map<String, Object> send(Map<String, Object> channel,
                                    String externalConversationId,
                                    String content,
                                    Map<String, Object> metadata) {
        Map<String, Object> config = objectMap(channel.get("config"));
        String outboundUrl = text(config.get("outboundUrl"));
        if (!StringUtils.hasText(outboundUrl)) {
            return Map.of("status", "NO_OUTBOUND_ENDPOINT", "delivered", false);
        }
        String secret = secretResolver.resolve(text(channel.get("credentialRef")));
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("CHANNEL_CREDENTIAL_UNAVAILABLE：渠道 credentialRef 未解析到 secret");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("conversationId", externalConversationId);
        body.put("content", content);
        body.put("metadata", metadata == null ? Map.of() : metadata);
        String deliveryId = text(metadata == null ? null : metadata.get("deliveryId"));
        if (!StringUtils.hasText(deliveryId)) {
            throw new IllegalArgumentException("CHANNEL_DELIVERY_ID_REQUIRED");
        }
        body.put("deliveryId", deliveryId);
        long timestamp = Instant.now().getEpochSecond();
        body.put("timestamp", timestamp);
        String json = JSON.toJSONString(body);
        String signature = OpsChannelSignature.sign(secret, OpsChannelSignature.outboundPayload(json, timestamp));
        try {
            URI target = outboundUrlPolicy.validate(outboundUrl);
            HttpRequest request = HttpRequest.newBuilder(target)
                    .timeout(Duration.ofSeconds(number(config.get("timeoutSeconds"), 15)))
                    .header("Content-Type", "application/json")
                    .header("X-Ops-Timestamp", String.valueOf(timestamp))
                    .header("X-Ops-Signature", signature)
                    .header("Idempotency-Key", deliveryId)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("CHANNEL_DELIVERY_FAILED：HTTP " + response.statusCode());
            }
            return Map.of("status", "DELIVERED", "delivered", true, "httpStatus", response.statusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("CHANNEL_DELIVERY_INTERRUPTED", e);
        } catch (Exception e) {
            throw new IllegalStateException("CHANNEL_DELIVERY_FAILED：" + e.getMessage(), e);
        }
    }

    private Map<String, Object> objectMap(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private int number(Object value, int fallback) {
        try { return Math.max(1, Math.min(60, Integer.parseInt(String.valueOf(value)))); }
        catch (Exception ignored) { return fallback; }
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
}
