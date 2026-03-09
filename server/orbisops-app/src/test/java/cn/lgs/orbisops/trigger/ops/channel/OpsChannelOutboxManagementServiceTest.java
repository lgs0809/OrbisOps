package cn.lgs.orbisops.trigger.ops.channel;

import cn.lgs.orbisops.application.channel.ChannelQueryService;
import cn.lgs.orbisops.domain.channel.adapter.repository.IChannelOutboxRepository;
import cn.lgs.orbisops.domain.channel.model.ChannelOutboxRecord;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsChannelOutboxManagementServiceTest {

    @Test
    void requeueMustReadBeforeAndAfterWithinProjectAndAuditTransition() {
        IChannelOutboxRepository repository = mock(IChannelOutboxRepository.class);
        when(repository.available()).thenReturn(true);
        when(repository.findByProjectAndId("project-1", 7L))
                .thenReturn(Optional.of(record("DEAD_LETTER")), Optional.of(record("PENDING")));
        when(repository.requeue("project-1", 7L)).thenReturn(true);
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        OpsChannelOutboxManagementService service = service(repository, audit);

        Map<String, Object> result = service.requeue("project-1", 7L, "admin-1");

        assertEquals("PENDING", result.get("status"));
        verify(repository).requeue("project-1", 7L);
        verify(audit).record(
                eq("project-1"),
                eq("channel-notification-outbox"),
                eq("requeue"),
                eq("7"),
                any(),
                eq(Map.of("status", "PENDING", "actor", "admin-1")));
    }

    @Test
    void cancelConflictMustPreserveStableErrorAndAvoidAudit() {
        IChannelOutboxRepository repository = mock(IChannelOutboxRepository.class);
        when(repository.available()).thenReturn(true);
        when(repository.findByProjectAndId("project-1", 7L)).thenReturn(Optional.of(record("DEAD_LETTER")));
        when(repository.cancel("project-1", 7L)).thenReturn(false);
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        OpsChannelOutboxManagementService service = service(repository, audit);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.cancel("project-1", 7L, "admin"));

        assertEquals("CHANNEL_OUTBOX_CANCEL_STATE_CONFLICT", error.getMessage());
    }

    private OpsChannelOutboxManagementService service(
            IChannelOutboxRepository repository,
            OpsConfigAuditService audit) {
        OpsChannelOutboxQueryService queries = new OpsChannelOutboxQueryService(
                repository,
                mock(ChannelQueryService.class),
                new OpsChannelOutboxViewMapper());
        return new OpsChannelOutboxManagementService(repository, queries, audit);
    }

    private ChannelOutboxRecord record(String status) {
        return new ChannelOutboxRecord(
                7L, "dedup", "project-1", "channel", "target", "analysis", status,
                "message", "{}", "hash", 0, "", "",
                null, null, null, null, null, null);
    }
}
