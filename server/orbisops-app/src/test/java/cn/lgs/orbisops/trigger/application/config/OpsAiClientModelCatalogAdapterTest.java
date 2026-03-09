package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.AiClientModelDefinition;
import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientModelConfigRepository;
import cn.lgs.orbisops.domain.agent.model.entity.AiClientConfigRecord;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsAiClientModelCatalogAdapterTest {

    @Test
    void mapsTypedDefinitionToLegacyRepositoryAndBack() {
        IAiClientModelConfigRepository repository = mock(IAiClientModelConfigRepository.class);
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        OpsAiClientModelCatalogAdapter adapter = new OpsAiClientModelCatalogAdapter(repository, audit);
        LocalDateTime now = LocalDateTime.of(2026, 7, 30, 5, 0);
        AiClientModelDefinition definition = new AiClientModelDefinition(
                7L, "gpt-main", "openai-main", "GPT Main", "CHAT", "CHAT",
                "primary", 1, now, now);
        when(repository.insert(org.mockito.ArgumentMatchers.any())).thenReturn(1);
        when(repository.queryByApiId("openai-main")).thenReturn(List.of(
                AiClientConfigRecord.builder()
                        .id(7L)
                        .modelId("gpt-main")
                        .apiId("openai-main")
                        .modelName("GPT Main")
                        .modelType("CHAT")
                        .modelUsage("CHAT")
                        .description("primary")
                        .status(1)
                        .createTime(now)
                        .updateTime(now)
                        .build()));

        assertTrue(adapter.insert(definition));
        List<AiClientModelDefinition> values = adapter.findByApiId("openai-main");

        ArgumentCaptor<AiClientConfigRecord> saved = ArgumentCaptor.forClass(AiClientConfigRecord.class);
        verify(repository).insert(saved.capture());
        assertEquals("gpt-main", saved.getValue().getModelId());
        assertEquals("CHAT", saved.getValue().getModelUsage());
        assertEquals(List.of(definition), values);
    }

    @Test
    void projectsAuditSnapshotsThroughLegacyWideEntity() {
        IAiClientModelConfigRepository repository = mock(IAiClientModelConfigRepository.class);
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        OpsAiClientModelCatalogAdapter adapter = new OpsAiClientModelCatalogAdapter(repository, audit);
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
                org.mockito.ArgumentMatchers.argThat(value -> value instanceof AiClientConfigRecord
                        && "Old".equals(((AiClientConfigRecord) value).getModelName())),
                org.mockito.ArgumentMatchers.argThat(value -> value instanceof AiClientConfigRecord
                        && "New".equals(((AiClientConfigRecord) value).getModelName())));
        verify(audit).record(
                "model-config",
                "delete-by-model-id",
                "gpt-main",
                AiClientConfigRecord.builder()
                        .id(7L)
                        .modelId("gpt-main")
                        .apiId("openai-main")
                        .modelName("Old")
                        .modelType("CHAT")
                        .modelUsage("CHAT")
                        .description("old")
                        .status(1)
                        .build(),
                Map.of("deleted", true));
    }
}
