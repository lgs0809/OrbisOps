package cn.lgs.orbisops.trigger.ops.channel;

import cn.lgs.orbisops.application.channel.ChannelQueryService;
import cn.lgs.orbisops.domain.channel.adapter.repository.IChannelOutboxRepository;
import cn.lgs.orbisops.domain.channel.model.ChannelOutboxRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelOutboxStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsChannelOutboxQueryServiceTest {

    @Test
    void listGetAndStatusOfMustUseProjectScopedRepositoryQueries() {
        IChannelOutboxRepository repository = mock(IChannelOutboxRepository.class);
        ChannelQueryService channels = mock(ChannelQueryService.class);
        when(repository.available()).thenReturn(true);
        when(repository.findByProject("project-1", 10)).thenReturn(List.of(record("PENDING")));
        when(repository.findByProjectAndId("project-1", 7L)).thenReturn(Optional.of(record("PENDING")));
        when(repository.findById(7L)).thenReturn(Optional.of(record("SUCCEEDED")));
        OpsChannelOutboxQueryService service = service(repository, channels);

        List<Map<String, Object>> list = service.list("project-1", 10);
        Map<String, Object> one = service.get("project-1", 7L);

        assertEquals(1, list.size());
        assertEquals(7L, one.get("id"));
        assertEquals("SUCCEEDED", service.statusOf(7L));
    }

    @Test
    void statusMustCombineChannelAndOutboxHealth() {
        IChannelOutboxRepository repository = mock(IChannelOutboxRepository.class);
        ChannelQueryService channels = mock(ChannelQueryService.class);
        when(repository.available()).thenReturn(true);
        when(repository.status("project-1")).thenReturn(new ChannelOutboxStatus(3, 2));
        when(repository.statusAll()).thenReturn(new ChannelOutboxStatus(5, 4));
        when(channels.status("project-1")).thenReturn(Map.of("channelReady", true));
        when(channels.statusAll()).thenReturn(Map.of("channelCount", 6));
        OpsChannelOutboxQueryService service = service(repository, channels);

        Map<String, Object> project = service.status("project-1");
        Map<String, Object> all = service.statusAll();

        assertEquals(true, project.get("channelReady"));
        assertEquals(3L, ((Number) project.get("pendingNotifications")).longValue());
        assertEquals(2L, ((Number) project.get("deadLetterNotifications")).longValue());
        assertEquals(6, all.get("channelCount"));
        assertEquals(true, all.get("outboxReady"));
        assertThrows(UnsupportedOperationException.class, () -> project.put("changed", true));
    }

    @Test
    void unavailableStoreMustExposeStableStatusAndBlockDataQueries() {
        IChannelOutboxRepository repository = mock(IChannelOutboxRepository.class);
        when(repository.available()).thenReturn(false);
        OpsChannelOutboxQueryService service = service(repository, mock(ChannelQueryService.class));

        Map<String, Object> status = service.status("project-1");

        assertFalse((Boolean) status.get("ready"));
        assertEquals("CHANNEL_NOTIFICATION_STORE_UNAVAILABLE", status.get("reason"));
        assertThrows(IllegalStateException.class, () -> service.list("project-1", 10));
        assertThrows(IllegalStateException.class, () -> service.get("project-1", 7L));
        assertTrue(!service.available());
    }

    private OpsChannelOutboxQueryService service(
            IChannelOutboxRepository repository,
            ChannelQueryService channels) {
        return new OpsChannelOutboxQueryService(
                repository,
                channels,
                new OpsChannelOutboxViewMapper());
    }

    private ChannelOutboxRecord record(String status) {
        return new ChannelOutboxRecord(
                7L, "dedup", "project-1", "channel", "target", "analysis", status,
                "message", "{}", "hash", 0, "", "",
                null, null, null, null, null, null);
    }
}
