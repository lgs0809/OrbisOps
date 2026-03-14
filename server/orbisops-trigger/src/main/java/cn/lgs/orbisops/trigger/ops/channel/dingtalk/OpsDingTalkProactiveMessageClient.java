package cn.lgs.orbisops.trigger.ops.channel.dingtalk;

import cn.lgs.orbisops.application.channel.provider.ChannelDeliveryReceipt;
import cn.lgs.orbisops.application.channel.provider.ChannelOutboundMessage;
import com.alibaba.fastjson2.JSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Durable outbound path used when a short-lived session webhook is unavailable. */
@Component
final class OpsDingTalkProactiveMessageClient {

    private static final String API_BASE = "https://api.dingtalk.com";
    private final OpsDingTalkAccessTokenClient tokens;
    private final RestClient http;

    @Autowired
    OpsDingTalkProactiveMessageClient(OpsDingTalkAccessTokenClient tokens) {
        this(tokens, RestClient.builder().baseUrl(API_BASE).build());
    }

    OpsDingTalkProactiveMessageClient(OpsDingTalkAccessTokenClient tokens, RestClient http) {
        if (tokens == null) throw new IllegalArgumentException("DINGTALK_ACCESS_TOKEN_CLIENT_REQUIRED");
        if (http == null) throw new IllegalArgumentException("DINGTALK_HTTP_CLIENT_REQUIRED");
        this.tokens = tokens;
        this.http = http;
    }

    ChannelDeliveryReceipt send(OpsDingTalkChannelConfiguration configuration,
                                ChannelOutboundMessage message) {
        if (configuration == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        if (message == null) throw new IllegalArgumentException("CHANNEL_OUTBOUND_MESSAGE_REQUIRED");
        Target target = Target.parse(message.conversation().externalConversationId());
        String accessToken = tokens.token(configuration);
        String markdown = message.content().markdown().isBlank()
                ? message.content().plainText()
                : message.content().markdown();
        String msgParam = JSON.toJSONString(Map.of("title", "OrbisOps", "text", markdown));
        Map<String, Object> body = target.group()
                ? Map.of(
                        "robotCode", configuration.robotCode(),
                        "openConversationId", target.value(),
                        "msgKey", "sampleMarkdown",
                        "msgParam", msgParam)
                : Map.of(
                        "robotCode", configuration.robotCode(),
                        "userIds", List.of(target.value()),
                        "msgKey", "sampleMarkdown",
                        "msgParam", msgParam);
        String uri = target.group()
                ? "/v1.0/robot/groupMessages/send"
                : "/v1.0/robot/oToMessages/batchSend";
        Map<?, ?> response = http.post()
                .uri(uri)
                .header("x-acs-dingtalk-access-token", accessToken)
                .body(body)
                .retrieve()
                .body(Map.class);
        String processKey = response == null || response.get("processQueryKey") == null
                ? ""
                : String.valueOf(response.get("processQueryKey")).trim();
        return new ChannelDeliveryReceipt(true, "DELIVERED", processKey, null, Instant.now());
    }

    record Target(boolean group, String value) {
        static Target parse(String raw) {
            String normalized = raw == null ? "" : raw.trim();
            if (normalized.startsWith("group:") && normalized.length() > 6) {
                return new Target(true, normalized.substring(6));
            }
            if (normalized.startsWith("user:") && normalized.length() > 5) {
                return new Target(false, normalized.substring(5));
            }
            throw new IllegalArgumentException("DINGTALK_TARGET_INVALID");
        }
    }
}
