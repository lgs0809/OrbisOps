package cn.lgs.orbisops.application.channel;

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
        when(delivery.send(any(), any(), any(), any(), any()))
                .thenReturn(new ChannelDeliveryResult(true, Map.of("externalMessageId", "bridge-1")));

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
        verify(audit).record(org.mockito.ArgumentMatchers.eq("project-1"),
                org.mockito.ArgumentMatchers.eq("channel:channel-1"), org.mockito.ArgumentMatchers.eq("admin"),
                org.mockito.ArgumentMatchers.eq("CHANNEL_MESSAGE_DELIVERED"),
                org.mockito.ArgumentMatchers.eq(inserted.getValue().messageId()),
                org.mockito.ArgumentMatchers.eq("LOW"), org.mockito.ArgumentMatchers.eq("SUCCEEDED"), any());
        assertEquals(Boolean.TRUE, result.get("delivered"));
    }

    @Test
    void protocolFailureMarksOutboundFailedAndNeverReturnsSuccess() {
        when(delivery.send(any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("token=do-not-leak"));

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> service.send(command()));

        assertEquals(true, failure.getMessage().contains("CHANNEL_DELIVERY_FAILED"));
        ArgumentCaptor<String> safeError = ArgumentCaptor.forClass(String.class);
        verify(repository).markOutboundFailed(any(), safeError.capture());
        assertEquals(false, safeError.getValue().contains("do-not-leak"));
    }

    @Test
    void outboundDeliveryReceivesMaskedContentAndAllowlistedMetadataOnly() {
        when(delivery.send(any(), any(), any(), any(), any()))
                .thenReturn(new ChannelDeliveryResult(true, Map.of("externalMessageId", "bridge-1")));

        service.send(command());

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(delivery).send(any(), any(), org.mockito.ArgumentMatchers.eq("room-1"),
                content.capture(), metadata.capture());
        assertEquals(false, content.getValue().contains("secret-value"));
        assertEquals(true, content.getValue().contains("password=***"));
        assertEquals("run-1", metadata.getValue().get("runId"));
        assertEquals("session-1", metadata.getValue().get("sessionId"));
        assertEquals(false, metadata.getValue().containsKey("token"));
        assertEquals(false, metadata.getValue().containsKey("context"));
    }

    @Test
    void auditFailureAfterRemoteSendIsExplicitOutcomeUnknown() {
        when(delivery.send(any(), any(), any(), any(), any()))
                .thenReturn(new ChannelDeliveryResult(true, Map.of()));
        doThrow(new IllegalStateException("audit unavailable")).when(audit).record(
                any(), any(), any(), org.mockito.ArgumentMatchers.eq("CHANNEL_MESSAGE_DELIVERED"),
                any(), any(), any(), any());

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> service.send(command()));

        assertEquals("CHANNEL_DELIVERY_AUDIT_FAILED_AFTER_SEND", failure.getMessage());
        verify(repository).markOutboundCompleted(any(), org.mockito.ArgumentMatchers.eq("DELIVERED"), any());
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
