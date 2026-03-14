package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.ChannelIdentityDirectoryPort;
import cn.lgs.orbisops.application.channel.ChannelRuntimeAuditPort;
import cn.lgs.orbisops.application.channel.ChannelRuntimeReadPort;
import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveActionEnvelope;
import cn.lgs.orbisops.application.runtime.workflow.WorkflowApprovalApplicationService;
import cn.lgs.orbisops.application.runtime.workflow.WorkflowApprovalRecord;
import cn.lgs.orbisops.application.worksession.run.WorkSessionRunApplicationService;
import cn.lgs.orbisops.domain.channel.model.ChannelIdentityRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/** Channel callback adapter for durable Workflow HUMAN_APPROVAL. It records a decision; it never executes Graph inline. */
@Service
public class OpsWorkflowApprovalActionService {

    private final WorkflowApprovalApplicationService approvals;
    private final ChannelRuntimeReadPort channels;
    private final ChannelIdentityDirectoryPort identities;
    private final WorkSessionRunApplicationService workSessions;
    private final ApplicationEventPublisher events;
    private final ChannelRuntimeAuditPort audit;

    public OpsWorkflowApprovalActionService(WorkflowApprovalApplicationService approvals,
                                            ChannelRuntimeReadPort channels,
                                            ChannelIdentityDirectoryPort identities,
                                            WorkSessionRunApplicationService workSessions,
                                            ApplicationEventPublisher events,
                                            ChannelRuntimeAuditPort audit) {
        this.approvals = required(approvals, "WORKFLOW_APPROVAL_SERVICE_REQUIRED");
        this.channels = required(channels, "CHANNEL_RUNTIME_READ_PORT_REQUIRED");
        this.identities = required(identities, "CHANNEL_IDENTITY_DIRECTORY_REQUIRED");
        this.workSessions = required(workSessions, "WORK_SESSION_RUN_SERVICE_REQUIRED");
        this.events = required(events, "WORKFLOW_APPROVAL_EVENT_PUBLISHER_REQUIRED");
        this.audit = required(audit, "CHANNEL_RUNTIME_AUDIT_PORT_REQUIRED");
    }

    public Optional<ActionOutcome> tryExecute(String channelId, ChannelInteractiveActionEnvelope envelope) {
        if (envelope == null) return Optional.empty();
        WorkflowApprovalApplicationService.ResolvedAction resolved;
        try {
            resolved = approvals.resolveAction(envelope.action().opaqueActionToken());
        } catch (IllegalArgumentException invalidShape) {
            return Optional.empty();
        } catch (SecurityException notWorkflowAction) {
            String message = text(notWorkflowAction.getMessage());
            if (message.startsWith("WORKFLOW_APPROVAL_ACTION_INVALID")) return Optional.empty();
            throw notWorkflowAction;
        }

        WorkflowApprovalRecord record = resolved.record();
        String safeChannelId = requiredText(channelId, "CHANNEL_ID_REQUIRED");
        if (record.channelId().isBlank()) {
            throw new SecurityException("WORKFLOW_APPROVAL_ACTION_NOT_CHANNEL_BOUND");
        }
        if (!record.channelId().equals(safeChannelId)) {
            throw new SecurityException("WORKFLOW_APPROVAL_ACTION_CHANNEL_MISMATCH");
        }
        String conversationId = envelope.message().conversation().externalConversationId();
        if (!record.target().isBlank() && !record.target().equals(conversationId)) {
            throw new SecurityException("WORKFLOW_APPROVAL_ACTION_CONVERSATION_MISMATCH");
        }

        ChannelIdentityRecord identity = channels.findIdentity(
                        safeChannelId,
                        requiredText(envelope.actor().externalPrincipalId(), "CHANNEL_EXTERNAL_IDENTITY_REQUIRED"))
                .filter(item -> item.status() == ChannelStatus.ACTIVE)
                .orElseThrow(() -> new SecurityException("WORKFLOW_APPROVAL_IDENTITY_MAPPING_REQUIRED"));
        if (!identities.isTrusted(record.projectId(), identity)) {
            throw new SecurityException("WORKFLOW_APPROVAL_IDENTITY_NOT_AUTHORIZED");
        }
        String actor = identity.platformUserId();

        // Authorize against the current WAITING_APPROVAL Work Session before consuming the decision.
        workSessions.resumeApproval(record.runId(), record.projectId(), actor);
        WorkflowApprovalRecord decided = approvals.decide(resolved, actor);
        events.publishEvent(new WorkflowApprovalDecidedEvent(
                decided.approvalId(), decided.runId(), decided.projectId(), decided.nodeId(), actor, resolved.decision()));
        auditSuccess(decided, actor, resolved.decision());
        return Optional.of(new ActionOutcome(
                decided.approvalId(),
                decided.runId(),
                decided.projectId(),
                decided.nodeId(),
                resolved.decision(),
                decided.status().name()));
    }

    private void auditSuccess(WorkflowApprovalRecord record,
                              String actor,
                              WorkflowApprovalRecord.Decision decision) {
        try {
            audit.record(record.projectId(), record.runId(), actor,
                    "WORKFLOW_APPROVAL_DECIDED", record.approvalId(), "MEDIUM", "SUCCEEDED",
                    Map.of(
                            "approvalId", record.approvalId(),
                            "nodeId", record.nodeId(),
                            "decision", decision.name(),
                            "channelId", record.channelId()));
        } catch (RuntimeException ignored) {
            // The durable workflow approval ledger is authoritative; secondary audit failure cannot undo a decision.
        }
    }

    private String requiredText(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static <T> T required(T value, String reasonCode) {
        if (value == null) throw new IllegalArgumentException(reasonCode);
        return value;
    }

    public record ActionOutcome(String approvalId,
                                String runId,
                                String projectId,
                                String nodeId,
                                WorkflowApprovalRecord.Decision decision,
                                String status) {
    }
}
