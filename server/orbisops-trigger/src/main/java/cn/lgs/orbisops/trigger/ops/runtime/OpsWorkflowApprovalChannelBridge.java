package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.channel.ChannelModels;
import cn.lgs.orbisops.application.channel.ChannelOutboundApplicationService;
import cn.lgs.orbisops.application.channel.ChannelRuntimeReadPort;
import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveAction;
import cn.lgs.orbisops.application.channel.provider.ChannelRichContent;
import cn.lgs.orbisops.application.runtime.workflow.WorkflowApprovalApplicationService;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Optional provider bridge. Durable workflow approval remains authoritative even when no Channel is involved. */
@Component
class OpsWorkflowApprovalChannelBridge {

    private static final Set<String> INTERACTIVE_PROVIDERS = Set.of("FEISHU", "WECOM", "SLACK", "DINGTALK");

    private final ChannelRuntimeReadPort channels;
    private final ChannelOutboundApplicationService outbound;

    OpsWorkflowApprovalChannelBridge(ChannelRuntimeReadPort channels,
                                     ChannelOutboundApplicationService outbound) {
        if (channels == null) throw new IllegalArgumentException("CHANNEL_RUNTIME_READ_PORT_REQUIRED");
        if (outbound == null) throw new IllegalArgumentException("CHANNEL_OUTBOUND_SERVICE_REQUIRED");
        this.channels = channels;
        this.outbound = outbound;
    }

    boolean sendIfChannel(OpsAgentChatRequest request,
                          OpsWorkflowNode node,
                          WorkflowApprovalApplicationService.IssuedApproval issued) {
        if (request == null || issued == null) return false;
        Map<String, Object> metadata = request.getMetadata() == null ? Map.of() : request.getMetadata();
        if (!"CHANNEL".equalsIgnoreCase(text(metadata.get("source")))) return false;
        String channelId = text(metadata.get("channelId"));
        String target = text(metadata.get("externalConversationId"));
        if (channelId.isBlank() || target.isBlank()) {
            throw new IllegalStateException("WORKFLOW_APPROVAL_CHANNEL_CONTEXT_INCOMPLETE");
        }
        ChannelRecord channel = channels.findById(channelId)
                .orElseThrow(() -> new IllegalStateException("WORKFLOW_APPROVAL_CHANNEL_NOT_FOUND"));
        if (!text(request.getProjectId()).equals(channel.projectId())) {
            throw new SecurityException("WORKFLOW_APPROVAL_CHANNEL_PROJECT_MISMATCH");
        }
        if (channel.status() != ChannelStatus.ACTIVE) {
            throw new SecurityException("WORKFLOW_APPROVAL_CHANNEL_DISABLED");
        }
        if (!INTERACTIVE_PROVIDERS.contains(channel.channelType())) {
            throw new IllegalStateException("WORKFLOW_APPROVAL_INTERACTIVE_CHANNEL_REQUIRED:" + channel.channelType());
        }

        String summary = summary(request, issued);
        outbound.sendRich(new ChannelModels.RichSend(
                request.getProjectId(),
                channelId,
                target,
                new ChannelRichContent(summary, summary, List.of(
                        new ChannelInteractiveAction(
                                "workflow-approve-" + issued.record().approvalId(),
                                "Approve",
                                issued.approveAction(),
                                ChannelInteractiveAction.ActionStyle.PRIMARY),
                        new ChannelInteractiveAction(
                                "workflow-reject-" + issued.record().approvalId(),
                                "Reject",
                                issued.rejectAction(),
                                ChannelInteractiveAction.ActionStyle.DANGER))),
                Map.of(
                        "source", "WORKFLOW_APPROVAL",
                        "runId", text(request.getRunId()),
                        "sessionId", text(request.getSessionId())),
                text(request.getUserId()).isBlank() ? "workflow-runtime" : request.getUserId()));
        return true;
    }

    private String summary(OpsAgentChatRequest request,
                           WorkflowApprovalApplicationService.IssuedApproval issued) {
        StringBuilder summary = new StringBuilder("Workflow approval required");
        if (!issued.record().requestSummary().isBlank()) {
            summary.append("\nRequest: ").append(issued.record().requestSummary());
        }
        summary.append("\nRun: ").append(text(request.getRunId()));
        summary.append("\nApprove or reject to resume this durable workflow.");
        return summary.toString();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
