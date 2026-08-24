package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.ColdMemoryItemSnapshot;
import cn.lgs.orbisops.domain.memory.service.MemorySceneClassificationPolicy;
import cn.lgs.orbisops.domain.memory.service.MemorySelectionPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MemoryQueryApplicationServiceTest {

    @Test
    void protectedSourceOverflowIsNotSilentlyDowngradedToEmptyContext() {
        MemoryRetrievalApplicationService retrieval = mock(MemoryRetrievalApplicationService.class);
        when(retrieval.retrieve(any())).thenThrow(new MemoryContextIntegrityException("MEMORY_PROTECTED_CONTEXT_BUDGET_EXCEEDED"));
        var service = service(retrieval, null, null, new ArrayList<>());
        org.junit.jupiter.api.Assertions.assertThrows(MemoryContextIntegrityException.class,
                () -> service.query(command("", "", "继续处理")));
    }

    @Test
    void assemblesRetrievalSelectionRenderingAndReferences() {
        MemoryRetrievalApplicationService retrievalService = mock(MemoryRetrievalApplicationService.class);
        MemoryContextRenderingApplicationService renderingService =
                mock(MemoryContextRenderingApplicationService.class);
        MemorySelectionReferenceApplicationService referenceService =
                mock(MemorySelectionReferenceApplicationService.class);
        ContextMemoryView contextMemory = contextMemory();
        when(retrievalService.retrieve(any())).thenReturn(new MemoryRetrievalResult(
                List.of(coldItem()),
                List.of(message("hot message", "assistant", 1)),
                List.of(message("semantic message", "user", 2)),
                List.of(contextMemory)));
        when(renderingService.render(any())).thenReturn(
                new MemoryContextRenderingResult("rendered context", 1, 1, 2, false));
        MemorySelectionReference reference = new MemorySelectionReference(
                "ctx-1", 1, "hash", "PROJECT_CONTEXT", "PROJECT", "demo-project",
                "source", "hash", "2026-07-21T11:00:00Z", false);
        when(referenceService.assemble(List.of(contextMemory))).thenReturn(List.of(reference));
        MemoryQueryApplicationService service = service(
                retrievalService,
                renderingService,
                referenceService,
                new ArrayList<>());

        MemoryQueryResult result = service.query(command("", "", "请分析故障告警"));

        assertEquals("rendered context", result.context());
        assertEquals(List.of(reference), result.references());
        ArgumentCaptor<MemoryRetrievalQuery> retrievalCaptor =
                ArgumentCaptor.forClass(MemoryRetrievalQuery.class);
        verify(retrievalService).retrieve(retrievalCaptor.capture());
        MemoryRetrievalQuery retrievalQuery = retrievalCaptor.getValue();
        assertEquals(MemorySceneClassificationPolicy.OPS_TROUBLESHOOTING, retrievalQuery.scene());
        assertEquals("demo-project", retrievalQuery.projectId());
        assertEquals(3, retrievalQuery.coldItemLimit());
        assertEquals(4, retrievalQuery.hotMessageLimit());
        assertEquals(5, retrievalQuery.semanticMessageLimit());
        assertEquals(3, retrievalQuery.contextMemoryLimit());
        assertEquals(900L, retrievalQuery.timeoutMillis());
        ArgumentCaptor<MemoryContextRenderingRequest> renderingCaptor =
                ArgumentCaptor.forClass(MemoryContextRenderingRequest.class);
        verify(renderingService).render(renderingCaptor.capture());
        MemoryContextRenderingRequest renderingRequest = renderingCaptor.getValue();
        assertEquals("durable item", renderingRequest.items().get(0).content());
        assertEquals(2, renderingRequest.messages().size());
        assertEquals("semantic message", renderingRequest.messages().get(0).content());
        assertEquals(3, renderingRequest.contextMemoryLimit());
        assertEquals(8, renderingRequest.itemLimit());
        assertEquals(9, renderingRequest.messageLimit());
        assertEquals(3600, renderingRequest.maxChars());
    }

    @Test
    void explicitSceneAndTaskTypePriorityReachRetrievalQuery() {
        MemoryRetrievalApplicationService retrievalService = mock(MemoryRetrievalApplicationService.class);
        when(retrievalService.retrieve(any())).thenReturn(MemoryRetrievalResult.empty());
        MemoryQueryApplicationService service = service(
                retrievalService,
                null,
                null,
                new ArrayList<>());

        service.query(command(" CUSTOM_SCENE ", "OPS_TASK", "故障"));

        ArgumentCaptor<MemoryRetrievalQuery> captor = ArgumentCaptor.forClass(MemoryRetrievalQuery.class);
        verify(retrievalService).retrieve(captor.capture());
        assertEquals("CUSTOM_SCENE", captor.getValue().scene());
    }

    @Test
    void queryFailureIsObservedAndReturnsEmptyResult() {
        MemoryRetrievalApplicationService retrievalService = mock(MemoryRetrievalApplicationService.class);
        when(retrievalService.retrieve(any())).thenThrow(new IllegalStateException("retrieval failed"));
        MemoryContextRenderingApplicationService renderingService =
                mock(MemoryContextRenderingApplicationService.class);
        MemorySelectionReferenceApplicationService referenceService =
                mock(MemorySelectionReferenceApplicationService.class);
        List<String> failures = new ArrayList<>();
        MemoryQueryApplicationService service = service(
                retrievalService,
                renderingService,
                referenceService,
                failures);

        MemoryQueryResult result = service.query(command("", "", "query"));

        assertEquals(MemoryQueryResult.empty(), result);
        assertEquals(List.of("query:retrieval failed"), failures);
        verifyNoInteractions(renderingService, referenceService);
    }

    @Test
    void invalidCommandShortCircuitsAllCollaborators() {
        MemoryRetrievalApplicationService retrievalService = mock(MemoryRetrievalApplicationService.class);
        MemoryContextRenderingApplicationService renderingService =
                mock(MemoryContextRenderingApplicationService.class);
        MemorySelectionReferenceApplicationService referenceService =
                mock(MemorySelectionReferenceApplicationService.class);
        MemoryQueryApplicationService service = service(
                retrievalService,
                renderingService,
                referenceService,
                new ArrayList<>());

        assertEquals(MemoryQueryResult.empty(), service.query(null));
        assertEquals(MemoryQueryResult.empty(), service.query(new MemoryQueryCommand(
                " ", "u1", "query", "", "", "demo-project", 1, 1, 1, 1200, 100, true, 6)));

        verifyNoInteractions(retrievalService, renderingService, referenceService);
    }

    private MemoryQueryApplicationService service(MemoryRetrievalApplicationService retrievalService,
                                                  MemoryContextRenderingApplicationService renderingService,
                                                  MemorySelectionReferenceApplicationService referenceService,
                                                  List<String> failures) {
        return new MemoryQueryApplicationService(
                retrievalService,
                renderingService,
                referenceService,
                new MemorySelectionPolicy(),
                new MemorySceneClassificationPolicy(),
                (operation, error) -> failures.add(operation + ":" + error.getMessage()));
    }

    private MemoryQueryCommand command(String explicitScene, String taskType, String query) {
        return new MemoryQueryCommand(
                "s1",
                "u1",
                query,
                explicitScene,
                taskType,
                "demo-project",
                3,
                4,
                5,
                3600,
                900,
                true,
                6);
    }

    private ColdMemoryItemSnapshot coldItem() {
        return new ColdMemoryItemSnapshot(
                "s1",
                "u1",
                "PROJECT_CONTEXT",
                "durable item",
                BigDecimal.valueOf(0.9),
                "[]",
                "user",
                "source",
                Map.of("turn_index", 1),
                "2026-07-21 18:00:00");
    }

    private MemoryMessageView message(String content, String role, int turn) {
        return new MemoryMessageView(
                "s1",
                "u1",
                role,
                content,
                "2026-07-21 18:00:00",
                Map.of("turn_index", turn));
    }

    private ContextMemoryView contextMemory() {
        return new ContextMemoryView(
                "ctx-1",
                "PROJECT_CONTEXT",
                "PROJECT",
                "demo-project",
                "Architecture",
                "DDD migration",
                "Agent DDD migration",
                "source",
                Map.of());
    }
}
