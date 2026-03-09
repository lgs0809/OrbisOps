package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.AiClientModelDefinition;
import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientModelConfigRepository;
import cn.lgs.orbisops.domain.agent.model.entity.AiClientConfigRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsAiClientModelSyncCatalogAdapterTest {

    @Test
    void mapsLegacyRecordToTypedDefinition() {
        IAiClientModelConfigRepository repository = mock(IAiClientModelConfigRepository.class);
        OpsAiClientModelSyncCatalogAdapter adapter = new OpsAiClientModelSyncCatalogAdapter(repository);
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 2, 3, 4);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 2, 3, 4, 5);
        when(repository.queryByModelId("gpt-main")).thenReturn(AiClientConfigRecord.builder()
                .id(7L)
                .modelId("gpt-main")
                .apiId("openai-main")
                .modelName("GPT Main")
                .modelType("CHAT")
                .modelUsage("CHAT")
                .description("primary")
                .status(1)
                .createTime(createdAt)
                .updateTime(updatedAt)
                .build());

        AiClientModelDefinition result = adapter.findByModelId("gpt-main");

        assertEquals(new AiClientModelDefinition(
                7L, "gpt-main", "openai-main", "GPT Main", "CHAT", "CHAT",
                "primary", 1, createdAt, updatedAt), result);
    }

    @Test
    void mapsTypedDefinitionToInsertAndUpdateWithoutCrudAudit() {
        IAiClientModelConfigRepository repository = mock(IAiClientModelConfigRepository.class);
        OpsAiClientModelSyncCatalogAdapter adapter = new OpsAiClientModelSyncCatalogAdapter(repository);
        LocalDateTime now = LocalDateTime.of(2026, 7, 30, 7, 0);
        AiClientModelDefinition definition = new AiClientModelDefinition(
                8L, "qwen3-vl-8b", "openai-main", "Qwen VL", "VISION", "VISION",
                "synced", 1, now, now);
        when(repository.insert(org.mockito.ArgumentMatchers.any())).thenReturn(1);
        when(repository.updateByModelId(org.mockito.ArgumentMatchers.any())).thenReturn(0);

        assertTrue(adapter.insert(definition));
        assertFalse(adapter.updateByModelId(definition));

        ArgumentCaptor<AiClientConfigRecord> records = ArgumentCaptor.forClass(AiClientConfigRecord.class);
        verify(repository).insert(records.capture());
        verify(repository).updateByModelId(records.capture());
        for (AiClientConfigRecord record : records.getAllValues()) {
            assertEquals(8L, record.getId());
            assertEquals("qwen3-vl-8b", record.getModelId());
            assertEquals("openai-main", record.getApiId());
            assertEquals("VISION", record.getModelUsage());
            assertEquals(now, record.getCreateTime());
            assertEquals(now, record.getUpdateTime());
        }
    }
}
