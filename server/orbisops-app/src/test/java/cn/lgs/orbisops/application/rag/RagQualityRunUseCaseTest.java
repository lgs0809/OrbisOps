package cn.lgs.orbisops.application.rag;

import cn.lgs.orbisops.domain.rageval.service.RagQualityAssessmentPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagQualityRunUseCaseTest {

    @Test
    void probesCasesAggregatesMetricsAndPersistsTypedResult() {
        @SuppressWarnings("unchecked")
        RagQualityRetrievalPort<String> retrievalPort = mock(RagQualityRetrievalPort.class);
        @SuppressWarnings("unchecked")
        RagQualityRunPersistencePort<String> persistencePort = mock(RagQualityRunPersistencePort.class);
        RagQualityProbeUseCase<String> probeUseCase = new RagQualityProbeUseCase<>(
                retrievalPort,
                new RagQualityAssessmentPolicy());
        RagQualityRunUseCase<String> useCase = new RagQualityRunUseCase<>(
                probeUseCase,
                new RagQualityAssessmentPolicy(),
                persistencePort);
        RagQualityRunCommand command = new RagQualityRunCommand(
                List.of(
                        new RagQualityEvalCase(
                                "订单超时",
                                "订单超时",
                                "ops",
                                List.of("traceId", "timeout"),
                                8),
                        new RagQualityEvalCase(
                                "支付失败",
                                "支付失败",
                                "ops",
                                List.of("errorCode"),
                                5)),
                "hybrid",
                true);
        when(retrievalPort.retrieve(any())).thenAnswer(invocation -> {
            RagQualityProbeCommand probeCommand = invocation.getArgument(0);
            if ("订单超时".equals(probeCommand.query())) {
                return List.of(new RagQualityRetrievalHit<>(
                        "chunk-1",
                        "runbook.md",
                        "ops",
                        "markdown",
                        "section",
                        "订单超时 traceId timeout",
                        "metadata"));
            }
            return List.of();
        });

        RagQualityRunResult<String> result = useCase.run(command);

        assertEquals(2, result.assessment().caseCount());
        assertEquals(0.5D, result.assessment().hitRate());
        assertEquals(0.5D, result.assessment().averageKeywordCoverage());
        assertEquals(0.5D, result.assessment().meanReciprocalRank());
        assertEquals(1, result.assessment().passedCount());
        assertEquals(2, result.caseResults().size());
        assertEquals("订单超时", result.caseResults().get(0).evalCase().caseName());
        assertEquals("支付失败", result.caseResults().get(1).evalCase().caseName());

        ArgumentCaptor<RagQualityRunResult<String>> resultCaptor = ArgumentCaptor.forClass(RagQualityRunResult.class);
        verify(persistencePort).save(resultCaptor.capture());
        assertEquals(result, resultCaptor.getValue());

        InOrder order = inOrder(retrievalPort, persistencePort);
        order.verify(retrievalPort, times(2)).retrieve(any());
        order.verify(persistencePort).save(any());
    }

    @Test
    void emptyCasesPreserveCompatibilityError() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new RagQualityRunCommand(List.of(), "hybrid", true));

        assertEquals(
                "没有可执行的 RAG 评测用例，请先创建启用状态的用例，或在请求体传入 cases。",
                error.getMessage());
    }
}
