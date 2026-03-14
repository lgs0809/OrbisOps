package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.channel.ChannelModels;
import cn.lgs.orbisops.application.channel.ChannelOutboundApplicationService;
import cn.lgs.orbisops.application.channel.ChannelRuntimeReadPort;
import cn.lgs.orbisops.application.runtime.workflow.WorkflowApprovalApplicationService;
import cn.lgs.orbisops.application.runtime.workflow.WorkflowApprovalRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelAccessPolicy;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;
import cn.lgs.orbisops.types.execution.ExecutionBinding;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsWorkflowApprovalChannelBridgeTest {

    @Test
    void dingTalkUsesSameSharedWorkflowApprovalActionsWithoutTransportAuthority() {
        ChannelRuntimeReadPort channels = mock(ChannelRuntimeReadPort.class);
        ChannelOutboundApplicationService outbound = mock(ChannelOutboundApplicationService.class);
        OpsWorkflowApprovalChannelBridge bridge = new OpsWorkflowApprovalChannelBridge(channels, outbound);
        when(channels.findById("channel-ding")).thenReturn(Optional.of(new ChannelRecord(
                "channel-ding", "project-1", ExecutionBinding.react(), "DingTalk Oncall", "DINGTALK", "credential-ref",
                Map.of("private", "must-not-project"), ChannelAccessPolicy.DENY_UNKNOWN, ChannelStatus.ACTIVE,
                "admin", null, null)));
        when(outbound.sendRich(any())).thenReturn(Map.of("delivered", true));

        WorkflowApprovalRecord record = mock(WorkflowApprovalRecord.class);
        when(record.approvalId()).thenReturn("approval-1");
        when(record.requestSummary()).thenReturn("Deploy production change");
        WorkflowApprovalApplicationService.IssuedApproval issued =
                new WorkflowApprovalApplicationService.IssuedApproval(record, "opaque-approve", "opaque-reject");
        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .runId("run-1")
                .sessionId("session-1")
                .projectId("project-1")
                .userId("user-1")
                .metadata(Map.of(
                        "source", "CHANNEL",
                        "channelId", "channel-ding",
                        "externalConversationId", "group:cid-1"))
                .build();
        OpsWorkflowNode node = OpsWorkflowNode.builder()
                .nodeId("approval-node")
                .type("HUMAN_APPROVAL")
                .description("Production approval")
                .build();

        assertTrue(bridge.sendIfChannel(request, node, issued));

        ArgumentCaptor<ChannelModels.RichSend> command = ArgumentCaptor.forClass(ChannelModels.RichSend.class);
        verify(outbound).sendRich(command.capture());
        ChannelModels.RichSend value = command.getValue();
        assertEquals("project-1", value.projectId());
        assertEquals("channel-ding", value.channelId());
        assertEquals("group:cid-1", value.target());
        assertEquals(2, value.content().actions().size());
        assertEquals("opaque-approve", value.content().actions().get(0).opaqueActionToken());
        assertEquals("opaque-reject", value.content().actions().get(1).opaqueActionToken());
        assertEquals("WORKFLOW_APPROVAL", value.metadata().get("source"));
        assertEquals("run-1", value.metadata().get("runId"));
        assertEquals(3, value.metadata().size());
    }
}
