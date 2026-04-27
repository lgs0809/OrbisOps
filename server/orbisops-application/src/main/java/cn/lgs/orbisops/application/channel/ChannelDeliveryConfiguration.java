package cn.lgs.orbisops.application.channel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** ACL over channel-type-specific open configuration used by delivery adapters. */
public record ChannelDeliveryConfiguration(
        String outboundUrl,
        Map<String, Object> protocolConfig) {

    public ChannelDeliveryConfiguration {
        outboundUrl = text(outboundUrl);
        protocolConfig = immutable(protocolConfig);
    }

    public static ChannelDeliveryConfiguration from(Map<String, ?> source) {
        Map<String, Object> config = new LinkedHashMap<>();
        if (source != null) source.forEach((key, value) -> config.put(String.valueOf(key), value));
        return new ChannelDeliveryConfiguration(text(config.get("outboundUrl")), config);
    }

    public boolean replyConfigured() {
        return !outboundUrl.isBlank();
    }

    private static Map<String, Object> immutable(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return Map.of();
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
