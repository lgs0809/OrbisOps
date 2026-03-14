package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.ChannelRuntimeAuditPort;
import cn.lgs.orbisops.trigger.application.worksession.OpsWorkSessionRunApplicationFacade;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Schedules a fresh Work Session attempt after the durable approval decision has already been committed. */
@Component
public class OpsWorkflowApprovalResumeListener {

    private final OpsWorkSessionRunApplicationFacade workSessions;
    private final ChannelRuntimeAuditPort audit;

    public OpsWorkflowApprovalResumeListener(OpsWorkSessionRunApplicationFacade workSessions,
                                             ChannelRuntimeAuditPort audit) {
        if (workSessions == null) throw new IllegalArgumentException("WORK_SESSION_RUN_FACADE_REQUIRED");
        if (audit == null) throw new IllegalArgumentException("CHANNEL_RUNTIME_AUDIT_PORT_REQUIRED");
        this.workSessions = workSessions;
        this.audit = audit;
    }

    @EventListener
    public void onDecision(WorkflowApprovalDecidedEvent event) {
        if (event == null) return;
        try {
            workSessions.resumeApproval(event.runId(), event.projectId(), event.actor());
            audit(event, "WORKFLOW_APPROVAL_RESUME_SCHEDULED", "SUCCEEDED", "");
        } catch (RuntimeException failure) {
            audit(event, "WORKFLOW_APPROVAL_RESUME_SCHEDULE_FAILED", "FAILED", text(failure.getMessage()));
            // The decision is already authoritative. Do not roll it back or allow an opposite second decision.
        }
    }

    private void audit(WorkflowApprovalDecidedEvent event,
                       String action,
                       String status,
                       String error) {
        try {
            audit.record(event.projectId(), event.runId(), event.actor(), action,
                    event.approvalId(), "MEDIUM", status,
                    Map.of(
                            "approvalId", event.approvalId(),
                            "nodeId", event.nodeId(),
                            "decision", event.decision().name(),
                            "error", error));
        } catch (RuntimeException ignored) {
            // Resume scheduling outcome is secondary to the committed approval decision.
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
