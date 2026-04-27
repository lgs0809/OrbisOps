package cn.lgs.orbisops.application.channel;

import cn.lgs.orbisops.application.channel.provider.ChannelInboundEnvelope;
import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveAction;
import cn.lgs.orbisops.domain.channel.adapter.repository.IChannelRepository;
import cn.lgs.orbisops.domain.channel.model.ChannelIdentityRecord;
import cn.lgs.orbisops.types.execution.ExecutionType;
import cn.lgs.orbisops.types.execution.ExecutionVersionPolicy;
import cn.lgs.orbisops.domain.channel.model.ChannelMessageRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;
import cn.lgs.orbisops.domain.channel.service.ChannelOutboundContentPolicy;

import java.time.Duration;
import java.util.LinkedHashMap;
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
        ChannelRecord channel = activeInboundChannel(command.channelId());
        ChannelInboundProtocolPort.VerifiedInbound verified = protocol.verifyAndProtect(
                channel, command.message(), command.timestampHeader(), command.signature());
        return acceptVerified(channel, command.message(), verified);
    }

    public Map<String, Object> receiveProvider(String channelId, ChannelInboundEnvelope envelope) {
        if (envelope == null) throw new IllegalArgumentException("CHANNEL_PROVIDER_ENVELOPE_REQUIRED");
        ChannelRecord channel = activeInboundChannel(channelId);
        ChannelModels.InboundMessage message = providerMessage(envelope);
        ChannelInboundProtocolPort.VerifiedInbound verified = protocol.protectTrusted(channel, message);
        return acceptVerified(channel, message, verified);
    }

    private Map<String, Object> acceptVerified(ChannelRecord channel,
                                                ChannelModels.InboundMessage message,
                                                ChannelInboundProtocolPort.VerifiedInbound verified) {
        boolean replyConfigured = ChannelDeliveryConfiguration.from(channel.config()).replyConfigured();
        boolean inserted = repository.insertInbound(new ChannelMessageRecord(
                "channel-message-" + UUID.randomUUID(), channel.channelId(), channel.projectId(),
                message.externalMessageId(), message.externalConversationId(),
                message.senderId(), "", "", "INBOUND", "RECEIVED",
                verified.storagePayloadJson(), null, null, null));
        if (!inserted) {
            return Map.of("status", "DUPLICATE_IGNORED", "channelId", channel.channelId(),
                    "externalMessageId", message.externalMessageId());
        }
        String sessionId = repository.resolveSession(channel.channelId(), channel.projectId(), executionKey(channel),
                sessionConversationKey(message), message.senderId(),
                "channel-session-" + UUID.randomUUID());
        String runId = "channel-" + channel.channelId() + "-" + UUID.randomUUID().toString().substring(0, 12);
        repository.markInboundQueued(channel.channelId(), message.externalMessageId(), runId, sessionId);
        try {
            audit.record(channel.projectId(), executionKey(channel),
                    "channel:" + channel.channelId() + ":" + message.senderId(),
                    "CHANNEL_MESSAGE_ACCEPTED", message.externalMessageId(), "LOW", "QUEUED",
                    Map.of("channelId", channel.channelId(), "runId", runId, "sessionId", sessionId));
        } catch (RuntimeException auditFailure) {
            failAccepted(channel, message.externalMessageId(), "CHANNEL_ACCEPTANCE_AUDIT_FAILED", auditFailure);
        }
        try {
            executor.execute(() -> processQueued(channel.channelId(), message.externalMessageId()));
        } catch (RuntimeException rejected) {
            try {
                failUnclaimed(channel, message.externalMessageId(),
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
                throw new SecurityException("CHANNEL_IDENTITY_NO_LONGER_AUTHORIZED");
            }
            if (identityStatus == ChannelAgentChatPort.IdentityStatus.UNMAPPED
                    && !channel.accessPolicy().allowsUnknownRuntime()) {
                if (channel.accessPolicy() == cn.lgs.orbisops.domain.channel.model.ChannelAccessPolicy.PAIRING) {
                    auditUnknownSender(channel, stored, message, "CHANNEL_IDENTITY_PAIRING_REQUIRED");
                    response = new ChannelAgentChatPort.ChatResult(
                            "身份尚未绑定，请联系项目管理员完成 Channel 身份绑定后再使用。",
                            "channel:" + channel.channelId() + ":" + message.senderId());
                    delivery = replies.enqueue(channel.projectId(), channel.channelId(), message.externalConversationId(),
                            response.content(), text(stored.runId()), text(stored.sessionId()), replyAnchor(message));
                    if (delivery == null) throw new IllegalStateException("CHANNEL_REPLY_DELIVERY_RESULT_REQUIRED");
                } else {
                    String reasonCode = channel.accessPolicy() == cn.lgs.orbisops.domain.channel.model.ChannelAccessPolicy.ALLOWLIST
                            ? "CHANNEL_SENDER_NOT_ALLOWLISTED"
                            : "CHANNEL_UNKNOWN_SENDER_DENIED";
                    auditUnknownSender(channel, stored, message, reasonCode);
                    throw new SecurityException(reasonCode);
                }
            } else {
                ChannelExecutionBindingPort.ResolvedExecution execution = executionBindings.resolve(
                        channel.projectId(), channel.inboundExecution());
                if (channel.inboundExecution().type() == ExecutionType.WORKFLOW
                        && channel.inboundExecution().versionPolicy() == ExecutionVersionPolicy.PINNED_VERSION
                        && !text(channel.inboundExecution().definitionHash()).equals(execution.definitionHash())) {
                    throw new SecurityException("CHANNEL_WORKFLOW_DEFINITION_HASH_MISMATCH");
                }
                ChannelAgentChatPort.RuntimeAccess runtimeAccess = identityStatus == ChannelAgentChatPort.IdentityStatus.MAPPED
                        ? ChannelAgentChatPort.RuntimeAccess.AUTHENTICATED
                        : ChannelAgentChatPort.RuntimeAccess.OBSERVE_ONLY_UNKNOWN;
                response = chat.chat(new ChannelAgentChatPort.ChatCommand(
                        channel.projectId(), channel.channelId(), channel.channelType(), text(stored.runId()),
                        text(stored.sessionId()), execution.type(), execution.definitionId(), execution.version(),
                        execution.definitionHash(), message, trustedIdentity.orElse(null), identityStatus, runtimeAccess));
                delivery = replies.enqueue(channel.projectId(), channel.channelId(), message.externalConversationId(),
                        response.content(), text(stored.runId()), text(stored.sessionId()), replyAnchor(message));
                if (delivery == null) throw new IllegalStateException("CHANNEL_REPLY_DELIVERY_RESULT_REQUIRED");
            }
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

    private void auditUnknownSender(ChannelRecord channel,
                                    ChannelMessageRecord stored,
                                    ChannelModels.InboundMessage message,
                                    String reasonCode) {
        audit.record(channel.projectId(), executionKey(channel),
                "channel:" + channel.channelId() + ":" + message.senderId(),
                reasonCode, message.externalMessageId(), "MEDIUM", "BLOCKED",
                Map.of("channelId", channel.channelId(),
                        "accessPolicy", channel.accessPolicy().name(),
                        "runId", text(stored.runId()),
                        "sessionId", text(stored.sessionId()),
                        "reasonCode", reasonCode));
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

    private ChannelRecord activeInboundChannel(String channelId) {
        ChannelRecord channel = channel(channelId);
        if (channel.status() != ChannelStatus.ACTIVE) throw new SecurityException("CHANNEL_DISABLED");
        if (!channel.acceptsInbound()) throw new SecurityException("CHANNEL_INBOUND_DISABLED");
        return channel;
    }

    private ChannelModels.InboundMessage providerMessage(ChannelInboundEnvelope envelope) {
        ChannelInteractiveAction firstAction = envelope.content().actions().isEmpty()
                ? null
                : envelope.content().actions().get(0);
        ChannelModels.Action action = firstAction == null ? null : new ChannelModels.Action(
                firstAction.actionId(), "INTERACTIVE", firstAction.opaqueActionToken(), Map.of());
        var attachments = envelope.attachments().stream()
                .map(item -> new ChannelModels.Attachment(item.attachmentId(), item.fileName(), item.contentType(),
                        item.sizeBytes(), item.contentRef(), item.contentHash()))
                .toList();
        String content = envelope.content().plainText().isBlank()
                ? envelope.content().markdown()
                : envelope.content().plainText();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("providerAuthenticated", true);
        if (!text(envelope.message().threadId()).isBlank()) {
            metadata.put("threadId", text(envelope.message().threadId()));
        }
        return new ChannelModels.InboundMessage(
                envelope.message().externalMessageId(),
                envelope.message().conversation().externalConversationId(),
                envelope.sender().externalPrincipalId(),
                content,
                envelope.receivedAt().getEpochSecond(),
                Map.copyOf(metadata),
                action == null ? "TEXT" : "ACTION",
                attachments,
                action);
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

    private String sessionConversationKey(ChannelModels.InboundMessage message) {
        String threadId = text(message == null ? null : message.metadata().get("threadId"));
        return threadId.isBlank()
                ? message.externalConversationId()
                : message.externalConversationId() + "\nthread:" + threadId;
    }

    private String replyAnchor(ChannelModels.InboundMessage message) {
        String threadId = text(message == null ? null : message.metadata().get("threadId"));
        return threadId.isBlank() ? message.externalMessageId() : threadId;
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
