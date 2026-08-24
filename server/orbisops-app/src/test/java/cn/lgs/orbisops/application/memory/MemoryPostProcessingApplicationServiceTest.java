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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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

    @Test void durableDispatchReturnsBeforeTheQueuedModelRunsAndDefersItsFailureWithoutCompleting() {
        var repository=mock(cn.lgs.orbisops.domain.memory.adapter.repository.IConversationMemoryRepository.class);
        var extraction=mock(MemoryExtractionPort.class);
        var source=new ColdMemoryMessageSnapshot("s1","u1","user","captured message","now",Map.of());
        var job=new cn.lgs.orbisops.domain.memory.model.MemoryProcessingJob(1,"s1",1,"EXTRACTION",8,12,"worker",12,source);
        when(repository.pending(eq("s1"),eq(12),anyLong())).thenReturn(List.of(1L));
        when(repository.claim(eq(1L),anyString(),anyLong(),eq(300_000L))).thenReturn(java.util.Optional.of(job));
        when(extraction.extractDurably(any())).thenReturn(new MemoryExtractionPort.Outcome(List.of(),true,"RULE_FALLBACK_MODEL_PENDING"));
        var queue=new ArrayList<Runnable>();
        var service=new MemoryPostProcessingApplicationService(null,extraction,null,null,null,()->queue::add,null,repository,java.time.Clock.systemUTC());
        service.submit(command(false));
        assertEquals(1,queue.size());verifyNoInteractions(extraction);
        verify(repository,never()).claim(anyLong(),anyString(),anyLong(),anyLong());
        queue.get(0).run();
        verify(extraction).extractDurably(any());
        verify(repository).defer(eq(job),eq("RULE_FALLBACK_MODEL_PENDING"),eq("MEMORY_EXTRACTION_MODEL_DEFERRED"),anyLong(),any());
        verify(repository,never()).complete(any(),anyString(),anyLong(),any());
    }

    @Test void disabledModelDoesNotEraseAnAlreadyPersistedExtractionSupplement() {
        var repository=mock(cn.lgs.orbisops.domain.memory.adapter.repository.IConversationMemoryRepository.class);
        var extraction=mock(MemoryExtractionPort.class);
        var source=new ColdMemoryMessageSnapshot("s1","u1","user","captured message","now",Map.of());
        var job=new cn.lgs.orbisops.domain.memory.model.MemoryProcessingJob(1,"s1",1,"EXTRACTION",8,9,"worker",9,source,"","RULE_FALLBACK_MODEL_PENDING");
        when(repository.pending(eq("s1"),eq(12),anyLong())).thenReturn(List.of(1L));
        when(repository.claim(eq(1L),anyString(),anyLong(),eq(300_000L))).thenReturn(java.util.Optional.of(job));
        when(extraction.extractDurably(any())).thenReturn(new MemoryExtractionPort.Outcome(List.of(),false,"RULES_ONLY"));
        var service=new MemoryPostProcessingApplicationService(null,extraction,null,null,null,()->Runnable::run,null,repository,java.time.Clock.systemUTC());
        service.submit(command(false));
        verify(repository).retry(eq(job),eq("MEMORY_EXTRACTION_MODEL_DISABLED"),anyLong());
        verify(repository,never()).complete(any(),anyString(),anyLong(),any());
        verify(repository,never()).defer(any(),anyString(),anyString(),anyLong(),any());
    }

    @Test void semanticLexicalFallbackKeepsTheSameDurableTaskPendingAndRevokedSourcesNeverCallThePort() {
        var repository=mock(cn.lgs.orbisops.domain.memory.adapter.repository.IConversationMemoryRepository.class);
        var semantic=mock(SemanticMemoryWritePort.class);
        var source=new ColdMemoryMessageSnapshot("s1","u1","user","semantic source","now",Map.of());
        var job=new cn.lgs.orbisops.domain.memory.model.MemoryProcessingJob(1,"s1",1,"SEMANTIC",8,12,"worker",12,source,"","LEXICAL_MODEL_PENDING","frozen");
        when(repository.pending(eq("s1"),eq(12),anyLong())).thenReturn(List.of(1L));
        when(repository.claim(eq(1L),anyString(),anyLong(),eq(300_000L))).thenReturn(java.util.Optional.of(job));
        when(repository.validSemanticSource(eq(job),anyLong())).thenReturn(true,false);
        when(semantic.appendDurably(job)).thenReturn(new SemanticMemoryWritePort.Outcome("LEXICAL",true,"EMBEDDING_TEMPORARILY_UNAVAILABLE"));
        var service=new MemoryPostProcessingApplicationService(semantic,null,null,null,null,()->Runnable::run,null,repository,java.time.Clock.systemUTC());
        service.submit(command(false));
        verify(repository).defer(eq(job),eq("LEXICAL_MODEL_PENDING"),eq("EMBEDDING_TEMPORARILY_UNAVAILABLE"),anyLong(),isNull());
        service.submit(command(false));verify(semantic,times(1)).appendDurably(job);
        verify(repository).block(eq(job),eq("MEMORY_SEMANTIC_SOURCE_OR_OWNER_CHANGED"),anyLong());
        verify(repository,never()).complete(any(),anyString(),anyLong(),any());
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
