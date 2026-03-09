package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.application.rag.RagOrderDefinition;
import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientRagOrderConfigRepository;
import cn.lgs.orbisops.domain.agent.model.entity.AiClientConfigRecord;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsRagOrderCatalogAdapterTest {

    @Test
    void mapsNarrowApplicationDefinitionToLegacyRepositoryRecord() {
        IAiClientRagOrderConfigRepository repository = mock(IAiClientRagOrderConfigRepository.class);
        OpsConfigAuditService auditService = mock(OpsConfigAuditService.class);
        OpsRagOrderCatalogAdapter adapter = new OpsRagOrderCatalogAdapter(repository, auditService);
        RagOrderDefinition definition = new RagOrderDefinition(
                7L,
                "rag-1",
                "运维知识库",
                "ops",
                1,
                LocalDateTime.of(2026, 7, 30, 10, 0),
                LocalDateTime.of(2026, 7, 30, 10, 0));
        when(repository.insert(any())).thenReturn(1);

        assertTrue(adapter.insert(definition));
        adapter.created(definition);

        ArgumentCaptor<AiClientConfigRecord> recordCaptor = ArgumentCaptor.forClass(AiClientConfigRecord.class);
        verify(repository).insert(recordCaptor.capture());
        AiClientConfigRecord record = recordCaptor.getValue();
        assertEquals(7L, record.getId());
        assertEquals("rag-1", record.getRagId());
        assertEquals("运维知识库", record.getRagName());
        assertEquals("ops", record.getKnowledgeTag());
        assertEquals(1, record.getStatus());
        assertEquals(definition.createTime(), record.getCreateTime());
        verify(auditService).record(
                eq("rag-order"),
                eq("create"),
                eq("rag-1"),
                eq(null),
                any(AiClientConfigRecord.class));
    }

    @Test
    void mapsLegacyRepositoryRowsToImmutableApplicationDefinitions() {
        IAiClientRagOrderConfigRepository repository = mock(IAiClientRagOrderConfigRepository.class);
        OpsConfigAuditService auditService = mock(OpsConfigAuditService.class);
        OpsRagOrderCatalogAdapter adapter = new OpsRagOrderCatalogAdapter(repository, auditService);
        AiClientConfigRecord record = AiClientConfigRecord.builder()
                .id(7L)
                .ragId("rag-1")
                .ragName("运维知识库")
                .knowledgeTag("ops")
                .status(1)
                .createTime(LocalDateTime.of(2026, 7, 1, 0, 0))
                .updateTime(LocalDateTime.of(2026, 7, 2, 0, 0))
                .build();
        when(repository.queryEnabledRagOrders()).thenReturn(List.of(record));

        List<RagOrderDefinition> definitions = adapter.queryEnabled();

        assertEquals(1, definitions.size());
        assertEquals(7L, definitions.get(0).id());
        assertEquals("rag-1", definitions.get(0).ragId());
        assertEquals(record.getCreateTime(), definitions.get(0).createTime());
    }
}
