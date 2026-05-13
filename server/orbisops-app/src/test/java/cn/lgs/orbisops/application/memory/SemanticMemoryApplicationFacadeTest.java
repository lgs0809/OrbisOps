package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.SemanticMemoryDocumentSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SemanticMemoryApplicationFacadeTest {

    @Test
    void delegatesWriteSearchAndClearToTypedServices() {
        SemanticMemoryWriteApplicationService writeService =
                mock(SemanticMemoryWriteApplicationService.class);
        SemanticMemoryRetrievalApplicationService retrievalService =
                mock(SemanticMemoryRetrievalApplicationService.class);
        SemanticMemoryClearApplicationService clearService =
                mock(SemanticMemoryClearApplicationService.class);
        SemanticMemoryApplicationFacade facade = new SemanticMemoryApplicationFacade(
                writeService,
                retrievalService,
                clearService);
        SemanticMemoryWriteCommand writeCommand = new SemanticMemoryWriteCommand(
                "s1", "u1", "user", "memory", "now", Map.of(), true);
        SemanticMemoryRetrievalQuery query = new SemanticMemoryRetrievalQuery(
                "s1", "u1", "memory", 8, 8, true, true, 6D);
        SemanticMemoryDocumentSnapshot document = new SemanticMemoryDocumentSnapshot(
                "doc-1", "memory", Map.of("memory_kind", "message"));
        when(writeService.write(writeCommand)).thenReturn(SemanticMemoryWriteResult.vector());
        when(retrievalService.search(query)).thenReturn(List.of(document));
        when(clearService.clear("s1")).thenReturn(true);

        assertEquals(SemanticMemoryWriteResult.vector(), facade.write(writeCommand));
        assertEquals(List.of(document), facade.search(query));
        assertEquals(true, facade.clear("s1"));
        verify(writeService).write(writeCommand);
        verify(retrievalService).search(query);
        verify(clearService).clear("s1");
    }

    @Test
    void missingServicesUseSafeNoopResults() {
        SemanticMemoryApplicationFacade facade = new SemanticMemoryApplicationFacade(
                null,
                null,
                null);

        assertEquals(SemanticMemoryWriteResult.skipped(), facade.write(null));
        assertEquals(List.of(), facade.search(null));
        assertFalse(facade.clear("s1"));
    }
}
