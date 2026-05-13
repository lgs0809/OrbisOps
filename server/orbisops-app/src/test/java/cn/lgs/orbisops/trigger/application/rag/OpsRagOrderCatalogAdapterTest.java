package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.application.rag.RagOrderDefinition;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OpsRagOrderCatalogAdapterTest {

    @Test
    void auditsTypedKnowledgeBaseDefinitionsWithoutLegacyRepositoryProjection() {
        OpsConfigAuditService auditService = mock(OpsConfigAuditService.class);
        OpsRagOrderCatalogAdapter adapter = new OpsRagOrderCatalogAdapter(auditService);
        RagOrderDefinition before = definition("旧知识库");
        RagOrderDefinition after = definition("新知识库");

        adapter.created(after);
        adapter.updated("update-by-rag-id", "rag-1", before, after);
        adapter.deleted("delete-by-rag-id", "rag-1", before, true);

        verify(auditService).record(
                eq("rag-order"),
                eq("create"),
                eq("rag-1"),
                eq(null),
                eq(after));
        verify(auditService).record(
                eq("rag-order"),
                eq("update-by-rag-id"),
                eq("rag-1"),
                argThat(value -> value instanceof RagOrderDefinition definition
                        && "旧知识库".equals(definition.ragName())),
                argThat(value -> value instanceof RagOrderDefinition definition
                        && "新知识库".equals(definition.ragName())));
        verify(auditService).record(
                "rag-order",
                "delete-by-rag-id",
                "rag-1",
                before,
                Map.of("deleted", true));
    }

    private RagOrderDefinition definition(String name) {
        LocalDateTime time = LocalDateTime.of(2026, 7, 30, 10, 0);
        return new RagOrderDefinition(7L, "rag-1", name, "ops", 1, time, time);
    }
}
