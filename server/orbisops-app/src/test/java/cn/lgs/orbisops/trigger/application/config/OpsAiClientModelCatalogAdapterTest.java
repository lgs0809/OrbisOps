package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.AiClientModelDefinition;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OpsAiClientModelCatalogAdapterTest {

    @Test
    void auditsTypedModelDefinitionsWithoutLegacyWideEntity() {
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        OpsAiClientModelCatalogAdapter adapter = new OpsAiClientModelCatalogAdapter(audit);
        AiClientModelDefinition before = new AiClientModelDefinition(
                7L, "gpt-main", "openai-main", "Old", "CHAT", "CHAT", "old", 1, null, null);
        AiClientModelDefinition after = new AiClientModelDefinition(
                7L, "gpt-main", "openai-main", "New", "CHAT", "CHAT", "new", 1, null, null);

        adapter.updatedByModelId("gpt-main", before, after);
        adapter.deletedByModelId("gpt-main", before);

        verify(audit).record(
                org.mockito.ArgumentMatchers.eq("model-config"),
                org.mockito.ArgumentMatchers.eq("update-by-model-id"),
                org.mockito.ArgumentMatchers.eq("gpt-main"),
                argThat(value -> value instanceof AiClientModelDefinition definition
                        && "Old".equals(definition.modelName())),
                argThat(value -> value instanceof AiClientModelDefinition definition
                        && "New".equals(definition.modelName())));
        verify(audit).record(
                "model-config",
                "delete-by-model-id",
                "gpt-main",
                before,
                Map.of("deleted", true));
    }
}
