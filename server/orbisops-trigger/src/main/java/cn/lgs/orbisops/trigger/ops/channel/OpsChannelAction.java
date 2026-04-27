package cn.lgs.orbisops.trigger.ops.channel;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record OpsChannelAction(String actionId,
                               String actionType,
                               String value,
                               Map<String, Object> parameters) {
    public OpsChannelAction {
        parameters = parameters == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    }
}
