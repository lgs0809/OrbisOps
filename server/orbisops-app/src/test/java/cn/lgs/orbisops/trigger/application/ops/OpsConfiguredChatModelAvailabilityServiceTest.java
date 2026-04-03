package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.application.config.AiClientApiCatalogPort;
import cn.lgs.orbisops.application.config.AiClientApiDefinition;
import cn.lgs.orbisops.application.config.AiClientModelCatalogPort;
import cn.lgs.orbisops.application.config.AiClientModelDefinition;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsConfiguredChatModelAvailabilityServiceTest {

    @Test
    void enabledModelWithEnabledApiAndResolvedSecretMustBeAvailable() {
        AiClientModelCatalogPort models = mock(AiClientModelCatalogPort.class);
        AiClientApiCatalogPort apis = mock(AiClientApiCatalogPort.class);
        when(models.listEnabled()).thenReturn(List.of(model("9001", "gpt-test", "1001")));
        when(apis.findByApiId("1001")).thenReturn(api("1001", "${env:OPS_TEST_MODEL_KEY}"));
        MockEnvironment environment = new MockEnvironment().withProperty("OPS_TEST_MODEL_KEY", "real-model-secret-value");

        Map<String, Object> status = new OpsConfiguredChatModelAvailabilityService(
                models, apis, new OpsSecretResolver(environment)).status();

        assertTrue(Boolean.TRUE.equals(status.get("configuredModelAvailable")));
        assertEquals(1, status.get("enabledConfiguredModelCount"));
        assertEquals(1, status.get("usableConfiguredModelCount"));
    }

    @Test
    void unresolvedSecretReferenceMustRemainUnavailable() {
        AiClientModelCatalogPort models = mock(AiClientModelCatalogPort.class);
        AiClientApiCatalogPort apis = mock(AiClientApiCatalogPort.class);
        when(models.listEnabled()).thenReturn(List.of(model("9001", "gpt-test", "1001")));
        when(apis.findByApiId("1001")).thenReturn(api("1001", "${env:MISSING_MODEL_KEY}"));

        boolean available = new OpsConfiguredChatModelAvailabilityService(
                models, apis, new OpsSecretResolver(new MockEnvironment())).anyAvailable();

        assertFalse(available);
    }

    @Test
    void placeholderSecretMustRemainUnavailable() {
        AiClientModelCatalogPort models = mock(AiClientModelCatalogPort.class);
        AiClientApiCatalogPort apis = mock(AiClientApiCatalogPort.class);
        when(models.listEnabled()).thenReturn(List.of(model("9001", "gpt-test", "1001")));
        when(apis.findByApiId("1001")).thenReturn(api("1001", "dev-placeholder-key"));

        boolean available = new OpsConfiguredChatModelAvailabilityService(
                models, apis, new OpsSecretResolver(new MockEnvironment())).anyAvailable();

        assertFalse(available);
    }

    @Test
    void repositoryFailureMustReportUnavailableWithoutLeakingInfrastructureError() {
        AiClientModelCatalogPort models = mock(AiClientModelCatalogPort.class);
        when(models.listEnabled()).thenThrow(new IllegalStateException("database unavailable"));

        Map<String, Object> status = new OpsConfiguredChatModelAvailabilityService(
                models,
                mock(AiClientApiCatalogPort.class),
                new OpsSecretResolver(new MockEnvironment())).status();

        assertFalse(Boolean.TRUE.equals(status.get("configuredModelAvailable")));
        assertEquals(0, status.get("enabledConfiguredModelCount"));
        assertEquals(0, status.get("usableConfiguredModelCount"));
    }

    private AiClientModelDefinition model(String modelId, String modelName, String apiId) {
        return new AiClientModelDefinition(
                null, modelId, apiId, modelName, "CHAT", "CHAT", "", 1, null, null);
    }

    private AiClientApiDefinition api(String apiId, String apiKey) {
        return new AiClientApiDefinition(
                null, apiId, "test-provider", "OPENAI_COMPATIBLE",
                "https://example.invalid", apiKey, "v1/chat/completions", "", 1, null, null);
    }
}
