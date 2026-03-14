package cn.lgs.orbisops.application.channel;

import cn.lgs.orbisops.domain.channel.adapter.repository.IChannelRepository;
import cn.lgs.orbisops.domain.channel.model.ChannelMessageRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;
import cn.lgs.orbisops.types.execution.ExecutionBinding;
import cn.lgs.orbisops.types.execution.ExecutionVersionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChannelRunProgressApplicationServiceTest {

    private IChannelRepository repository;
    private ChannelOutboundApplicationService outbound;
    private ChannelOutboundDeliveryPort delivery;
    private ChannelRunProgressApplicationService service;

    @BeforeEach
    void setUp() {
        repository = mock(IChannelRepository.class);
        outbound = mock(ChannelOutboundApplicationService.class);
        delivery = mock(ChannelOutboundDeliveryPort.class);
        when(repository.findById("channel-1")).thenReturn(Optional.of(channel("FEISHU")));
        service = new ChannelRunProgressApplicationService(repository, outbound, delivery);
    }

    @Test
    void updateCapableProviderEditsDurableProgressAnchor() {
        when(delivery.supportsMessageUpdate(any())).thenReturn(true);
        when(repository.findLatestOutboundByRun("project-1", "channel-1", "run-1", ChannelRunProgressContract.SENDER))
                .thenReturn(Optional.of(progressRow(Instant.now().minusSeconds(20), "provider-progress-1", "DELIVERED")));
        when(outbound.update(any())).thenReturn(Map.of("status", "UPDATED", "updated", true));

        var outcome = service.project(command("INVESTIGATING", false));

        assertTrue(outcome.attempted());
        assertTrue(outcome.updated());
        assertFalse(outcome.sent());
        ArgumentCaptor<ChannelModels.Update> update = ArgumentCaptor.forClass(ChannelModels.Update.class);
        verify(outbound).update(update.capture());
        assertEquals("provider-progress-1", update.getValue().externalMessageId());
        assertEquals(ChannelRunProgressContract.SOURCE, update.getValue().metadata().get("source"));
        verify(outbound, never()).send(any());
    }

    @Test
    void updateFailureStartsFreshProgressAnchorWithoutFailingProjection() {
        when(delivery.supportsMessageUpdate(any())).thenReturn(true);
        when(repository.findLatestOutboundByRun("project-1", "channel-1", "run-1", ChannelRunProgressContract.SENDER))
                .thenReturn(Optional.of(progressRow(Instant.now().minusSeconds(20), "provider-progress-1", "DELIVERED")));
        doThrow(new IllegalStateException("provider edit unavailable")).when(outbound).update(any());
        when(outbound.send(any())).thenReturn(Map.of("status", "DELIVERED", "messageId", "message-2"));

        var outcome = service.project(command("INVESTIGATING", false));

        assertTrue(outcome.sent());
        assertEquals("message-2", outcome.messageId());
        verify(outbound).update(any());
        verify(outbound).send(any());
    }

    @Test
    void providerWithoutGenericUpdateDebouncesRecentStageEvents() {
        when(delivery.supportsMessageUpdate(any())).thenReturn(false);
        when(repository.findLatestOutboundByRun("project-1", "channel-1", "run-1", ChannelRunProgressContract.SENDER))
                .thenReturn(Optional.of(progressRow(Instant.now(), "", "DELIVERED")));

        var outcome = service.project(command("INVESTIGATING", false));

        assertFalse(outcome.attempted());
        assertEquals("DEBOUNCED", outcome.status());
        verify(outbound, never()).send(any());
        verify(outbound, never()).update(any());
    }

    @Test
    void providerWithoutGenericUpdateSendsOnlyStagedProgressAfterDebounce() {
        when(delivery.supportsMessageUpdate(any())).thenReturn(false);
        when(repository.findLatestOutboundByRun("project-1", "channel-1", "run-1", ChannelRunProgressContract.SENDER))
                .thenReturn(Optional.of(progressRow(Instant.now().minusSeconds(30), "", "DELIVERED")));
        when(outbound.send(any())).thenReturn(Map.of("status", "DELIVERED", "messageId", "message-2"));

        var outcome = service.project(command("INVESTIGATING", false));

        assertTrue(outcome.sent());
        verify(outbound).send(any());
        verify(outbound, never()).update(any());
    }

    @Test
    void forceTransitionBypassesDebounceAndCarriesOnlyStableProgressMetadata() {
        when(delivery.supportsMessageUpdate(any())).thenReturn(false);
        when(repository.findLatestOutboundByRun("project-1", "channel-1", "run-1", ChannelRunProgressContract.SENDER))
                .thenReturn(Optional.of(progressRow(Instant.now(), "", "DELIVERED")));
        when(outbound.send(any())).thenReturn(Map.of("status", "DELIVERED", "messageId", "message-3"));

        service.project(command("WAITING_APPROVAL", true));

        ArgumentCaptor<ChannelModels.Send> send = ArgumentCaptor.forClass(ChannelModels.Send.class);
        verify(outbound).send(send.capture());
        assertEquals(ChannelRunProgressContract.SOURCE, send.getValue().metadata().get("source"));
        assertEquals("inbound-message-1", send.getValue().metadata().get("replyToMessageId"));
        assertEquals(4, send.getValue().metadata().size());
        assertTrue(send.getValue().content().contains("Waiting for approval"));
    }

    private ChannelRunProgressApplicationService.ProgressCommand command(String stage, boolean force) {
        return new ChannelRunProgressApplicationService.ProgressCommand(
                "project-1", "channel-1", "room-1", "run-1", "session-1", "inbound-message-1",
                stage, "Database inspection in progress", force, "channel-runtime");
    }

    private ChannelMessageRecord progressRow(Instant updatedAt, String externalMessageId, String status) {
        return new ChannelMessageRecord(
                "message-1", "channel-1", "project-1", externalMessageId, "room-1",
                ChannelRunProgressContract.SENDER, "session-1", "run-1", "OUTBOUND", status,
                "{}", null, updatedAt.minusSeconds(1), updatedAt);
    }

    private ChannelRecord channel(String type) {
        return new ChannelRecord(
                "channel-1", "project-1",
                ExecutionBinding.workflow("agent-1", ExecutionVersionPolicy.LATEST_PUBLISHED, 2, "agent-hash"),
                "Oncall", type, "credential-ref", Map.of(), ChannelStatus.ACTIVE,
                "admin", null, null);
    }
}
