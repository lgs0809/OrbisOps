package cn.lgs.orbisops.application.rag;

import cn.lgs.orbisops.domain.rageval.service.RagQualityAssessmentPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagQualityProbeUseCaseTest {

    @Test
    void retrievesAssessesAndPairsScoresWithStableRanks() {
        @SuppressWarnings("unchecked")
        RagQualityRetrievalPort<String> port = mock(RagQualityRetrievalPort.class);
        RagQualityProbeUseCase<String> useCase = new RagQualityProbeUseCase<>(
                port,
                new RagQualityAssessmentPolicy());
        RagQualityProbeCommand command = new RagQualityProbeCommand(
                "订单超时",
                "ops",
                List.of("traceId", "timeout"),
                8,
                "hybrid",
                true);
        List<RagQualityRetrievalHit<String>> hits = List.of(
                new RagQualityRetrievalHit<>(
                        "chunk-1", "runbook.md", "ops", "markdown", "section",
                        "订单超时 traceId timeout", "m1"),
                new RagQualityRetrievalHit<>(
                        "chunk-2", "faq.md", "ops", "markdown", "paragraph",
                        "其他内容", "m2"));
        when(port.retrieve(command)).thenReturn(hits);

        RagQualityProbeResult<String> result = useCase.probe(command);

        assertEquals(2, result.hits().size());
        assertEquals(1, result.hits().get(0).rank());
        assertEquals(4D, result.hits().get(0).score());
        assertEquals(2, result.hits().get(1).rank());
        assertEquals(0D, result.hits().get(1).score());
        assertTrue(result.assessment().passed());
        verify(port).retrieve(command);
    }

    @Test
    void commandNormalizesDefaultsBoundsAndKeywords() {
        RagQualityProbeCommand command = new RagQualityProbeCommand(
                " query ",
                " tag ",
                List.of(" a ", "a", "", "b"),
                99,
                " ",
                false);

        assertEquals("query", command.query());
        assertEquals("tag", command.knowledgeTag());
        assertEquals(List.of("a", "b"), command.expectedKeywords());
        assertEquals(30, command.topK());
        assertEquals("hybrid", command.retrievalMode());
    }

    @Test
    void missingQueryPreservesCompatibilityError() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new RagQualityProbeCommand("", "", List.of(), 8, "hybrid", true));

        assertEquals("query 不能为空", error.getMessage());
    }
}
