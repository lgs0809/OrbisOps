package cn.lgs.orbisops.application.channel;

import cn.lgs.orbisops.application.channel.provider.ChannelProtocolDescriptor;
import cn.lgs.orbisops.domain.channel.adapter.repository.IChannelRepository;
import cn.lgs.orbisops.domain.channel.model.ChannelAccessPolicy;
import cn.lgs.orbisops.domain.channel.model.ChannelIdentityRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelMessageRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;
import cn.lgs.orbisops.domain.channel.service.ChannelConfigurationPolicy;
import cn.lgs.orbisops.types.execution.ExecutionBinding;
import cn.lgs.orbisops.types.execution.ExecutionType;
import cn.lgs.orbisops.types.execution.ExecutionVersionPolicy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ChannelManagementApplicationService {

    private final IChannelRepository repository;
    private final ChannelCatalogQuery catalogQuery;
    private final ChannelProtocolCatalogPort protocolCatalog;
    private final ChannelExecutionBindingPort executionBindingPort;
    private final ChannelIdentityDirectoryPort identityDirectoryPort;
    private final ChannelAuditPort auditPort;
    private final ChannelRecoveryDispatchPort recoveryDispatchPort;
    private final ChannelConfigurationPolicy configurationPolicy;

    public ChannelManagementApplicationService(IChannelRepository repository,
                                               ChannelCatalogQuery catalogQuery,
                                               ChannelProtocolCatalogPort protocolCatalog,
                                               ChannelExecutionBindingPort executionBindingPort,
                                               ChannelIdentityDirectoryPort identityDirectoryPort,
                                               ChannelAuditPort auditPort,
                                               ChannelRecoveryDispatchPort recoveryDispatchPort) {
        this(repository, catalogQuery, protocolCatalog, executionBindingPort, identityDirectoryPort,
                auditPort, recoveryDispatchPort, new ChannelConfigurationPolicy());
    }

    ChannelManagementApplicationService(IChannelRepository repository,
                                        ChannelCatalogQuery catalogQuery,
                                        ChannelProtocolCatalogPort protocolCatalog,
                                        ChannelExecutionBindingPort executionBindingPort,
                                        ChannelIdentityDirectoryPort identityDirectoryPort,
                                        ChannelAuditPort auditPort,
                                        ChannelRecoveryDispatchPort recoveryDispatchPort,
                                        ChannelConfigurationPolicy configurationPolicy) {
        if (repository == null) throw new IllegalArgumentException("CHANNEL_REPOSITORY_REQUIRED");
        if (catalogQuery == null) throw new IllegalArgumentException("CHANNEL_CATALOG_QUERY_REQUIRED");
        if (protocolCatalog == null) throw new IllegalArgumentException("CHANNEL_PROTOCOL_CATALOG_REQUIRED");
        if (executionBindingPort == null) throw new IllegalArgumentException("CHANNEL_AGENT_BINDING_PORT_REQUIRED");
        if (identityDirectoryPort == null) throw new IllegalArgumentException("CHANNEL_IDENTITY_DIRECTORY_PORT_REQUIRED");
        if (auditPort == null) throw new IllegalArgumentException("CHANNEL_AUDIT_PORT_REQUIRED");
        if (recoveryDispatchPort == null) throw new IllegalArgumentException("CHANNEL_RECOVERY_DISPATCH_PORT_REQUIRED");
        if (configurationPolicy == null) throw new IllegalArgumentException("CHANNEL_CONFIGURATION_POLICY_REQUIRED");
        this.repository = repository;
        this.catalogQuery = catalogQuery;
        this.protocolCatalog = protocolCatalog;
        this.executionBindingPort = executionBindingPort;
        this.identityDirectoryPort = identityDirectoryPort;
        this.auditPort = auditPort;
        this.recoveryDispatchPort = recoveryDispatchPort;
        this.configurationPolicy = configurationPolicy;
    }

    public Map<String, Object> create(ChannelModels.ConfigurationMutation command) {
        requireConfigurationMutation(command);
        String projectId = required(firstNonBlank(command.projectId(), command.requestedProjectId().value()),
                "CHANNEL_PROJECT_ID_REQUIRED");
        String channelId = firstNonBlank(command.channelId(), command.requestedChannelId().value(),
                "channel-" + UUID.randomUUID());
        ExecutionBinding requestedExecution = requestedExecution(command, ExecutionBinding.none());
        ChannelRecord candidate = configurationPolicy.create(new ChannelRecord(
                channelId,
                projectId,
                resolveWorkflowIfNeeded(projectId, requestedExecution),
                required(command.name().value(), "CHANNEL_NAME_REQUIRED"),
                required(command.channelType().value(), "CHANNEL_TYPE_REQUIRED"),
                command.credentialRef().orElse(""),
                command.configuration().orElse(Map.of()),
                command.accessPolicy().orElse(ChannelAccessPolicy.DENY_UNKNOWN),
                command.status().orElse(ChannelStatus.ACTIVE),
                command.actor(),
                null,
                null));
        protocolCatalog.validateConfiguration(candidate);
        repository.create(candidate);
        Map<String, Object> result = catalogQuery.get(projectId, channelId);
        auditPort.record(projectId, "channel", "create", channelId, null, result);
        return result;
    }

    public List<Map<String, Object>> list(String projectId) {
        return catalogQuery.list(required(projectId, "CHANNEL_PROJECT_ID_REQUIRED"));
    }

    public List<ChannelProtocolDescriptor> supportedTypes() {
        return catalogQuery.supportedTypes();
    }

    public Map<String, Object> update(ChannelModels.ConfigurationMutation command) {
        requireConfigurationMutation(command);
        String projectId = required(command.projectId(), "CHANNEL_PROJECT_ID_REQUIRED");
        String channelId = required(command.channelId(), "CHANNEL_ID_REQUIRED");
        ChannelRecord current = channel(projectId, channelId);
        Map<String, Object> before = catalogQuery.get(projectId, channelId);
        ExecutionBinding requestedExecution = requestedExecution(command, current.inboundExecution());
        ChannelRecord validated = configurationPolicy.update(current, new ChannelRecord(
                command.requestedChannelId().orElse(current.channelId()),
                command.requestedProjectId().orElse(current.projectId()),
                requestedExecution,
                command.name().orElse(current.name()),
                command.channelType().orElse(current.channelType()),
                command.credentialRef().orElse(current.credentialRef()),
                command.configuration().orElse(current.config()),
                command.accessPolicy().orElse(current.accessPolicy()),
                command.status().orElse(current.status()),
                current.createdBy(),
                current.createTime(),
                current.updateTime()));
        ChannelRecord candidate = withInboundExecution(validated,
                resolveWorkflowIfNeeded(validated.projectId(), validated.inboundExecution()));
        protocolCatalog.validateConfiguration(candidate);
        if (!repository.update(candidate)) throw new IllegalStateException("CHANNEL_UPDATE_CONFLICT");
        Map<String, Object> result = catalogQuery.get(projectId, channelId);
        auditPort.record(projectId, "channel", "update", channelId, before, result);
        return result;
    }

    public List<Map<String, Object>> messages(String projectId, String channelId, int limit) {
        return catalogQuery.messages(required(projectId, "CHANNEL_PROJECT_ID_REQUIRED"),
                required(channelId, "CHANNEL_ID_REQUIRED"), limit(limit));
    }

    public Map<String, Object> requeue(ChannelModels.Recovery command) {
        requireRecovery(command);
        channel(command.projectId(), command.channelId());
        if (!command.confirmedNoSideEffect()) {
            throw new IllegalArgumentException("CHANNEL_RECOVERY_CONFIRMATION_REQUIRED");
        }
        Map<String, Object> before = recoveryMessage(command);
        if (!repository.requeueRecoveryRequired(command.projectId(), command.channelId(), command.externalMessageId())) {
            throw new IllegalStateException("CHANNEL_RECOVERY_STATE_CONFLICT");
        }
        auditPort.record(command.projectId(), "channel-inbound", "requeue-recovery", command.externalMessageId(),
                before, Map.of("status", "QUEUED", "actor", command.actor(), "confirmedNoSideEffect", true));
        try {
            recoveryDispatchPort.dispatch(command.channelId(), command.externalMessageId());
        } catch (RuntimeException ignored) {
            // The durable QUEUED state is picked up by the scheduled inbound worker.
        }
        return recoveryMessage(command);
    }

    public Map<String, Object> cancelRecovery(ChannelModels.Recovery command) {
        requireRecovery(command);
        channel(command.projectId(), command.channelId());
        Map<String, Object> before = recoveryMessage(command);
        if (!repository.cancelRecoveryRequired(command.projectId(), command.channelId(), command.externalMessageId())) {
            throw new IllegalStateException("CHANNEL_RECOVERY_STATE_CONFLICT");
        }
        auditPort.record(command.projectId(), "channel-inbound", "cancel-recovery", command.externalMessageId(),
                before, Map.of("status", "CANCELLED", "actor", command.actor()));
        return recoveryMessage(command);
    }

    public Map<String, Object> status(String projectId) {
        return catalogQuery.status(required(projectId, "CHANNEL_PROJECT_ID_REQUIRED"));
    }

    public Map<String, Object> statusAll() {
        return catalogQuery.statusAll();
    }

    public List<Map<String, Object>> identities(String projectId, String channelId) {
        return catalogQuery.identities(required(projectId, "CHANNEL_PROJECT_ID_REQUIRED"),
                required(channelId, "CHANNEL_ID_REQUIRED"));
    }

    public Map<String, Object> bindIdentity(ChannelModels.IdentityBinding command) {
        if (command == null) throw new IllegalArgumentException("CHANNEL_IDENTITY_BINDING_REQUIRED");
        channel(command.projectId(), command.channelId());
        Optional<ChannelIdentityRecord> existing = repository.findIdentity(
                command.channelId(), command.externalSenderId());
        ChannelIdentityDirectoryPort.PlatformIdentity platformIdentity = disabledExisting(command.status(), existing)
                ? new ChannelIdentityDirectoryPort.PlatformIdentity(
                existing.orElseThrow().platformUserId(), existing.orElseThrow().username())
                : identityDirectoryPort.resolveActive(command.platformUserId(), command.username());
        if (command.status() == ChannelStatus.ACTIVE
                && !identityDirectoryPort.canAccessProject(command.projectId(), platformIdentity)) {
            throw new SecurityException("CHANNEL_IDENTITY_USER_NOT_PROJECT_MEMBER");
        }
        ChannelIdentityRecord identity;
        if (existing.isEmpty()) {
            if (command.expectedVersion() > 0) throw new IllegalArgumentException("CHANNEL_IDENTITY_NOT_FOUND");
            identity = new ChannelIdentityRecord("channel-identity-" + UUID.randomUUID(),
                    command.channelId(), command.projectId(), command.externalSenderId(),
                    platformIdentity.userId(), platformIdentity.username(), command.status(), 1L,
                    command.actor(), null, null);
            repository.createIdentity(identity);
        } else {
            if (command.expectedVersion() <= 0) {
                throw new IllegalArgumentException("CHANNEL_IDENTITY_EXPECTED_VERSION_REQUIRED");
            }
            ChannelIdentityRecord before = existing.get();
            identity = new ChannelIdentityRecord(before.mappingId(), command.channelId(), command.projectId(),
                    command.externalSenderId(), platformIdentity.userId(), platformIdentity.username(), command.status(),
                    command.expectedVersion() + 1, before.createdBy(), before.createTime(), null);
            if (!repository.updateIdentity(identity, command.expectedVersion())) {
                throw new IllegalStateException("CHANNEL_IDENTITY_VERSION_CONFLICT");
            }
        }
        Map<String, Object> result = identityView(identity);
        auditPort.record(command.projectId(), "channel-identity", existing.isPresent() ? "update" : "create",
                identity.mappingId(), existing.map(this::identityView).orElse(null), result);
        return result;
    }

    public Map<String, Object> get(String projectId, String channelId) {
        return catalogQuery.get(required(projectId, "CHANNEL_PROJECT_ID_REQUIRED"),
                required(channelId, "CHANNEL_ID_REQUIRED"));
    }

    private ChannelRecord channel(String projectId, String channelId) {
        ChannelRecord row = repository.findById(required(channelId, "CHANNEL_ID_REQUIRED"))
                .orElseThrow(() -> new IllegalArgumentException("渠道不存在"));
        if (!required(projectId, "CHANNEL_PROJECT_ID_REQUIRED").equals(row.projectId())) {
            throw new SecurityException("CHANNEL_PROJECT_MISMATCH");
        }
        return row;
    }

    private ChannelRecord withInboundExecution(ChannelRecord channel, ExecutionBinding execution) {
        return new ChannelRecord(channel.channelId(), channel.projectId(), execution, channel.name(),
                channel.channelType(), channel.credentialRef(), channel.config(), channel.accessPolicy(), channel.status(),
                channel.createdBy(), channel.createTime(), channel.updateTime());
    }

    private ExecutionBinding requestedExecution(ChannelModels.ConfigurationMutation command,
                                                ExecutionBinding current) {
        ExecutionBinding base = current == null ? ExecutionBinding.none() : current;
        ExecutionType type = command.executionType().orElse(base.type());
        if (type == ExecutionType.NONE) return ExecutionBinding.none();
        if (type == ExecutionType.REACT) return ExecutionBinding.react();
        return ExecutionBinding.workflow(
                command.workflowId().orElse(base.workflowId()),
                command.workflowVersionPolicy().orElse(base.versionPolicy()),
                command.workflowVersion().orElse(base.version()),
                base.definitionHash());
    }

    private ExecutionBinding resolveWorkflowIfNeeded(String projectId, ExecutionBinding requested) {
        if (requested.type() != ExecutionType.WORKFLOW) return requested;
        ChannelExecutionBindingPort.ResolvedExecution resolved = executionBindingPort.resolve(projectId, requested);
        if (resolved.type() != ExecutionType.WORKFLOW) {
            throw new IllegalStateException("CHANNEL_WORKFLOW_RESOLUTION_INVALID");
        }
        return ExecutionBinding.workflow(
                requested.workflowId(), requested.versionPolicy(), resolved.version(), resolved.definitionHash());
    }

    private Map<String, Object> recoveryMessage(ChannelModels.Recovery command) {
        ChannelMessageRecord row = repository.findInbound(command.channelId(), command.externalMessageId())
                .filter(item -> command.projectId().equals(item.projectId()))
                .orElseThrow(() -> new IllegalArgumentException("CHANNEL_INBOUND_MESSAGE_NOT_FOUND"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("messageId", row.messageId());
        result.put("channelId", row.channelId());
        result.put("projectId", row.projectId());
        result.put("externalMessageId", row.externalMessageId());
        result.put("status", row.status());
        result.put("runId", text(row.runId()));
        result.put("sessionId", text(row.sessionId()));
        result.put("errorMessage", text(row.errorMessage()));
        return Map.copyOf(result);
    }

    private Map<String, Object> identityView(ChannelIdentityRecord row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mappingId", row.mappingId());
        result.put("channelId", row.channelId());
        result.put("projectId", row.projectId());
        result.put("externalSenderId", row.externalSenderId());
        result.put("platformUserId", row.platformUserId());
        result.put("username", row.username());
        result.put("status", row.status().name());
        result.put("version", row.version());
        result.put("createdBy", row.createdBy());
        return Map.copyOf(result);
    }

    private boolean disabledExisting(ChannelStatus status, Optional<ChannelIdentityRecord> existing) {
        return status == ChannelStatus.DISABLED && existing.isPresent();
    }

    private void requireRecovery(ChannelModels.Recovery command) {
        if (command == null) throw new IllegalArgumentException("CHANNEL_RECOVERY_REQUIRED");
    }

    private void requireConfigurationMutation(ChannelModels.ConfigurationMutation command) {
        if (command == null) throw new IllegalArgumentException("CHANNEL_MUTATION_REQUIRED");
    }

    private int limit(int value) {
        if (value <= 0 || value > 1000) throw new IllegalArgumentException("CHANNEL_QUERY_LIMIT_INVALID");
        return value;
    }

    private String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = text(value);
            if (!normalized.isBlank()) return normalized;
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
