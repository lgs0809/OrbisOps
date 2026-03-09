package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.AiClientApiCatalogUseCase;
import cn.lgs.orbisops.application.config.AiClientApiDefinition;
import cn.lgs.orbisops.application.config.AiClientModelSyncTarget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsAiClientModelSyncTargetAdapterTest {

    @Test
    void resolvesTypedProviderTargetThroughApiCatalog() {
        AiClientApiCatalogUseCase catalog = mock(AiClientApiCatalogUseCase.class);
        OpsAiClientModelSyncTargetAdapter adapter = new OpsAiClientModelSyncTargetAdapter(catalog);
        when(catalog.findByApiId("openai-main")).thenReturn(new AiClientApiDefinition(
                1L,
                "openai-main",
                "OpenAI Main",
                "OPENAI_COMPATIBLE",
                "https://provider.example/v1",
                "sk-test",
                "/chat/completions",
                "/embeddings",
                1,
                null,
                null));

        AiClientModelSyncTarget target = adapter.find("openai-main");

        assertEquals("openai-main", target.apiId());
        assertEquals("https://provider.example/v1", target.baseUrl());
        assertEquals("sk-test", target.apiKey());
        verify(catalog).findByApiId("openai-main");
    }

    @Test
    void missingProviderReturnsNull() {
        AiClientApiCatalogUseCase catalog = mock(AiClientApiCatalogUseCase.class);
        OpsAiClientModelSyncTargetAdapter adapter = new OpsAiClientModelSyncTargetAdapter(catalog);
        when(catalog.findByApiId("missing")).thenReturn(null);

        assertNull(adapter.find("missing"));
    }
}
