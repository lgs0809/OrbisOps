package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.api.dto.AiClientApiHealthCheckResponseDTO;
import cn.lgs.orbisops.api.dto.AiClientApiRequestDTO;
import cn.lgs.orbisops.api.dto.AiClientApiResponseDTO;
import cn.lgs.orbisops.application.config.AiClientApiCatalogUseCase;
import cn.lgs.orbisops.application.config.AiClientApiDefinition;
import cn.lgs.orbisops.application.config.AiClientApiHealthCheckResult;
import cn.lgs.orbisops.application.config.AiClientApiHealthCheckUseCase;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiClientApiApplicationServiceTest {

    @Test
    void shouldMaskApiKeyInAdminResponse() {
        AiClientApiCatalogUseCase catalog = mock(AiClientApiCatalogUseCase.class);
        AiClientApiHealthCheckUseCase health = mock(AiClientApiHealthCheckUseCase.class);
        AiClientApiApplicationService service = new AiClientApiApplicationService(catalog, health);
        when(catalog.findByApiId("openai-main"))
                .thenReturn(new AiClientApiDefinition(
                        1L,
                        "openai-main",
                        "OpenAI",
                        "OPENAI_COMPATIBLE",
                        "https://example.test/v1",
                        "configured-api-key",
                        null,
                        null,
                        1,
                        null,
                        null));

        AiClientApiResponseDTO response = service.queryByApiId("openai-main");

        assertEquals("******", response.getApiKey());
    }

    @Test
    void shouldDelegateFailedUpdateToTypedCatalog() {
        AiClientApiCatalogUseCase catalog = mock(AiClientApiCatalogUseCase.class);
        AiClientApiApplicationService service = new AiClientApiApplicationService(
                catalog,
                mock(AiClientApiHealthCheckUseCase.class));
        AiClientApiRequestDTO request = new AiClientApiRequestDTO();
        request.setId(404L);
        request.setApiId("missing-api");
        request.setBaseUrl("https://example.test/v1");
        request.setApiKey("new-value");
        when(catalog.updateById(any())).thenReturn(false);

        boolean updated = service.updateById(request);

        assertFalse(updated);
        ArgumentCaptor<AiClientApiDefinition> command = ArgumentCaptor.forClass(AiClientApiDefinition.class);
        verify(catalog).updateById(command.capture());
        assertEquals(404L, command.getValue().id());
        assertEquals("missing-api", command.getValue().apiId());
    }

    @Test
    void shouldProjectTypedHealthCheckResultToAdminResponse() {
        AiClientApiCatalogUseCase catalog = mock(AiClientApiCatalogUseCase.class);
        AiClientApiHealthCheckUseCase health = mock(AiClientApiHealthCheckUseCase.class);
        AiClientApiApplicationService service = new AiClientApiApplicationService(catalog, health);
        LocalDateTime checkedAt = LocalDateTime.of(2026, 7, 30, 3, 0);
        when(health.check("local-provider")).thenReturn(new AiClientApiHealthCheckResult(
                "local-provider",
                "MODELS_ENDPOINT",
                "http://127.0.0.1:8080/v1/models",
                "SUCCESS",
                200,
                12L,
                "",
                "admin-1",
                checkedAt,
                checkedAt));

        AiClientApiHealthCheckResponseDTO result = service.healthCheck("local-provider");

        assertEquals("SUCCESS", result.getStatus());
        assertEquals(200, result.getHttpStatus());
        assertEquals("http://127.0.0.1:8080/v1/models", result.getEndpoint());
        assertEquals("admin-1", result.getTestedBy());
        verify(health).check("local-provider");
    }
}
