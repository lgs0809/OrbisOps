package cn.lgs.orbisops.application.channel;

import cn.lgs.orbisops.application.channel.provider.ChannelDeliveryReceipt;
import cn.lgs.orbisops.application.channel.provider.ChannelInteractiveAction;
import cn.lgs.orbisops.application.channel.provider.ChannelOutboundMessage;
import cn.lgs.orbisops.application.channel.provider.ChannelRichContent;
import cn.lgs.orbisops.domain.channel.adapter.repository.IChannelRepository;
import cn.lgs.orbisops.domain.channel.model.ChannelMessageRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;
import cn.lgs.orbisops.types.execution.ExecutionBinding;
import cn.lgs.orbisops.types.execution.ExecutionVersionPolicy;
import cn.lgs.orbisops.domain.channel.service.ChannelOutboundContentPolicy;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChannelOutboundApplicationServiceTest {

    private IChannelRepository repository;
    private ChannelOutboundDeliveryPort delivery;
    private ChannelRuntimeAuditPort audit;
    private ChannelOutboundApplicationService service;

    @BeforeEach
    void setUp() {
        repository = mock(IChannelRepository.class);
        delivery = mock(ChannelOutboundDeliveryPort.class);
        audit = mock(ChannelRuntimeAuditPort.class);
        when(repository.findById("channel-1")).thenReturn(Optional.of(channel()));
        service = new ChannelOutboundApplicationService(repository, delivery, audit,
                new ChannelOutboundContentPolicy(), 20000);
    }

    @Test
    void sendPersistsResultAndAuditAroundProtocolDelivery() {
        when(delivery.send(any(), any()))
                .thenReturn(new ChannelDeliveryReceipt(true, "DELIVERED", "bridge-1", 200, Instant.now()));

        Map<String, Object> result = service.send(command());

        ArgumentCaptor<ChannelMessageRecord> inserted = ArgumentCaptor.forClass(ChannelMessageRecord.class);
        verify(repository).insertOutbound(inserted.capture());
        assertEquals("SENDING", inserted.getValue().status());
        assertEquals("run-1", inserted.getValue().runId());
        Map<String, Object> pendingPayload = CanonicalJson.parseObject(inserted.getValue().payloadJson());
        assertEquals("run-1", ((Map<?, ?>) pendingPayload.get("metadata")).get("runId"));
        ArgumentCaptor<String> deliveryPayload = ArgumentCaptor.forClass(String.class);
        verify(repository).markOutboundCompleted(
                org.mockito.ArgumentMatchers.eq(inserted.getValue().messageId()),
                org.mockito.ArgumentMatchers.eq("DELIVERED"),
                deliveryPayload.capture());
        Map<String, Object> completedPayload = CanonicalJson.parseObject(deliveryPayload.getValue());
        assertEquals(true, completedPayload.get("delivered"));
        assertEquals("bridge-1", completedPayload.get("externalMessageId"));
        verify(repository).bindOutboundExternalMessageId(inserted.getValue().messageId(), "bridge-1");
        verify(audit).record(org.mockito.ArgumentMatchers.eq("project-1"),
                org.mockito.ArgumentMatchers.eq("channel:channel-1"), org.mockito.ArgumentMatchers.eq("admin"),
                org.mockito.ArgumentMatchers.eq("CHANNEL_MESSAGE_DELIVERED"),
                org.mockito.ArgumentMatchers.eq(inserted.getValue().messageId()),
                org.mockito.ArgumentMatchers.eq("LOW"), org.mockito.ArgumentMatchers.eq("SUCCEEDED"), any());
        assertEquals(Boolean.TRUE, result.get("delivered"));
    }

    @Test
    void protocolFailureMarksOutboundFailedAndNeverReturnsSuccess() {
        when(delivery.send(any(), any()))
                .thenThrow(new IllegalStateException("token=do-not-leak"));

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> service.send(command()));

        assertEquals(true, failure.getMessage().contains("CHANNEL_DELIVERY_FAILED"));
        ArgumentCaptor<String> safeError = ArgumentCaptor.forClass(String.class);
        verify(repository).markOutboundFailed(any(), safeError.capture());
        assertEquals(false, safeError.getValue().contains("do-not-leak"));
    }

    @Test
    void outboundDeliveryReceivesMaskedContentAndAllowlistedMetadataOnly() {
        when(delivery.send(any(), any()))
                .thenReturn(new ChannelDeliveryReceipt(true, "DELIVERED", "bridge-1", 200, Instant.now()));

        service.send(command());

        ArgumentCaptor<ChannelOutboundMessage> outbound = ArgumentCaptor.forClass(ChannelOutboundMessage.class);
        verify(delivery).send(any(), outbound.capture());
        assertEquals("room-1", outbound.getValue().conversation().externalConversationId());
        assertEquals(false, outbound.getValue().content().plainText().contains("secret-value"));
        assertEquals(true, outbound.getValue().content().plainText().contains("password=***"));
        assertEquals("run-1", outbound.getValue().metadata().runId());
        assertEquals("session-1", outbound.getValue().metadata().sessionId());
        assertEquals(false, outbound.getValue().metadata().toProtocolMap().containsKey("token"));
        assertEquals(false, outbound.getValue().metadata().toProtocolMap().containsKey("context"));
    }

    @Test
    void auditFailureAfterRemoteSendIsExplicitOutcomeUnknown() {
        when(delivery.send(any(), any()))
                .thenReturn(new ChannelDeliveryReceipt(true, "DELIVERED", "", 200, Instant.now()));
        doThrow(new IllegalStateException("audit unavailable")).when(audit).record(
                any(), any(), any(), org.mockito.ArgumentMatchers.eq("CHANNEL_MESSAGE_DELIVERED"),
                any(), any(), any(), any());

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> service.send(command()));

        assertEquals("CHANNEL_DELIVERY_AUDIT_FAILED_AFTER_SEND", failure.getMessage());
        verify(repository).markOutboundCompleted(any(), org.mockito.ArgumentMatchers.eq("DELIVERED"), any());
    }

    @Test
    void richApprovalActionIsDeliveredButOpaqueTokenIsNotPersistedInChannelMessagePayload() {
        when(delivery.send(any(), any()))
                .thenReturn(new ChannelDeliveryReceipt(true, "DELIVERED", "provider-1", 200, Instant.now()));
        String opaqueToken = "[REDACTED_SECRET]";
        ChannelInteractiveAction action = new ChannelInteractiveAction(
                "action-1", "Approve", opaqueToken, ChannelInteractiveAction.ActionStyle.PRIMARY);

        service.sendRich(new ChannelModels.RichSend(
                "project-1", "channel-1", "room-1",
                new ChannelRichContent("Approval required", "Approval required", java.util.List.of(action)),
                Map.of("source", "CHANNEL_APPROVAL_CARD"), "admin"));

        ArgumentCaptor<ChannelOutboundMessage> outbound = ArgumentCaptor.forClass(ChannelOutboundMessage.class);
        verify(delivery).send(any(), outbound.capture());
        assertEquals(opaqueToken, outbound.getValue().content().actions().get(0).opaqueActionToken());

        ArgumentCaptor<ChannelMessageRecord> inserted = ArgumentCaptor.forClass(ChannelMessageRecord.class);
        verify(repository).insertOutbound(inserted.capture());
        assertEquals(false, inserted.getValue().payloadJson().contains(opaqueToken));
        assertEquals(false, inserted.getValue().payloadJson().contains("action-1"));
    }

    @Test
    void runProgressUsesDedicatedSenderAndBindsProviderMessageId() {
        when(delivery.send(any(), any()))
                .thenReturn(new ChannelDeliveryReceipt(true, "DELIVERED", "provider-progress-1", 200, Instant.now()));

        service.send(new ChannelModels.Send(
                "project-1", "channel-1", "room-1", "Investigating",
                Map.of("runId", "run-1", "sessionId", "session-1", "source", ChannelRunProgressContract.SOURCE),
                "channel-runtime"));

        ArgumentCaptor<ChannelMessageRecord> inserted = ArgumentCaptor.forClass(ChannelMessageRecord.class);
        verify(repository).insertOutbound(inserted.capture());
        assertEquals(ChannelRunProgressContract.SENDER, inserted.getValue().senderId());
        verify(repository).bindOutboundExternalMessageId(inserted.getValue().messageId(), "provider-progress-1");
    }

    @Test
    void typedUpdateUsesExistingProviderMessageWithoutCreatingAnotherOutboundRow() {
        when(delivery.supportsMessageUpdate(any())).thenReturn(true);
        when(delivery.update(any(), any(), any()))
                .thenReturn(new ChannelDeliveryReceipt(true, "UPDATED", "provider-progress-1", 200, Instant.now()));

        Map<String, Object> result = service.update(new ChannelModels.Update(
                "project-1", "channel-1", "room-1", "message-1", "provider-progress-1",
                "Updated progress", Map.of("runId", "run-1", "source", ChannelRunProgressContract.SOURCE),
                "channel-runtime"));

        assertEquals(true, result.get("updated"));
        verify(delivery).update(any(), any(), any());
        verify(repository).markOutboundCompleted(
                org.mockito.ArgumentMatchers.eq("message-1"),
                org.mockito.ArgumentMatchers.eq("UPDATED"), any());
    }

    private ChannelModels.Send command() {
        return new ChannelModels.Send("project-1", "channel-1", "room-1", "password=secret-value\nready",
                Map.of("runId", "run-1", "sessionId", "session-1", "token", "drop-me",
                        "context", Map.of("credential", "nested-secret")), "admin");
    }

    private ChannelRecord channel() {
        return new ChannelRecord("channel-1", "project-1",
                ExecutionBinding.workflow("agent-1", ExecutionVersionPolicy.LATEST_PUBLISHED, 2, "agent-hash"),
                "Oncall", "GENERIC_WEBHOOK", "credential-ref",
                Map.of("outboundUrl", "https://bridge.test/send"), ChannelStatus.ACTIVE,
                "admin", null, null);
    }
}
