package cn.lgs.orbisops.application.channel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ChannelDeliveryResult(boolean delivered, Map<String, Object> payload) {

    public ChannelDeliveryResult {
        Map<String, Object> normalized = payload == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(payload);
        normalized.put("delivered", delivered);
        payload = Collections.unmodifiableMap(normalized);
    }
}
