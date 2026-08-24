package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.SemanticMemoryDocumentSnapshot;
import cn.lgs.orbisops.domain.memory.service.MemoryContentHashPolicy;
import cn.lgs.orbisops.domain.memory.service.SemanticMemoryPolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticMemoryWriteApplicationServiceTest {

    @Test
    void projectsHistoricalMetadataAndWritesVectorFirst() {
        AtomicReference<SemanticMemoryDocumentSnapshot> vectorDocument = new AtomicReference<>();
        AtomicInteger lexicalCalls = new AtomicInteger();
        SemanticMemoryWriteApplicationService service = service(
                vectorDocument::set,
                document -> lexicalCalls.incrementAndGet(),
                null);

        SemanticMemoryWriteResult result = service.write(command(true));

        assertTrue(result.written());
        assertEquals("vector", result.storage());
        assertEquals(0, lexicalCalls.get());
        SemanticMemoryDocumentSnapshot document = vectorDocument.get();
        assertEquals("  durable memory  ", document.content());
        assertEquals("ops_chat", document.metadata().get("memory_type"));
        assertEquals("message", document.metadata().get("memory_kind"));
        assertEquals("ops-chat-memory", document.metadata().get("knowledge"));
        assertEquals("session-1", document.metadata().get("session_id"));
        assertEquals("user-1", document.metadata().get("user_id"));
        assertEquals("ops_memory_facade", document.metadata().get("source"));
        assertEquals("user", document.metadata().get("role"));
        assertEquals("2026-07-21 23:30:00", document.metadata().get("created_at"));
        assertEquals(7, document.metadata().get("turn_index"));
        assertEquals(123L, document.metadata().get("created_at_epoch_ms"));
        assertEquals("ACTIVE", document.metadata().get("memory_status"));
        assertEquals("demo-project", ((Map<?, ?>) document.metadata().get("origin_metadata")).get("projectId"));
    }

    @Test
    void missingRoleAndStatusUseHistoricalDefaults() {
        SemanticMemoryWriteApplicationService service = service(
                document -> { },
                null,
                null);
        SemanticMemoryWriteCommand command = new SemanticMemoryWriteCommand(
                "session-1", "user-1", " ", "content", "", Map.of(), true);

        SemanticMemoryDocumentSnapshot document = service.project(command);

        assertEquals("assistant", document.metadata().get("role"));
        assertEquals("ACTIVE", document.metadata().get("memory_status"));
        assertFalse(document.metadata().containsKey("turn_index"));
        assertFalse(document.metadata().containsKey("created_at_epoch_ms"));
    }

    @Test
    void embeddingUnavailableSkipsVectorAndWritesLexical() {
        AtomicInteger vectorCalls = new AtomicInteger();
        AtomicReference<SemanticMemoryDocumentSnapshot> lexicalDocument = new AtomicReference<>();
        SemanticMemoryWriteApplicationService service = service(
                document -> vectorCalls.incrementAndGet(),
                lexicalDocument::set,
                null);

        SemanticMemoryWriteResult result = service.write(command(false));

        assertEquals(0, vectorCalls.get());
        assertTrue(result.written());
        assertEquals("lexical", result.storage());
        assertEquals("  durable memory  ", lexicalDocument.get().content());
    }

    @Test
    void vectorFailureIsObservedAndFallsBackToLexical() {
        List<String> failures = new ArrayList<>();
        AtomicInteger lexicalCalls = new AtomicInteger();
        SemanticMemoryWriteApplicationService service = service(
                document -> {
                    throw new IllegalStateException("vector down");
                },
                document -> lexicalCalls.incrementAndGet(),
                (operation, error) -> failures.add(operation + ":" + error.getMessage()));

        SemanticMemoryWriteResult result = service.write(command(true));

        assertEquals(List.of("vector-write:vector down"), failures);
        assertEquals(1, lexicalCalls.get());
        assertEquals("lexical", result.storage());
    }

    @Test
    void lexicalFailureIsObservedAndReturnsSkipped() {
        List<String> failures = new ArrayList<>();
        SemanticMemoryWriteApplicationService service = service(
                null,
                document -> {
                    throw new IllegalStateException("fts down");
                },
                (operation, error) -> failures.add(operation + ":" + error.getMessage()));

        SemanticMemoryWriteResult result = service.write(command(false));

        assertFalse(result.written());
        assertEquals("", result.storage());
        assertEquals(List.of("lexical-write:fts down"), failures);
    }

    @Test
    void observerFailureCannotBlockLexicalFallback() {
        AtomicInteger lexicalCalls = new AtomicInteger();
        SemanticMemoryWriteApplicationService service = service(
                document -> {
                    throw new IllegalStateException("vector down");
                },
                document -> lexicalCalls.incrementAndGet(),
                (operation, error) -> {
                    throw new IllegalStateException("observer down");
                });

        SemanticMemoryWriteResult result = service.write(command(true));

        assertEquals(1, lexicalCalls.get());
        assertEquals("lexical", result.storage());
    }

    @Test void configuredButTemporarilyUnavailableEmbeddingKeepsLexicalAndDurableModelIntent() {
        var calls=new AtomicInteger();
        var service=service(document->calls.incrementAndGet(),document->{},null);
        var legacy=command(false);
        var configured=new SemanticMemoryWriteCommand(legacy.sessionId(),legacy.userId(),legacy.role(),legacy.content(),legacy.createdAt(),legacy.metadata(),false,true);
        var result=service.write(configured);
        assertTrue(result.written());assertEquals("lexical",result.storage());assertTrue(result.modelPending());
        assertEquals("EMBEDDING_TEMPORARILY_UNAVAILABLE",result.reason());assertEquals(0,calls.get());
        var intentional=service.write(command(false));assertFalse(intentional.modelPending());assertEquals("INTENTIONAL_LEXICAL",intentional.reason());
    }
    @Test void transientVectorFailureDoesNotBecomeCompletedLexicalAndLateEpochsHaveDifferentPhysicalIds() {
        var vectors=new ArrayList<SemanticMemoryDocumentSnapshot>();var lexical=new ArrayList<SemanticMemoryDocumentSnapshot>();
        var service=service(document->{vectors.add(document);throw new IllegalStateException("temporary");},lexical::add,null);
        var input=command(true);
        for(int epoch=1;epoch<=10;epoch++) {
            var metadata=new java.util.LinkedHashMap<>(input.metadata());metadata.put("semantic_job_id",42L);metadata.put("semantic_epoch",epoch);metadata.put("semantic_source_hash","frozen");
            var result=service.write(new SemanticMemoryWriteCommand(input.sessionId(),input.userId(),input.role(),input.content(),input.createdAt(),metadata,true,true));
            assertTrue(result.written());assertTrue(result.modelPending());assertEquals("lexical",result.storage());
        }
        assertEquals(10,vectors.stream().map(SemanticMemoryDocumentSnapshot::id).distinct().count());
        assertEquals(1,lexical.stream().map(SemanticMemoryDocumentSnapshot::id).distinct().count());
        assertTrue(vectors.stream().allMatch(document->"VECTOR".equals(document.metadata().get("semantic_projection"))));
        assertTrue(lexical.stream().allMatch(document->"LEXICAL".equals(document.metadata().get("semantic_projection"))));
    }

    @Test
    void nullBlankOrMissingPersistencePortsAreSkipped() {
        SemanticMemoryWriteApplicationService service = service(null, null, null);

        assertEquals(SemanticMemoryWriteResult.skipped(), service.write(null));
        assertEquals(SemanticMemoryWriteResult.skipped(), service.write(new SemanticMemoryWriteCommand(
                "s", "u", "user", " ", "", Map.of(), true)));
        assertEquals(SemanticMemoryWriteResult.skipped(), service.write(command(true)));
    }

    private SemanticMemoryWriteApplicationService service(
            SemanticVectorWritePort vectorPort,
            SemanticLexicalWritePort lexicalPort,
            SemanticMemoryWriteFailurePort failurePort) {
        return new SemanticMemoryWriteApplicationService(
                new SemanticMemoryPolicy(new MemoryContentHashPolicy()),
                vectorPort,
                lexicalPort,
                failurePort);
    }

    private SemanticMemoryWriteCommand command(boolean embeddingAvailable) {
        return new SemanticMemoryWriteCommand(
                " session-1 ",
                " user-1 ",
                " user ",
                "  durable memory  ",
                " 2026-07-21 23:30:00 ",
                Map.of(
                        "turn_index", 7,
                        "created_at_epoch_ms", 123L,
                        "projectId", "demo-project"),
                embeddingAvailable);
    }
}
