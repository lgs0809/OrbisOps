package cn.lgs.orbisops.application.channel;

import cn.lgs.orbisops.application.channel.provider.ChannelProtocolDescriptor;
import cn.lgs.orbisops.domain.channel.adapter.repository.IChannelRepository;
import cn.lgs.orbisops.domain.channel.model.ChannelIdentityRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelMessageRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelStoreStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChannelQueryService implements ChannelCatalogQuery {

    private final IChannelRepository repository;
    private final ChannelProtocolCatalogPort protocolCatalog;

    public ChannelQueryService(IChannelRepository repository,
                               ChannelProtocolCatalogPort protocolCatalog) {
        if (repository == null) throw new IllegalArgumentException("CHANNEL_REPOSITORY_REQUIRED");
        if (protocolCatalog == null) throw new IllegalArgumentException("CHANNEL_PROTOCOL_CATALOG_REQUIRED");
        this.repository = repository;
        this.protocolCatalog = protocolCatalog;
    }

    @Override
    public List<Map<String, Object>> list(String projectId) {
        return repository.findByProject(required(projectId, "CHANNEL_PROJECT_ID_REQUIRED"))
                .stream().map(this::channelView).toList();
    }

    @Override
    public List<ChannelProtocolDescriptor> supportedTypes() {
        return protocolCatalog.supportedTypes();
    }

    @Override
    public List<Map<String, Object>> messages(String projectId, String channelId, int limit) {
        String safeProjectId = required(projectId, "CHANNEL_PROJECT_ID_REQUIRED");
        String safeChannelId = required(channelId, "CHANNEL_ID_REQUIRED");
        get(safeProjectId, safeChannelId);
        if (limit <= 0 || limit > 1000) throw new IllegalArgumentException("CHANNEL_QUERY_LIMIT_INVALID");
        return repository.findMessages(safeProjectId, safeChannelId, limit).stream().map(this::messageView).toList();
    }

    @Override
    public Map<String, Object> status(String projectId) {
        String safeProjectId = required(projectId, "CHANNEL_PROJECT_ID_REQUIRED");
        ChannelStoreStatus status = repository.status(safeProjectId);
        return Map.of("projectId", safeProjectId, "activeChannels", status.activeChannels(),
                "failedMessages", status.failedMessages(), "ready", status.ready());
    }

    @Override
    public Map<String, Object> statusAll() {
        ChannelStoreStatus status = repository.statusAll();
        return Map.of("activeChannels", status.activeChannels(), "failedMessages", status.failedMessages(),
                "ready", status.ready());
    }

    @Override
    public List<Map<String, Object>> identities(String projectId, String channelId) {
        String safeProjectId = required(projectId, "CHANNEL_PROJECT_ID_REQUIRED");
        String safeChannelId = required(channelId, "CHANNEL_ID_REQUIRED");
        get(safeProjectId, safeChannelId);
        return repository.findIdentities(safeProjectId, safeChannelId).stream().map(this::identityView).toList();
    }

    @Override
    public Map<String, Object> get(String projectId, String channelId) {
        String safeProjectId = required(projectId, "CHANNEL_PROJECT_ID_REQUIRED");
        ChannelRecord row = repository.findById(required(channelId, "CHANNEL_ID_REQUIRED"))
                .orElseThrow(() -> new IllegalArgumentException("渠道不存在"));
        if (!safeProjectId.equals(row.projectId())) throw new SecurityException("CHANNEL_PROJECT_MISMATCH");
        return channelView(row);
    }

    private Map<String, Object> channelView(ChannelRecord row) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("channelId", row.channelId());
        data.put("projectId", row.projectId());
        data.put("executionType", row.inboundExecution().type().name());
        data.put("workflowId", row.inboundExecution().workflowId());
        data.put("workflowVersionPolicy", row.inboundExecution().versionPolicy().name());
        data.put("workflowVersion", row.inboundExecution().version());
        data.put("workflowDefinitionHash", row.inboundExecution().definitionHash());
        // Compatibility aliases for older clients. New product surfaces use executionType/workflow*.
        data.put("agentId", row.inboundExecution().workflowId());
        data.put("agentBindingMode", row.inboundExecution().versionPolicy().name());
        data.put("agentVersion", row.inboundExecution().version());
        data.put("agentDefinitionHash", row.inboundExecution().definitionHash());
        data.put("name", row.name());
        data.put("channelType", row.channelType());
        data.put("type", row.channelType());
        data.put("credentialRef", text(row.credentialRef()));
        data.put("config", row.config());
        data.put("accessPolicy", row.accessPolicy().name());
        data.put("status", row.status().name());
        data.put("createdBy", row.createdBy());
        if (row.createTime() != null) data.put("createTime", row.createTime().toString());
        if (row.updateTime() != null) data.put("updateTime", row.updateTime().toString());
        return Collections.unmodifiableMap(data);
    }

    private Map<String, Object> messageView(ChannelMessageRecord row) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("messageId", row.messageId());
        data.put("channelId", row.channelId());
        data.put("projectId", row.projectId());
        data.put("externalMessageId", row.externalMessageId());
        data.put("externalConversationId", row.externalConversationId());
        data.put("senderId", row.senderId());
        data.put("sessionId", row.sessionId());
        data.put("runId", row.runId());
        data.put("direction", row.direction());
        data.put("status", row.status());
        data.put("errorMessage", row.errorMessage());
        if (row.createTime() != null) data.put("createTime", row.createTime().toString());
        if (row.updateTime() != null) data.put("updateTime", row.updateTime().toString());
        return Collections.unmodifiableMap(data);
    }

    private Map<String, Object> identityView(ChannelIdentityRecord row) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("mappingId", row.mappingId());
        data.put("channelId", row.channelId());
        data.put("projectId", row.projectId());
        data.put("externalSenderId", row.externalSenderId());
        data.put("platformUserId", row.platformUserId());
        data.put("username", row.username());
        data.put("status", row.status().name());
        data.put("version", row.version());
        data.put("createdBy", row.createdBy());
        if (row.createTime() != null) data.put("createTime", row.createTime().toString());
        if (row.updateTime() != null) data.put("updateTime", row.updateTime().toString());
        return Collections.unmodifiableMap(data);
    }

    private String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
