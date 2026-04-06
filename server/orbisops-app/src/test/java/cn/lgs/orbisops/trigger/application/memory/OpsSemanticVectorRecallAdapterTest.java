package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.domain.memory.model.SemanticMemoryRankedCandidate;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsSemanticVectorRecallAdapterTest {

    @Test
    @SuppressWarnings("unchecked")
    void mapsScopedVectorDocumentsToTypedRankedCandidates() {
        ObjectProvider<VectorStore> provider = mock(ObjectProvider.class);
        VectorStore vectorStore = mock(VectorStore.class);
        when(provider.getIfAvailable()).thenReturn(vectorStore);
        Document scoped = new Document("doc-1", "memory one", Map.of(
                "memory_type", "ops_chat",
                "memory_kind", "message",
                "session_id", "session-1",
                "user_id", "user-1",
                "memory_status", "ACTIVE"));
        Document wrongSession = new Document("doc-2", "memory two", Map.of(
                "memory_type", "ops_chat",
                "memory_kind", "message",
                "session_id", "session-2",
                "user_id", "user-1"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(scoped, wrongSession));
        OpsSemanticVectorRecallAdapter adapter = new OpsSemanticVectorRecallAdapter(provider);

        List<SemanticMemoryRankedCandidate> result = adapter.recallVector(
                "session-1", "user-1", "join metric", 8);

        assertEquals(1, result.size());
        SemanticMemoryRankedCandidate candidate = result.get(0);
        assertEquals("vector", candidate.source());
        assertEquals(1, candidate.rank());
        assertEquals(1.0D / 61D, candidate.rrfScore(), 0.0000001D);
        assertEquals("doc-1", candidate.document().id());
        assertEquals(1, candidate.document().metadata().get("memory_vector_rank"));
        verify(vectorStore).similaritySearch(any(SearchRequest.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void blankDocumentUserAllowsSharedRecall() {
        ObjectProvider<VectorStore> provider = mock(ObjectProvider.class);
        VectorStore vectorStore = mock(VectorStore.class);
        when(provider.getIfAvailable()).thenReturn(vectorStore);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(new Document(
                "doc-1",
                "shared memory",
                Map.of(
                        "memory_type", "ops_chat",
                        "memory_kind", "message",
                        "session_id", "session-1",
                        "user_id", ""))));
        OpsSemanticVectorRecallAdapter adapter = new OpsSemanticVectorRecallAdapter(provider);

        assertEquals(1, adapter.recallVector("session-1", "user-2", "query", 4).size());
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingVectorStoreFailsForApplicationContainment() {
        ObjectProvider<VectorStore> provider = mock(ObjectProvider.class);
        OpsSemanticVectorRecallAdapter adapter = new OpsSemanticVectorRecallAdapter(provider);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> adapter.recallVector("session-1", "user-1", "query", 8));

        assertEquals("VectorStore 未初始化", error.getMessage());
    }
}
