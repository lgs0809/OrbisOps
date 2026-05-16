package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.memory.MemoryCompressionApplicationService;
import cn.lgs.orbisops.application.memory.MemoryCompressionCommand;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsContextCompressorTest {

    @Test
    void mapsHistoricalConfigurationAndMessagesToTypedCommand() {
        MemoryCompressionApplicationService applicationService =
                mock(MemoryCompressionApplicationService.class);
        OpsContextCompressor compressor = compressor(applicationService, true);
        List<OpsMemoryMessage> messages = List.of(
                message("user", "问题一", "2026-07-21 10:00:00"),
                message("assistant", "结论一", "2026-07-21 10:01:00"));

        compressor.compressIfNeeded(" session-1 ", " user-1 ", messages, 20);

        ArgumentCaptor<MemoryCompressionCommand> captor =
                ArgumentCaptor.forClass(MemoryCompressionCommand.class);
        verify(applicationService).compress(captor.capture());
        MemoryCompressionCommand command = captor.getValue();
        assertEquals("session-1", command.sessionId());
        assertEquals("user-1", command.userId());
        assertEquals(2, command.messages().size());
        assertEquals("问题一", command.messages().get(0).content());
        assertEquals(4, command.thresholdMessages());
        assertEquals(2, command.keepRecent());
        assertEquals(false, command.modelEnabled());
        assertEquals(6000, command.modelMaxInputChars());
        assertEquals(20, command.hotBufferMessages());
    }

    @Test
    void disabledOrInvalidInputDoesNotCallApplicationService() {
        MemoryCompressionApplicationService applicationService =
                mock(MemoryCompressionApplicationService.class);
        OpsContextCompressor disabled = compressor(applicationService, false);
        OpsContextCompressor enabled = compressor(applicationService, true);

        disabled.compressIfNeeded(
                "session-1", "user-1", List.of(message("user", "x", "t")), 8);
        enabled.compressIfNeeded(" ", "user-1", List.of(message("user", "x", "t")), 8);
        enabled.compressIfNeeded("session-1", "user-1", null, 8);

        verify(applicationService, never()).compress(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void trimContextDelegatesToApplicationService() {
        MemoryCompressionApplicationService applicationService =
                mock(MemoryCompressionApplicationService.class);
        when(applicationService.trimContext("long", 100)).thenReturn("trimmed");
        OpsContextCompressor compressor = compressor(applicationService, true);

        assertEquals("trimmed", compressor.trimContext("long", 100));
        verify(applicationService).trimContext("long", 100);
    }

    private OpsContextCompressor compressor(
            MemoryCompressionApplicationService applicationService,
            boolean enabled) {
        return new OpsContextCompressor(
                applicationService,
                new OpsContextCompressionSettings(enabled, 4, 2, false, 6_000));
    }

    private OpsMemoryMessage message(String role, String content, String createdAt) {
        return OpsMemoryMessage.builder()
                .sessionId("session-1")
                .userId("user-1")
                .role(role)
                .content(content)
                .createdAt(createdAt)
                .metadata(Map.of())
                .build();
    }
}
