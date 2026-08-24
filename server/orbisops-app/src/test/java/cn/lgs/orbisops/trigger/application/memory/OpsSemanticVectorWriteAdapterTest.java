package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.domain.memory.model.SemanticMemoryDocumentSnapshot;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsSemanticVectorWriteAdapterTest {

    @Test
    @SuppressWarnings("unchecked")
    void mapsTypedDocumentToVectorStoreWrite() {
        ObjectProvider<VectorStore> provider = mock(ObjectProvider.class);
        VectorStore vectorStore = mock(VectorStore.class);
        when(provider.getIfAvailable()).thenReturn(vectorStore);
        OpsSemanticVectorWriteAdapter adapter = new OpsSemanticVectorWriteAdapter(provider);
        SemanticMemoryDocumentSnapshot snapshot = new SemanticMemoryDocumentSnapshot(
                "ops-message-stable-id",
                "semantic memory",
                Map.of("memory_type", "ops_chat", "session_id", "session-1"));

        adapter.writeVector(snapshot);

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        Document document = captor.getValue().get(0);
        assertEquals("ops-message-stable-id", document.getId());
        assertEquals("semantic memory", document.getText());
        assertEquals("ops_chat", document.getMetadata().get("memory_type"));
        assertEquals("session-1", document.getMetadata().get("session_id"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingVectorStoreFailsForApplicationFallback() {
        ObjectProvider<VectorStore> provider = mock(ObjectProvider.class);
        OpsSemanticVectorWriteAdapter adapter = new OpsSemanticVectorWriteAdapter(provider);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> adapter.writeVector(new SemanticMemoryDocumentSnapshot(
                        "", "memory", Map.of())));

        assertEquals("VectorStore 未初始化", error.getMessage());
    }
}
