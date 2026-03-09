package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.ChannelProtocolCatalogPort;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.trigger.ops.channel.OpsChannelAdapter;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public final class OpsChannelProtocolCatalogAdapter implements ChannelProtocolCatalogPort {

    private final Map<String, OpsChannelAdapter> adapters;

    public OpsChannelProtocolCatalogAdapter(List<OpsChannelAdapter> adapters) {
        Map<String, OpsChannelAdapter> byType = new LinkedHashMap<>();
        if (adapters != null) {
            adapters.forEach(adapter -> byType.put(adapter.type().toUpperCase(Locale.ROOT), adapter));
        }
        this.adapters = Map.copyOf(byType);
    }

    @Override
    public List<Map<String, Object>> supportedTypes() {
        return adapters.keySet().stream().sorted().map(type -> Map.<String, Object>of(
                "type", type,
                "displayName", "GENERIC_WEBHOOK".equals(type) ? "通用 Webhook" : type,
                "supportsInbound", true,
                "supportsOutbound", true)).toList();
    }

    @Override
    public void validateConfiguration(ChannelRecord channel) {
        if (channel == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_REQUIRED");
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("channelId", channel.channelId());
        view.put("projectId", channel.projectId());
        view.put("executionType", channel.inboundExecution().type().name());
        view.put("workflowId", channel.inboundExecution().workflowId());
        view.put("workflowVersionPolicy", channel.inboundExecution().versionPolicy().name());
        view.put("workflowVersion", channel.inboundExecution().version());
        view.put("workflowDefinitionHash", channel.inboundExecution().definitionHash());
        view.put("name", channel.name());
        view.put("type", channel.channelType());
        view.put("channelType", channel.channelType());
        view.put("credentialRef", channel.credentialRef());
        view.put("config", channel.config());
        view.put("status", channel.status().name());
        adapter(channel.channelType()).validateConfiguration(Map.copyOf(view));
    }

    private OpsChannelAdapter adapter(String channelType) {
        String normalized = channelType == null ? "" : channelType.trim().toUpperCase(Locale.ROOT);
        OpsChannelAdapter adapter = adapters.get(normalized);
        if (adapter == null) throw new IllegalArgumentException("渠道类型尚未接入：" + channelType);
        return adapter;
    }
}
