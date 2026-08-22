package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.application.config.AiClientApiCatalogUseCase;
import cn.lgs.orbisops.application.config.AiClientApiDefinition;
import cn.lgs.orbisops.application.config.AiClientModelSyncTarget;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
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
        OpsSecretResolver secretResolver = mock(OpsSecretResolver.class);
        OpsAiClientModelSyncTargetAdapter adapter = new OpsAiClientModelSyncTargetAdapter(catalog, secretResolver);
        when(catalog.findByApiId("openai-main")).thenReturn(new AiClientApiDefinition(
                1L,
                "openai-main",
                "OpenAI Main",
                "OPENAI_COMPATIBLE",
                "https://provider.example/v1",
                "${env:OPENAI_API_KEY:}",
                "/chat/completions",
                "/embeddings",
                1,
                null,
                null));
        when(secretResolver.resolve("${env:OPENAI_API_KEY:}")).thenReturn("test-api-key");

        AiClientModelSyncTarget target = adapter.find("openai-main");

        assertEquals("openai-main", target.apiId());
        assertEquals("https://provider.example/v1", target.baseUrl());
        assertEquals("test-api-key", target.apiKey());
        verify(catalog).findByApiId("openai-main");
        verify(secretResolver).resolve("${env:OPENAI_API_KEY:}");
    }

    @Test
    void missingProviderReturnsNull() {
        AiClientApiCatalogUseCase catalog = mock(AiClientApiCatalogUseCase.class);
        OpsSecretResolver secretResolver = mock(OpsSecretResolver.class);
        OpsAiClientModelSyncTargetAdapter adapter = new OpsAiClientModelSyncTargetAdapter(catalog, secretResolver);
        when(catalog.findByApiId("missing")).thenReturn(null);

        assertNull(adapter.find("missing"));
    }
}
