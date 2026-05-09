package cn.lgs.orbisops.application.rag;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagFeedbackUseCaseTest {

    @Test
    void initializeRespectsTypedAutoInitFlag() {
        RagFeedbackCatalogPort catalog = mock(RagFeedbackCatalogPort.class);
        RagFeedbackEvalCasePort eval = mock(RagFeedbackEvalCasePort.class);

        new RagFeedbackUseCase(catalog, eval, false).initialize();
        verify(catalog, never()).ensureReady();

        new RagFeedbackUseCase(catalog, eval, true).initialize();
        verify(catalog).ensureReady();
    }

    @Test
    void usefulAndResolvedFeedbackDoesNotCreateGap() {
        RagFeedbackCatalogPort catalog = mock(RagFeedbackCatalogPort.class);
        RagFeedbackEvalCasePort eval = mock(RagFeedbackEvalCasePort.class);
        RagFeedbackUseCase useCase = new RagFeedbackUseCase(catalog, eval, false);
        RagFeedbackSubmitCommand command = command(true, true);
        when(catalog.insertFeedback(command)).thenReturn(7L);

        RagFeedbackSubmissionResult result = useCase.submit(command);

        assertEquals(7L, result.id());
        assertFalse(result.gapCreated());
        verify(catalog, never()).upsertGap(command.query(), command.knowledgeTag(), command.comment());
    }

    @Test
    void negativeFeedbackPersistsFeedbackBeforeUpsertingGap() {
        RagFeedbackCatalogPort catalog = mock(RagFeedbackCatalogPort.class);
        RagFeedbackEvalCasePort eval = mock(RagFeedbackEvalCasePort.class);
        RagFeedbackUseCase useCase = new RagFeedbackUseCase(catalog, eval, false);
        RagFeedbackSubmitCommand command = command(false, true);
        RagKnowledgeGap gap = gap(9L);
        when(catalog.insertFeedback(command)).thenReturn(7L);
        when(catalog.upsertGap(command.query(), command.knowledgeTag(), command.comment())).thenReturn(gap);

        RagFeedbackSubmissionResult result = useCase.submit(command);

        InOrder order = inOrder(catalog);
        order.verify(catalog).insertFeedback(command);
        order.verify(catalog).upsertGap(command.query(), command.knowledgeTag(), command.comment());
        assertTrue(result.gapCreated());
        assertEquals(gap, result.gap());
    }

    @Test
    void queriesClampLimitAndStatusUpdateNormalizesValue() {
        RagFeedbackCatalogPort catalog = mock(RagFeedbackCatalogPort.class);
        RagFeedbackEvalCasePort eval = mock(RagFeedbackEvalCasePort.class);
        RagFeedbackUseCase useCase = new RagFeedbackUseCase(catalog, eval, false);
        when(catalog.listFeedback("ops", true, null, 500)).thenReturn(List.of());
        when(catalog.listGaps("open", "ops", 1)).thenReturn(List.of());
        when(catalog.updateGapStatus(3L, "OPEN")).thenReturn(true);
        when(catalog.updateGapStatus(4L, "FIXED")).thenReturn(true);

        useCase.listFeedback(" ops ", true, null, 999);
        useCase.listGaps(" open ", " ops ", 0);

        verify(catalog).listFeedback("ops", true, null, 500);
        verify(catalog).listGaps("open", "ops", 1);
        assertTrue(useCase.updateGapStatus(3L, ""));
        assertTrue(useCase.updateGapStatus(4L, " fixed "));
    }

    @Test
    void promotionSavesEvalCaseBeforeMarkingGapTriaged() {
        RagFeedbackCatalogPort catalog = mock(RagFeedbackCatalogPort.class);
        RagFeedbackEvalCasePort eval = mock(RagFeedbackEvalCasePort.class);
        RagFeedbackUseCase useCase = new RagFeedbackUseCase(catalog, eval, false);
        RagKnowledgeGap gap = gap(9L);
        RagQualityCaseRecord saved = new RagQualityCaseRecord(
                15L, "知识缺口 #9", gap.queryText(), gap.knowledgeTag(), List.of(), 8, true);
        when(catalog.findGap(9L)).thenReturn(gap);
        when(eval.save(org.mockito.ArgumentMatchers.any())).thenReturn(saved);
        when(catalog.updateGapStatus(9L, "TRIAGED")).thenReturn(false);

        RagQualityCaseRecord result = useCase.promoteGapToEvalCase(9L);

        ArgumentCaptor<RagQualityCaseSaveCommand> command = ArgumentCaptor.forClass(RagQualityCaseSaveCommand.class);
        InOrder order = inOrder(catalog, eval);
        order.verify(catalog).findGap(9L);
        order.verify(eval).save(command.capture());
        order.verify(catalog).updateGapStatus(9L, "TRIAGED");
        assertEquals(saved, result);
        assertEquals("知识缺口 #9", command.getValue().caseName());
        assertEquals(gap.queryText(), command.getValue().query());
        assertEquals(gap.knowledgeTag(), command.getValue().knowledgeTag());
        assertEquals(8, command.getValue().topK());
        assertTrue(command.getValue().enabled());
    }

    @Test
    void promotionRejectsMissingGapBeforeCreatingEvalCase() {
        RagFeedbackCatalogPort catalog = mock(RagFeedbackCatalogPort.class);
        RagFeedbackEvalCasePort eval = mock(RagFeedbackEvalCasePort.class);
        RagFeedbackUseCase useCase = new RagFeedbackUseCase(catalog, eval, false);
        when(catalog.findGap(404L)).thenReturn(null);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> useCase.promoteGapToEvalCase(404L));

        assertEquals("知识缺口不存在：404", error.getMessage());
        verify(eval, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private RagFeedbackSubmitCommand command(Boolean useful, Boolean resolved) {
        return new RagFeedbackSubmitCommand(
                "为什么检索不到",
                "旧答案",
                useful,
                resolved,
                "chat",
                "message-1",
                "ops",
                List.of("chunk-1"),
                "缺少文档");
    }

    private RagKnowledgeGap gap(Long id) {
        return new RagKnowledgeGap(
                id,
                "gap-key",
                "为什么检索不到",
                "ops",
                "OPEN",
                2,
                "缺少文档",
                "2026-07-30 10:00:00",
                "",
                "");
    }
}
