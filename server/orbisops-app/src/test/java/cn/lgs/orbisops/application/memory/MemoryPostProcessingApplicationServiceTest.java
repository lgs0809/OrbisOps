package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.adapter.repository.IColdMemoryRepository;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryItemSnapshot;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryPostProcessingApplicationServiceTest {

    @Test
    void processesSemanticExtractionPersistenceAndCompressionSynchronously() {
        RecordingColdMemoryRepository repository = new RecordingColdMemoryRepository();
        ColdMemoryStoreApplicationService coldStore = new ColdMemoryStoreApplicationService(
                repository,
                () -> true,
                null);
        List<String> operations = new ArrayList<>();
        ColdMemoryItemSnapshot extracted = item("durable fact");
        MemoryPostProcessingApplicationService service = new MemoryPostProcessingApplicationService(
                message -> operations.add("semantic:" + message.content()),
                message -> {
                    operations.add("extract:" + message.content());
                    return List.of(extracted);
                },
                coldStore,
                items -> operations.add("context:" + items.size()),
                (sessionId, userId, bufferSize) -> operations.add(
                        "compress:" + sessionId + ":" + userId + ":" + bufferSize),
                () -> Runnable::run,
                null);

        service.submit(command(false));

        assertEquals(List.of(
                "semantic:captured message",
                "extract:captured message",
                "context:1",
                "compress:s1:u1:8"), operations);
        assertEquals(List.of(extracted), repository.savedItems);
    }

    @Test
    void isolatesFailedOperationsWithoutDroppingLaterStages() {
        RecordingColdMemoryRepository repository = new RecordingColdMemoryRepository();
        ColdMemoryStoreApplicationService coldStore = new ColdMemoryStoreApplicationService(
                repository,
                () -> true,
                null);
        List<String> operations = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        ColdMemoryItemSnapshot extracted = item("surviving fact");
        MemoryPostProcessingApplicationService service = new MemoryPostProcessingApplicationService(
                message -> {
                    throw new IllegalStateException("semantic down");
                },
                message -> {
                    operations.add("extract");
                    return List.of(extracted);
                },
                coldStore,
                items -> operations.add("context"),
                (sessionId, userId, bufferSize) -> {
                    throw new IllegalStateException("compress down");
                },
                () -> Runnable::run,
                (operation, error) -> failures.add(operation + ":" + error.getMessage()));

        service.submit(command(false));

        assertEquals(List.of("extract", "context"), operations);
        assertEquals(List.of(extracted), repository.savedItems);
        assertEquals(List.of(
                "semantic-append:semantic down",
                "compress:compress down"), failures);
    }

    @Test
    void rejectedOuterSubmissionNeverFallsBackToCallerThread() {
        RecordingColdMemoryRepository repository = new RecordingColdMemoryRepository();
        ColdMemoryStoreApplicationService coldStore = new ColdMemoryStoreApplicationService(
                repository,
                () -> true,
                null);
        List<String> operations = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        MemoryPostProcessingApplicationService service = new MemoryPostProcessingApplicationService(
                message -> operations.add("semantic"),
                message -> List.of(item("must-not-run-inline")),
                coldStore,
                items -> operations.add("context"),
                (sessionId, userId, bufferSize) -> operations.add("compress"),
                () -> command -> {
                    throw new RejectedExecutionException("executor saturated");
                },
                (operation, error) -> failures.add(operation));

        service.submit(command(false));

        assertTrue(operations.isEmpty());
        assertTrue(repository.savedItems.isEmpty());
        assertEquals(List.of("post-processing-submit"), failures);
    }

    @Test
    void invalidCommandDoesNotCallPorts() {
        List<String> operations = new ArrayList<>();
        MemoryPostProcessingApplicationService service = new MemoryPostProcessingApplicationService(
                message -> operations.add("semantic"),
                message -> {
                    operations.add("extract");
                    return List.of();
                },
                null,
                items -> operations.add("context"),
                (sessionId, userId, bufferSize) -> operations.add("compress"),
                () -> null,
                null);

        service.submit(null);
        service.submit(new MemoryPostProcessingCommand(
                new MemoryMessageView(" ", "u1", "user", "content", "", Map.of()),
                1,
                false));

        assertTrue(operations.isEmpty());
    }

    private MemoryPostProcessingCommand command(boolean extractionAsyncEnabled) {
        return new MemoryPostProcessingCommand(
                new MemoryMessageView(
                        "s1",
                        "u1",
                        "user",
                        "captured message",
                        "2026-07-21 18:00:00",
                        Map.of("turn_index", 3)),
                8,
                extractionAsyncEnabled);
    }

    private ColdMemoryItemSnapshot item(String content) {
        return new ColdMemoryItemSnapshot(
                "s1",
                "u1",
                "fact",
                content,
                BigDecimal.valueOf(0.8),
                "[]",
                "user",
                "hash",
                Map.of(),
                "2026-07-21 18:00:00");
    }

    private static class RecordingColdMemoryRepository implements IColdMemoryRepository {

        private final List<ColdMemoryItemSnapshot> savedItems = new ArrayList<>();

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public void appendMessage(ColdMemoryMessageSnapshot message) {
        }

        @Override
        public void saveItems(List<ColdMemoryItemSnapshot> items) {
            savedItems.addAll(items);
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
