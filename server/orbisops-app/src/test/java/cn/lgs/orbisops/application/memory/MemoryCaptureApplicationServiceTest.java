package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.adapter.repository.IColdMemoryRepository;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryItemSnapshot;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;
import cn.lgs.orbisops.domain.memory.service.MemoryCapturePolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class MemoryCaptureApplicationServiceTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-07-21T10:30:00Z");

    @Test
    void preparesAndWritesTypedMessageToHotColdAndPostProcessing() {
        RecordingHotMemoryWritePort hotMemory = new RecordingHotMemoryWritePort();
        RecordingColdMemoryRepository repository = new RecordingColdMemoryRepository();
        ColdMemoryStoreApplicationService coldMemory = coldStore(repository);
        MemoryPostProcessingApplicationService postProcessing = mock(MemoryPostProcessingApplicationService.class);
        MemoryCaptureApplicationService service = service(
                hotMemory,
                coldMemory,
                postProcessing,
                new ArrayList<>());

        MemoryCaptureResult result = service.capture(command("s1", Map.of("projectId", "demo-project")));

        assertTrue(result.captured());
        assertEquals("assistant", result.message().role());
        assertEquals("2026-07-21 10:30:00", result.message().createdAt());
        assertEquals(1L, result.message().metadata().get("turn_index"));
        assertEquals(FIXED_INSTANT.toEpochMilli(), result.message().metadata().get("created_at_epoch_ms"));
        assertEquals("ACTIVE", result.message().metadata().get("memory_status"));
        assertEquals("demo-project", result.message().metadata().get("projectId"));
        assertEquals(List.of(result.message()), hotMemory.messages);
        assertEquals(8, hotMemory.bufferSizes.get(0));
        assertEquals(1, repository.messages.size());
        assertEquals("captured", repository.messages.get(0).content());
        ArgumentCaptor<MemoryPostProcessingCommand> commandCaptor =
                ArgumentCaptor.forClass(MemoryPostProcessingCommand.class);
        verify(postProcessing).submit(commandCaptor.capture());
        assertEquals(result.message(), commandCaptor.getValue().message());
        assertEquals(8, commandCaptor.getValue().bufferSize());
        assertTrue(commandCaptor.getValue().extractionAsyncEnabled());
    }

    @Test
    void explicitTurnIsPreservedWhileInternalCounterStillAdvances() {
        RecordingHotMemoryWritePort hotMemory = new RecordingHotMemoryWritePort();
        MemoryCaptureApplicationService service = service(
                hotMemory,
                coldStore(new RecordingColdMemoryRepository()),
                null,
                new ArrayList<>());

        service.capture(command("s1", Map.of("turn_index", 99)));
        service.capture(command("s1", Map.of()));

        assertEquals(99, hotMemory.messages.get(0).metadata().get("turn_index"));
        assertEquals(2L, hotMemory.messages.get(1).metadata().get("turn_index"));
    }

    @Test
    void clearingSessionStateResetsTurnCounter() {
        RecordingHotMemoryWritePort hotMemory = new RecordingHotMemoryWritePort();
        MemoryCaptureApplicationService service = service(
                hotMemory,
                coldStore(new RecordingColdMemoryRepository()),
                null,
                new ArrayList<>());

        service.capture(command("s1", Map.of()));
        service.clearSessionState("s1");
        service.capture(command("s1", Map.of()));

        assertEquals(1L, hotMemory.messages.get(0).metadata().get("turn_index"));
        assertEquals(1L, hotMemory.messages.get(1).metadata().get("turn_index"));
    }

    @Test
    void hotWriteFailureStopsLaterStagesAndIsObserved() {
        RecordingHotMemoryWritePort hotMemory = new RecordingHotMemoryWritePort();
        hotMemory.failure = new IllegalStateException("hot memory unavailable");
        RecordingColdMemoryRepository repository = new RecordingColdMemoryRepository();
        MemoryPostProcessingApplicationService postProcessing = mock(MemoryPostProcessingApplicationService.class);
        List<String> failures = new ArrayList<>();
        MemoryCaptureApplicationService service = service(
                hotMemory,
                coldStore(repository),
                postProcessing,
                failures);

        MemoryCaptureResult result = service.capture(command("s1", Map.of()));

        assertFalse(result.captured());
        assertTrue(repository.messages.isEmpty());
        verifyNoInteractions(postProcessing);
        assertEquals(List.of("capture:hot memory unavailable"), failures);
    }

    @Test
    void invalidCommandIsSkippedWithoutCallingPorts() {
        RecordingHotMemoryWritePort hotMemory = new RecordingHotMemoryWritePort();
        RecordingColdMemoryRepository repository = new RecordingColdMemoryRepository();
        MemoryPostProcessingApplicationService postProcessing = mock(MemoryPostProcessingApplicationService.class);
        MemoryCaptureApplicationService service = service(
                hotMemory,
                coldStore(repository),
                postProcessing,
                new ArrayList<>());

        MemoryCaptureResult nullResult = service.capture(null);
        MemoryCaptureResult invalidResult = service.capture(command(" ", Map.of()));

        assertFalse(nullResult.captured());
        assertFalse(invalidResult.captured());
        assertTrue(hotMemory.messages.isEmpty());
        assertTrue(repository.messages.isEmpty());
        verifyNoInteractions(postProcessing);
    }

    private MemoryCaptureApplicationService service(HotMemoryWritePort hotMemory,
                                                     ColdMemoryStoreApplicationService coldMemory,
                                                     MemoryPostProcessingApplicationService postProcessing,
                                                     List<String> failures) {
        return new MemoryCaptureApplicationService(
                hotMemory,
                coldMemory,
                postProcessing,
                new MemoryCapturePolicy(),
                Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC),
                (operation, error) -> failures.add(operation + ":" + error.getMessage()));
    }

    private ColdMemoryStoreApplicationService coldStore(RecordingColdMemoryRepository repository) {
        return new ColdMemoryStoreApplicationService(repository, () -> true, null);
    }

    private MemoryCaptureCommand command(String sessionId, Map<String, Object> metadata) {
        return new MemoryCaptureCommand(
                sessionId,
                "u1",
                " ",
                "captured",
                metadata,
                8,
                true);
    }

    private static class RecordingHotMemoryWritePort implements HotMemoryWritePort {

        private final List<MemoryMessageView> messages = new ArrayList<>();
        private final List<Integer> bufferSizes = new ArrayList<>();
        private RuntimeException failure;

        @Override
        public void append(MemoryMessageView message, int bufferSize) {
            if (failure != null) throw failure;
            messages.add(message);
            bufferSizes.add(bufferSize);
        }
    }

    private static class RecordingColdMemoryRepository implements IColdMemoryRepository {

        private final List<ColdMemoryMessageSnapshot> messages = new ArrayList<>();

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public void appendMessage(ColdMemoryMessageSnapshot message) {
            messages.add(message);
        }

        @Override
        public void saveItems(List<ColdMemoryItemSnapshot> items) {
        }

        @Override
        public List<ColdMemoryItemSnapshot> listItems(String sessionId, String userId, int limit) {
            return List.of();
        }

        @Override
        public void clear(String sessionId) {
        }
    }
}
