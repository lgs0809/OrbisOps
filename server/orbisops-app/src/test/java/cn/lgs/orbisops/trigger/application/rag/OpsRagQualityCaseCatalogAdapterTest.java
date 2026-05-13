package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.application.rag.RagQualityCaseRecord;
import cn.lgs.orbisops.application.rag.RagQualityCaseSaveCommand;
import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagEvalRepository;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsRagQualityCaseCatalogAdapterTest {

    @Test
    void mapsLegacyRepositoryRowsIntoTypedRecords() {
        IRagEvalRepository repository = mock(IRagEvalRepository.class);
        OpsRagQualityCaseCatalogAdapter adapter = new OpsRagQualityCaseCatalogAdapter(repository);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", 7L);
        row.put("case_name", "订单超时");
        row.put("query_text", "订单超时");
        row.put("knowledge_tag", "ops");
        row.put("expected_keywords_json", "[\"traceId\",\"timeout\"]");
        row.put("top_k", 12);
        row.put("enabled", 0);
        when(repository.listCases(false, 20)).thenReturn(List.of(row));

        List<RagQualityCaseRecord> records = adapter.list(false, 20);

        assertEquals(1, records.size());
        RagQualityCaseRecord record = records.get(0);
        assertEquals(7L, record.id());
        assertEquals("订单超时", record.caseName());
        assertEquals("订单超时", record.query());
        assertEquals("ops", record.knowledgeTag());
        assertEquals(List.of("traceId", "timeout"), record.expectedKeywords());
        assertEquals(12, record.topK());
        assertFalse(record.enabled());
    }

    @Test
    void serializesTypedSaveCommandOnlyInsideRepositoryAdapter() {
        IRagEvalRepository repository = mock(IRagEvalRepository.class);
        OpsRagQualityCaseCatalogAdapter adapter = new OpsRagQualityCaseCatalogAdapter(repository);
        RagQualityCaseSaveCommand command = new RagQualityCaseSaveCommand(
                7L,
                "订单超时",
                "订单超时",
                "ops",
                List.of("traceId", "timeout"),
                8,
                true);
        when(repository.saveCase(
                7L,
                "订单超时",
                "订单超时",
                "ops",
                "[\"traceId\",\"timeout\"]",
                8,
                true)).thenReturn(9L);

        RagQualityCaseRecord saved = adapter.save(command);

        assertEquals(9L, saved.id());
        assertEquals(command.caseName(), saved.caseName());
        assertEquals(command.expectedKeywords(), saved.expectedKeywords());
        verify(repository).saveCase(
                7L,
                "订单超时",
                "订单超时",
                "ops",
                "[\"traceId\",\"timeout\"]",
                8,
                true);
    }
}
