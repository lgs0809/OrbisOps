package cn.lgs.orbisops.application.channel;

import cn.lgs.orbisops.domain.channel.adapter.repository.IChannelRepository;
import cn.lgs.orbisops.domain.channel.model.ChannelIdentityRecord;
import cn.lgs.orbisops.types.execution.ExecutionType;
import cn.lgs.orbisops.types.execution.ExecutionVersionPolicy;
import cn.lgs.orbisops.domain.channel.model.ChannelMessageRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;
import cn.lgs.orbisops.domain.channel.service.ChannelOutboundContentPolicy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ChannelInboundProcessManager {

    private final IChannelRepository repository;
    private final ChannelInboundProtocolPort protocol;
    private final ChannelConversationLeaseService leases;
    private final ChannelExecutionBindingPort executionBindings;
    private final ChannelIdentityDirectoryPort identities;
    private final ChannelAgentChatPort chat;
    private final ChannelReplyOutboxPort replies;
    private final ChannelRuntimeAuditPort audit;
    private final ChannelTaskExecutorPort executor;
    private final ChannelOutboundContentPolicy contentPolicy;
    private final boolean workerEnabled;
    private final int workerBatchSize;
    private final Duration leaseDuration;

    public ChannelInboundProcessManager(IChannelRepository repository,
                                        ChannelInboundProtocolPort protocol,
                                        ChannelConversationLeaseService leases,
                                        ChannelExecutionBindingPort executionBindings,
                                        ChannelIdentityDirectoryPort identities,
                                        ChannelAgentChatPort chat,
                                        ChannelReplyOutboxPort replies,
                                        ChannelRuntimeAuditPort audit,
                                        ChannelTaskExecutorPort executor,
                                        ChannelOutboundContentPolicy contentPolicy,
                                        boolean workerEnabled,
                                        int workerBatchSize,
                                        int leaseSeconds) {
        if (repository == null) throw new IllegalArgumentException("CHANNEL_REPOSITORY_REQUIRED");
        if (protocol == null) throw new IllegalArgumentException("CHANNEL_INBOUND_PROTOCOL_PORT_REQUIRED");
        if (leases == null) throw new IllegalArgumentException("CHANNEL_LEASE_SERVICE_REQUIRED");
        if (executionBindings == null) throw new IllegalArgumentException("CHANNEL_EXECUTION_BINDING_PORT_REQUIRED");
        if (identities == null) throw new IllegalArgumentException("CHANNEL_IDENTITY_DIRECTORY_PORT_REQUIRED");
        if (chat == null) throw new IllegalArgumentException("CHANNEL_AGENT_CHAT_PORT_REQUIRED");
        if (replies == null) throw new IllegalArgumentException("CHANNEL_REPLY_OUTBOX_PORT_REQUIRED");
        if (audit == null) throw new IllegalArgumentException("CHANNEL_RUNTIME_AUDIT_PORT_REQUIRED");
        if (executor == null) throw new IllegalArgumentException("CHANNEL_TASK_EXECUTOR_PORT_REQUIRED");
        if (contentPolicy == null) throw new IllegalArgumentException("CHANNEL_CONTENT_POLICY_REQUIRED");
        this.repository = repository;
        this.protocol = protocol;
        this.leases = leases;
        this.executionBindings = executionBindings;
        this.identities = identities;
        this.chat = chat;
        this.replies = replies;
        this.audit = audit;
        this.executor = executor;
        this.contentPolicy = contentPolicy;
        this.workerEnabled = workerEnabled;
        this.workerBatchSize = Math.max(1, Math.min(workerBatchSize, 100));
        this.leaseDuration = Duration.ofSeconds(Math.max(30, leaseSeconds));
    }

    public Map<String, Object> receive(ChannelModels.Receive command) {
        if (command == null) throw new IllegalArgumentException("CHANNEL_RECEIVE_COMMAND_REQUIRED");
        ChannelRecord channel = channel(command.channelId());
        if (channel.status() != ChannelStatus.ACTIVE) throw new SecurityException("CHANNEL_DISABLED");
        if (!channel.acceptsInbound()) throw new SecurityException("CHANNEL_INBOUND_DISABLED");
        boolean replyConfigured = ChannelDeliveryConfiguration.from(channel.config()).replyConfigured();
        ChannelInboundProtocolPort.VerifiedInbound verified = protocol.verifyAndProtect(
                channel, command.message(), command.timestampHeader(), command.signature());
        boolean inserted = repository.insertInbound(new ChannelMessageRecord(
                "channel-message-" + UUID.randomUUID(), channel.channelId(), channel.projectId(),
                command.message().externalMessageId(), command.message().externalConversationId(),
                command.message().senderId(), "", "", "INBOUND", "RECEIVED",
                verified.storagePayloadJson(), null, null, null));
        if (!inserted) {
            return Map.of("status", "DUPLICATE_IGNORED", "channelId", channel.channelId(),
                    "externalMessageId", command.message().externalMessageId());
        }
        String sessionId = repository.resolveSession(channel.channelId(), channel.projectId(), executionKey(channel),
                command.message().externalConversationId(), command.message().senderId(),
                "channel-session-" + UUID.randomUUID());
        String runId = "channel-" + channel.channelId() + "-" + UUID.randomUUID().toString().substring(0, 12);
        repository.markInboundQueued(channel.channelId(), command.message().externalMessageId(), runId, sessionId);
        try {
            audit.record(channel.projectId(), executionKey(channel),
                    "channel:" + channel.channelId() + ":" + command.message().senderId(),
                    "CHANNEL_MESSAGE_ACCEPTED", command.message().externalMessageId(), "LOW", "QUEUED",
                    Map.of("channelId", channel.channelId(), "runId", runId, "sessionId", sessionId));
        } catch (RuntimeException auditFailure) {
            failAccepted(channel, command.message().externalMessageId(), "CHANNEL_ACCEPTANCE_AUDIT_FAILED", auditFailure);
        }
        try {
            executor.execute(() -> processQueued(channel.channelId(), command.message().externalMessageId()));
        } catch (RuntimeException rejected) {
            try {
                failUnclaimed(channel, command.message().externalMessageId(),
                        "CHANNEL_EXECUTOR_REJECTED:" + error(rejected));
            } catch (RuntimeException stateFailure) {
                rejected.addSuppressed(stateFailure);
            }
            throw rejected;
        }
        return Map.of("status", "ACCEPTED", "runId", runId, "sessionId", sessionId,
                "replyDeliveryConfigured", replyConfigured);
    }

    public void processPending() {
        for (ChannelConversationLeaseService.LeaseLost lost : leases.renewActive(leaseDuration)) {
            audit.record(lost.projectId(), "", "channel-runtime", "CHANNEL_INBOUND_LEASE_LOST",
                    lost.externalMessageId(), "MEDIUM", "BLOCKED",
                    Map.of("channelId", lost.channelId(), "reasonCode", "CHANNEL_INBOUND_LEASE_LOST"));
        }
        if (!workerEnabled) return;
        for (ChannelConversationLeasePort.RecoveredLease expired : leases.recoverExpired(workerBatchSize)) {
            auditRecoveryRequired(expired);
        }
        for (ChannelMessageRecord queued : repository.findQueuedInbound(workerBatchSize)) {
            try {
                executor.execute(() -> processQueued(queued.channelId(), queued.externalMessageId()));
            } catch (RuntimeException ignored) {
                // The durable QUEUED row remains claimable by another worker.
            }
        }
    }

    public void processQueued(String channelId, String externalMessageId) {
        ChannelMessageRecord stored = repository.findInbound(channelId, externalMessageId).orElse(null);
        if (stored == null || !"QUEUED".equals(stored.status())) return;
        Optional<ChannelConversationLeaseService.LeaseHandle> acquired = leases.acquire(
                new ChannelConversationLeaseService.AcquireRequest(stored.projectId(), channelId,
                        stored.externalConversationId(), stored.senderId(), externalMessageId), leaseDuration);
        if (acquired.isEmpty()) return;
        ChannelConversationLeaseService.LeaseHandle lease = acquired.get();
        try {
            ChannelModels.InboundMessage message;
            try {
                message = protocol.restore(stored);
                audit.record(stored.projectId(), "", "channel-runtime", "CHANNEL_INBOUND_PROCESSING_STARTED",
                        externalMessageId, "LOW", "RUNNING",
                        Map.of("channelId", channelId, "runId", text(stored.runId()),
                                "sessionId", text(stored.sessionId())));
            } catch (RuntimeException failure) {
                failClaimed(stored, lease, error(failure));
                return;
            }
            processClaimed(channel(channelId), stored, message, lease);
        } finally {
            leases.release(lease);
        }
    }

    private void processClaimed(ChannelRecord channel,
                                ChannelMessageRecord stored,
                                ChannelModels.InboundMessage message,
                                ChannelConversationLeaseService.LeaseHandle lease) {
        ChannelAgentChatPort.ChatResult response;
        ChannelReplyOutboxPort.ReplyDelivery delivery;
        try {
            ChannelExecutionBindingPort.ResolvedExecution execution = executionBindings.resolve(
                    channel.projectId(), channel.inboundExecution());
            if (channel.inboundExecution().type() == ExecutionType.WORKFLOW
                    && channel.inboundExecution().versionPolicy() == ExecutionVersionPolicy.PINNED_VERSION
                    && !text(channel.inboundExecution().definitionHash()).equals(execution.definitionHash())) {
                throw new SecurityException("CHANNEL_WORKFLOW_DEFINITION_HASH_MISMATCH");
            }
            Optional<ChannelIdentityRecord> storedIdentity = repository.findIdentity(channel.channelId(), message.senderId())
                    .filter(item -> item.status() == ChannelStatus.ACTIVE);
            Optional<ChannelIdentityRecord> trustedIdentity = storedIdentity
                    .filter(item -> identities.isTrusted(channel.projectId(), item));
            ChannelAgentChatPort.IdentityStatus identityStatus = trustedIdentity.isPresent()
                    ? ChannelAgentChatPort.IdentityStatus.MAPPED
                    : storedIdentity.isPresent()
                    ? ChannelAgentChatPort.IdentityStatus.INVALID
                    : ChannelAgentChatPort.IdentityStatus.UNMAPPED;
            if (identityStatus == ChannelAgentChatPort.IdentityStatus.INVALID) {
                audit.record(channel.projectId(), executionKey(channel),
                        "channel:" + channel.channelId() + ":" + message.senderId(),
                        "CHANNEL_IDENTITY_REJECTED", storedIdentity.orElseThrow().mappingId(), "MEDIUM", "BLOCKED",
                        Map.of("channelId", channel.channelId(), "runId", text(stored.runId()),
                                "sessionId", text(stored.sessionId()),
                                "reasonCode", "CHANNEL_IDENTITY_NO_LONGER_AUTHORIZED"));
            }
            response = chat.chat(new ChannelAgentChatPort.ChatCommand(
                    channel.projectId(), channel.channelId(), channel.channelType(), text(stored.runId()),
                    text(stored.sessionId()), execution.type(), execution.definitionId(), execution.version(),
                    execution.definitionHash(), message, trustedIdentity.orElse(null), identityStatus));
            delivery = replies.enqueue(channel.projectId(), channel.channelId(), message.externalConversationId(),
                    response.content(), text(stored.runId()), text(stored.sessionId()), message.externalMessageId());
            if (delivery == null) throw new IllegalStateException("CHANNEL_REPLY_DELIVERY_RESULT_REQUIRED");
        } catch (RuntimeException failure) {
            failClaimed(stored, lease, error(failure));
            return;
        }
        String status = delivery.delivered() ? "COMPLETED"
                : delivery.terminalFailure() ? "REPLY_FAILED" : "REPLY_QUEUED";
        final boolean completed;
        try {
            completed = leases.complete(lease, status, stored.runId(), stored.sessionId());
        } catch (RuntimeException completionFailure) {
            throw new IllegalStateException("CHANNEL_COMPLETION_STATE_UNKNOWN_AFTER_REPLY", completionFailure);
        }
        if (!completed) {
            auditRecoveryRequired(stored);
            return;
        }
        try {
            audit.record(channel.projectId(), executionKey(channel), response.userId(),
                    "CHANNEL_MESSAGE_COMPLETED", message.externalMessageId(), "LOW", "SUCCEEDED",
                    Map.of("channelId", channel.channelId(), "runId", text(stored.runId()),
                            "sessionId", text(stored.sessionId())));
        } catch (RuntimeException auditFailure) {
            throw new IllegalStateException("CHANNEL_COMPLETION_AUDIT_FAILED_AFTER_COMMIT", auditFailure);
        }
    }

    private void failAccepted(ChannelRecord channel,
                              String externalMessageId,
                              String reasonCode,
                              RuntimeException cause) {
        try {
            repository.markInboundFailed(channel.channelId(), externalMessageId, reasonCode);
        } catch (RuntimeException stateFailure) {
            cause.addSuppressed(stateFailure);
        }
        throw new IllegalStateException(reasonCode, cause);
    }

    private void failUnclaimed(ChannelRecord channel, String externalMessageId, String error) {
        String safeError = safeError(error);
        repository.markInboundFailed(channel.channelId(), externalMessageId, safeError);
        audit.record(channel.projectId(), executionKey(channel), "channel-runtime", "CHANNEL_MESSAGE_FAILED",
                externalMessageId, "LOW", "FAILED",
                Map.of("channelId", channel.channelId(), "error", safeError));
    }

    private void failClaimed(ChannelMessageRecord stored,
                             ChannelConversationLeaseService.LeaseHandle lease,
                             String error) {
        String safeError = safeError(error);
        if (!leases.fail(lease, safeError)) {
            auditRecoveryRequired(stored);
            return;
        }
        audit.record(stored.projectId(), "", "channel-runtime", "CHANNEL_MESSAGE_FAILED",
                stored.externalMessageId(), "LOW", "FAILED",
                Map.of("channelId", stored.channelId(), "runId", text(stored.runId()),
                        "sessionId", text(stored.sessionId()), "error", safeError));
    }

    private void auditRecoveryRequired(ChannelMessageRecord stored) {
        auditRecoveryRequired(new ChannelConversationLeasePort.RecoveredLease(stored.projectId(), stored.channelId(),
                stored.externalConversationId(), stored.senderId(), stored.externalMessageId(),
                stored.runId(), stored.sessionId()));
    }

    private void auditRecoveryRequired(ChannelConversationLeasePort.RecoveredLease stored) {
        audit.record(stored.projectId(), "", "channel-runtime", "CHANNEL_INBOUND_RECOVERY_REQUIRED",
                stored.externalMessageId(), "MEDIUM", "BLOCKED",
                Map.of("channelId", stored.channelId(), "runId", text(stored.runId()),
                        "sessionId", text(stored.sessionId()),
                        "reasonCode", "CHANNEL_INBOUND_OUTCOME_UNKNOWN"));
    }

    private ChannelRecord channel(String channelId) {
        return repository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("CHANNEL_NOT_FOUND"));
    }

    private String executionKey(ChannelRecord channel) {
        if (channel == null || channel.inboundExecution() == null) return "NONE";
        return channel.inboundExecution().type() == ExecutionType.WORKFLOW
                ? channel.inboundExecution().workflowId()
                : channel.inboundExecution().type().name();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String error(RuntimeException failure) {
        String message = failure == null ? "" : text(failure.getMessage());
        return message.isBlank() ? "CHANNEL_INBOUND_PROCESSING_FAILED" : safeError(message);
    }

    private String safeError(Object value) {
        String masked = contentPolicy.sanitize(text(value));
        return masked.isBlank() ? "CHANNEL_INBOUND_PROCESSING_FAILED" : masked;
    }
}
