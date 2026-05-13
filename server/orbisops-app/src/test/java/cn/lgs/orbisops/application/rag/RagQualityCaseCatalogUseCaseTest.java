package cn.lgs.orbisops.application.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagQualityCaseCatalogUseCaseTest {

    @Test
    void coordinatesInitializationCrudAndEnabledCaseProjection() {
        RagQualityCaseCatalogPort port = mock(RagQualityCaseCatalogPort.class);
        RagQualityCaseCatalogUseCase useCase = new RagQualityCaseCatalogUseCase(port);
        RagQualityCaseRecord record = new RagQualityCaseRecord(
                7L,
                "订单超时",
                "订单超时",
                "ops",
                List.of("traceId", "timeout"),
                8,
                true);
        RagQualityCaseSaveCommand command = new RagQualityCaseSaveCommand(
                7L,
                "订单超时",
                "订单超时",
                "ops",
                List.of("traceId", "timeout"),
                8,
                true);
        when(port.list(true, 20)).thenReturn(List.of(record));
        when(port.save(command)).thenReturn(record);
        when(port.delete(7L)).thenReturn(true);
        when(port.listEnabled(200)).thenReturn(List.of(record));

        useCase.initialize();
        List<RagQualityCaseRecord> records = useCase.list(true, 20);
        RagQualityCaseRecord saved = useCase.save(command);
        boolean deleted = useCase.delete(7L);
        List<RagQualityEvalCase> enabledCases = useCase.enabledCases(200);

        assertEquals(List.of(record), records);
        assertEquals(record, saved);
        assertTrue(deleted);
        assertEquals(1, enabledCases.size());
        assertEquals("订单超时", enabledCases.get(0).caseName());
        assertEquals(List.of("traceId", "timeout"), enabledCases.get(0).expectedKeywords());

        verify(port, times(5)).ensureReady();
        verify(port).list(true, 20);
        verify(port).save(command);
        verify(port).delete(7L);
        verify(port).listEnabled(200);
    }

    @Test
    void saveCommandReusesEvalCaseNormalizationAndValidation() {
        RagQualityCaseSaveCommand command = new RagQualityCaseSaveCommand(
                null,
                " ",
                " query ",
                " tag ",
                List.of(" a ", "a", "", "b"),
                99,
                true);

        assertEquals("query", command.caseName());
        assertEquals("query", command.query());
        assertEquals("tag", command.knowledgeTag());
        assertEquals(List.of("a", "b"), command.expectedKeywords());
        assertEquals(30, command.topK());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new RagQualityCaseSaveCommand(
                        null,
                        "",
                        "",
                        "",
                        List.of(),
                        8,
                        true));
        assertEquals("query 不能为空", error.getMessage());
    }
}
