package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.application.rag.RagFeedbackSubmitCommand;
import cn.lgs.orbisops.application.rag.RagFeedbackSubmissionResult;
import cn.lgs.orbisops.application.rag.RagFeedbackUseCase;
import cn.lgs.orbisops.application.rag.RagKnowledgeGap;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagFeedbackServiceTest {

    @Test
    void ensureTablesDelegatesToTypedUseCase() {
        RagFeedbackUseCase useCase = mock(RagFeedbackUseCase.class);
        RagFeedbackService service = service(useCase);

        service.ensureTables();

        verify(useCase).initialize();
    }

    @Test
    void submitParsesLegacyMapAndProjectsTypedResult() {
        RagFeedbackUseCase useCase = mock(RagFeedbackUseCase.class);
        RagFeedbackService service = service(useCase);
        RagKnowledgeGap gap = new RagKnowledgeGap(
                9L, "gap-key", "为什么检索不到", "ops", "OPEN", 2,
                "缺少文档", "2026-07-30 10:00:00", "", "");
        when(useCase.submit(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new RagFeedbackSubmissionResult(7L, true, gap));

        Map<String, Object> result = service.submitFeedback(Map.of(
                "query", " 为什么检索不到 ",
                "answer", "旧答案",
                "useful", "0",
                "resolved", "yes",
                "sourceType", "chat",
                "sourceId", "message-1",
                "knowledgeTag", "ops",
                "chunkIds", List.of("chunk-1", "chunk-2"),
                "comment", "缺少文档"));

        ArgumentCaptor<RagFeedbackSubmitCommand> captor = ArgumentCaptor.forClass(RagFeedbackSubmitCommand.class);
        verify(useCase).submit(captor.capture());
        RagFeedbackSubmitCommand command = captor.getValue();
        assertEquals("为什么检索不到", command.query());
        assertFalse(command.useful());
        assertEquals(Boolean.TRUE, command.resolved());
        assertEquals(List.of("chunk-1", "chunk-2"), command.chunkIds());
        assertEquals(7L, result.get("id"));
        assertEquals(true, result.get("gapCreated"));
        @SuppressWarnings("unchecked")
        Map<String, Object> gapView = (Map<String, Object>) result.get("gap");
        assertEquals("gap-key", gapView.get("gapKey"));
    }

    private RagFeedbackService service(RagFeedbackUseCase useCase) {
        return new RagFeedbackService(new OpsRagFeedbackManagementAssembly(
                useCase,
                new OpsRagFeedbackViewMapper()));
    }
}
