package cn.lgs.orbisops.application.channel;

import cn.lgs.orbisops.domain.channel.adapter.repository.IChannelRepository;
import cn.lgs.orbisops.domain.channel.model.ChannelMessageRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;
import cn.lgs.orbisops.types.execution.ExecutionBinding;
import cn.lgs.orbisops.types.execution.ExecutionVersionPolicy;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChannelQueryServiceTest {

    @Test
    void queryBuildsApplicationReadModelWithoutExposingPayload() {
        IChannelRepository repository = mock(IChannelRepository.class);
        ChannelProtocolCatalogPort protocols = mock(ChannelProtocolCatalogPort.class);
        ChannelRecord channel = channel("project-1");
        when(repository.findById("channel-1")).thenReturn(Optional.of(channel));
        when(repository.findMessages("project-1", "channel-1", 20)).thenReturn(List.of(new ChannelMessageRecord(
                "message-1", "channel-1", "project-1", "external-1", "room-1", "sender-1",
                "session-1", "run-1", "INBOUND", "COMPLETED", "encrypted-payload", null,
                Instant.parse("2026-07-20T00:00:00Z"), Instant.parse("2026-07-20T00:01:00Z"))));
        ChannelQueryService service = new ChannelQueryService(repository, protocols);

        Map<String, Object> view = service.get("project-1", "channel-1");
        List<Map<String, Object>> messages = service.messages("project-1", "channel-1", 20);

        assertEquals("agent-1", view.get("agentId"));
        assertEquals(Map.of("outboundUrl", "https://example.test"), view.get("config"));
        assertEquals("message-1", messages.get(0).get("messageId"));
        org.junit.jupiter.api.Assertions.assertFalse(messages.get(0).containsKey("payloadJson"));
        verify(repository).findMessages("project-1", "channel-1", 20);
    }

    @Test
    void queryRejectsCrossProjectChannelAccess() {
        IChannelRepository repository = mock(IChannelRepository.class);
        when(repository.findById("channel-1")).thenReturn(Optional.of(channel("project-1")));
        ChannelQueryService service = new ChannelQueryService(
                repository, mock(ChannelProtocolCatalogPort.class));

        SecurityException error = assertThrows(SecurityException.class,
                () -> service.get("project-2", "channel-1"));

        assertEquals("CHANNEL_PROJECT_MISMATCH", error.getMessage());
    }

    private ChannelRecord channel(String projectId) {
        return new ChannelRecord(
                "channel-1", projectId,
                ExecutionBinding.workflow("agent-1", ExecutionVersionPolicy.LATEST_PUBLISHED, 3, "agent-hash"),
                "Oncall", "GENERIC_WEBHOOK", "credential-ref",
                Map.of("outboundUrl", "https://example.test"),
                ChannelStatus.ACTIVE, "admin", Instant.parse("2026-07-20T00:00:00Z"),
                Instant.parse("2026-07-20T00:01:00Z"));
    }
}
