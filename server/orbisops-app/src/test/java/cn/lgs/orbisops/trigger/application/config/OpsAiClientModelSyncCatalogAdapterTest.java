package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.AiClientModelCatalogPort;
import cn.lgs.orbisops.application.config.AiClientModelDefinition;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsAiClientModelSyncCatalogAdapterTest {

    @Test
    void delegatesTypedModelLookupAndMutationsWithoutLegacyRecordProjection() {
        AiClientModelCatalogPort catalog = mock(AiClientModelCatalogPort.class);
        OpsAiClientModelSyncCatalogAdapter adapter = new OpsAiClientModelSyncCatalogAdapter(catalog);
        LocalDateTime now = LocalDateTime.of(2026, 7, 30, 7, 0);
        AiClientModelDefinition definition = new AiClientModelDefinition(
                8L, "qwen3-vl-8b", "openai-main", "Qwen VL", "VISION", "VISION",
                "synced", 1, now, now);
        when(catalog.findByModelId("qwen3-vl-8b")).thenReturn(definition);
        when(catalog.insert(definition)).thenReturn(true);
        when(catalog.updateByModelId(definition)).thenReturn(false);

        assertEquals(definition, adapter.findByModelId("qwen3-vl-8b"));
        assertTrue(adapter.insert(definition));
        assertFalse(adapter.updateByModelId(definition));

        verify(catalog).findByModelId("qwen3-vl-8b");
        verify(catalog).insert(definition);
        verify(catalog).updateByModelId(definition);
    }
}
