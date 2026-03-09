package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.ChannelDeliveryResult;
import cn.lgs.orbisops.application.channel.ChannelOutboundDeliveryPort;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.trigger.ops.channel.OpsChannelAdapter;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public final class OpsChannelOutboundDeliveryAdapter implements ChannelOutboundDeliveryPort {

    private final Map<String, OpsChannelAdapter> adapters;

    public OpsChannelOutboundDeliveryAdapter(List<OpsChannelAdapter> adapters) {
        Map<String, OpsChannelAdapter> indexed = new LinkedHashMap<>();
        if (adapters != null) {
            adapters.forEach(adapter -> indexed.put(adapter.type().toUpperCase(Locale.ROOT), adapter));
        }
        this.adapters = Map.copyOf(indexed);
    }

    @Override
    public ChannelDeliveryResult send(ChannelRecord channel,
                                      Map<String, Object> configuration,
                                      String target,
                                      String content,
                                      Map<String, Object> metadata) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("channelId", channel.channelId());
        view.put("projectId", channel.projectId());
        view.put("type", channel.channelType());
        view.put("channelType", channel.channelType());
        view.put("credentialRef", channel.credentialRef());
        view.put("config", configuration == null ? Map.of() : Map.copyOf(configuration));
        view.put("status", channel.status().name());
        OpsChannelAdapter adapter = adapters.get(channel.channelType().toUpperCase(Locale.ROOT));
        if (adapter == null) throw new IllegalStateException("CHANNEL_DELIVERY_ADAPTER_NOT_CONFIGURED");
        Map<String, Object> payload = adapter.send(Map.copyOf(view), target, content,
                metadata == null ? Map.of() : Map.copyOf(metadata));
        Map<String, Object> safe = payload == null ? Map.of() : payload;
        return new ChannelDeliveryResult(Boolean.TRUE.equals(safe.get("delivered")), safe);
    }
}
