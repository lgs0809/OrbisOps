package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.ChannelModels;
import cn.lgs.orbisops.application.channel.ChannelOutboundApplicationService;
import cn.lgs.orbisops.application.channel.ChannelRuntimeReadPort;
import cn.lgs.orbisops.application.changepackage.ChangePackageCurrentReadPort;
import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveAction;
import cn.lgs.orbisops.domain.channel.model.ChannelAccessPolicy;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentState;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePointer;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageType;
import cn.lgs.orbisops.types.execution.ExecutionBinding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsChannelApprovalCardServiceTest {

    private OpsChannelApprovalActionService approvalActions;
    private ChannelOutboundApplicationService outbound;
    private ChannelRuntimeReadPort channels;
    private ChangePackageCurrentReadPort packages;
    private OpsChannelApprovalCardService service;

    @BeforeEach
    void setUp() {
        approvalActions = mock(OpsChannelApprovalActionService.class);
        outbound = mock(ChannelOutboundApplicationService.class);
        channels = mock(ChannelRuntimeReadPort.class);
        packages = mock(ChangePackageCurrentReadPort.class);
        service = new OpsChannelApprovalCardService(approvalActions, outbound, channels, packages);
        when(packages.available()).thenReturn(true);
        when(packages.find("cp-1")).thenReturn(Optional.of(current()));
        when(approvalActions.issue("channel-1", "cp-1", "user-1"))
                .thenReturn(new OpsChannelApprovalActionService.ApprovalActions(
                        "cp-1", 3, "hash-3", List.of(
                        new ChannelInteractiveAction("approve-id", "Approve", "opaque-action-a",
                                ChannelInteractiveAction.ActionStyle.PRIMARY),
                        new ChannelInteractiveAction("reject-id", "Reject", "opaque-action-b",
                                ChannelInteractiveAction.ActionStyle.DANGER))));
        when(outbound.sendRich(any())).thenReturn(Map.of("delivered", true));
    }

    @Test
    void availableProjectsOnlyMinimalActiveInteractiveChannelFacts() {
        ChannelRecord activeFeishu = channel("feishu-1", "Feishu Oncall", "FEISHU", ChannelStatus.ACTIVE);
        ChannelRecord activeWeCom = channel("wecom-1", "WeCom Oncall", "WECOM", ChannelStatus.ACTIVE);
        ChannelRecord activeSlack = channel("slack-1", "Slack Oncall", "SLACK", ChannelStatus.ACTIVE);
        ChannelRecord activeDingTalk = channel("dingtalk-1", "DingTalk Oncall", "DINGTALK", ChannelStatus.ACTIVE);
        ChannelRecord disabledFeishu = channel("feishu-disabled", "Disabled", "FEISHU", ChannelStatus.DISABLED);
        ChannelRecord generic = channel("generic-1", "Generic", "GENERIC_WEBHOOK", ChannelStatus.ACTIVE);
        when(channels.findByProject("project-1")).thenReturn(List.of(activeFeishu, activeWeCom, activeSlack, activeDingTalk, disabledFeishu, generic));

        List<Map<String, Object>> result = service.available("project-1");

        assertEquals(4, result.size());
        assertEquals(Map.of("channelId", "feishu-1", "name", "Feishu Oncall", "type", "FEISHU"), result.get(0));
        assertEquals(Map.of("channelId", "wecom-1", "name", "WeCom Oncall", "type", "WECOM"), result.get(1));
        assertEquals(Map.of("channelId", "slack-1", "name", "Slack Oncall", "type", "SLACK"), result.get(2));
        assertEquals(Map.of("channelId", "dingtalk-1", "name", "DingTalk Oncall", "type", "DINGTALK"), result.get(3));
        result.forEach(item -> {
            assertEquals(3, item.size());
            assertEquals(false, item.containsKey("credentialRef"));
            assertEquals(false, item.containsKey("config"));
        });
    }

    @Test
    void sendsWeComApprovalCardThroughSharedRichOutbound() {
        when(channels.findById("channel-1")).thenReturn(Optional.of(channel("WECOM")));

        Map<String, Object> result = service.send("project-1", "channel-1", "room-1", "cp-1", "user-1");

        assertEquals(true, result.get("delivered"));
        assertRichCommand();
    }

    @Test
    void sendsFeishuApprovalCardThroughSameSharedRichOutbound() {
        when(channels.findById("channel-1")).thenReturn(Optional.of(channel("FEISHU")));

        Map<String, Object> result = service.send("project-1", "channel-1", "chat-1", "cp-1", "user-1");

        assertEquals(true, result.get("delivered"));
        assertRichCommand();
    }

    @Test
    void sendsSlackApprovalCardThroughSameSharedRichOutbound() {
        when(channels.findById("channel-1")).thenReturn(Optional.of(channel("SLACK")));

        Map<String, Object> result = service.send("project-1", "channel-1", "C123", "cp-1", "user-1");

        assertEquals(true, result.get("delivered"));
        assertRichCommand();
    }

    @Test
    void sendsDingTalkApprovalCardThroughSameSharedRichOutbound() {
        when(channels.findById("channel-1")).thenReturn(Optional.of(channel("DINGTALK")));

        Map<String, Object> result = service.send("project-1", "channel-1", "group:cid-1", "cp-1", "user-1");

        assertEquals(true, result.get("delivered"));
        assertRichCommand();
    }

    @Test
    void unsupportedProviderIsRejectedBeforeIssuingActions() {
        when(channels.findById("channel-1")).thenReturn(Optional.of(channel("GENERIC_WEBHOOK")));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> service.send("project-1", "channel-1", "room-1", "cp-1", "user-1"));

        assertTrue(failure.getMessage().startsWith("CHANNEL_APPROVAL_CARD_PROVIDER_UNSUPPORTED"));
        verify(approvalActions, never()).issue(any(), any(), any());
        verify(outbound, never()).sendRich(any());
    }

    @Test
    void crossProjectChannelIsRejectedBeforeReadingPackage() {
        when(channels.findById("channel-1")).thenReturn(Optional.of(new ChannelRecord(
                "channel-1", "other-project", ExecutionBinding.react(), "Channel", "FEISHU", "credential-ref",
                Map.of(), ChannelAccessPolicy.DENY_UNKNOWN, ChannelStatus.ACTIVE, "admin", null, null)));

        SecurityException failure = assertThrows(SecurityException.class,
                () -> service.send("project-1", "channel-1", "chat-1", "cp-1", "user-1"));

        assertEquals("CHANNEL_PROJECT_MISMATCH", failure.getMessage());
        verify(packages, never()).find(any());
        verify(outbound, never()).sendRich(any());
    }

    private void assertRichCommand() {
        ArgumentCaptor<ChannelModels.RichSend> command = ArgumentCaptor.forClass(ChannelModels.RichSend.class);
        verify(outbound).sendRich(command.capture());
        ChannelModels.RichSend value = command.getValue();
        assertEquals("project-1", value.projectId());
        assertEquals("channel-1", value.channelId());
        assertEquals(2, value.content().actions().size());
        assertTrue(value.content().plainText().contains("ChangePackage cp-1 v3"));
        assertTrue(value.content().plainText().contains("WHY: Restore checkout service"));
        assertTrue(value.content().plainText().contains("WHAT: See OrbisOps Approval Cockpit"));
        assertTrue(value.content().plainText().contains("WHERE: production / unspecified"));
    }

    private ChannelRecord channel(String type) {
        return channel("channel-1", "Channel", type, ChannelStatus.ACTIVE);
    }

    private ChannelRecord channel(String channelId, String name, String type, ChannelStatus status) {
        return new ChannelRecord(
                channelId, "project-1", ExecutionBinding.react(), name, type, "credential-ref",
                Map.of("private", "must-not-project"), ChannelAccessPolicy.DENY_UNKNOWN, status, "admin", null, null);
    }

    private ChangePackageCurrent current() {
        ChangePackagePointer pointer = new ChangePackagePointer(
                "cp-1", ChangePackageStatus.REVIEWING, 3, "hash-3", 0, "");
        ChangePackageCurrentState state = ChangePackageCurrentState.fromSnapshot(Map.of(
                "riskLevel", "HIGH",
                "targetEnvironment", "production",
                "objective", "Restore checkout service",
                "rollbackStepsJson", "[rollback]",
                "verificationCriteriaJson", "[verify]"));
        return new ChangePackageCurrent(
                1L, pointer, "session-1", "incident-1", "project-1", "agent-1", 1,
                ChangePackageType.MCP_OPERATION_PACKAGE, state, null, "", "creator", "", null, null, null);
    }
}
