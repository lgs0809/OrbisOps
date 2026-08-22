package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.api.dto.AiClientApiCredentialReferenceRequestDTO;
import cn.lgs.orbisops.api.dto.AiClientApiCredentialReferenceResponseDTO;
import cn.lgs.orbisops.application.config.AiClientApiCatalogUseCase;
import cn.lgs.orbisops.application.config.AiClientApiDefinition;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiClientApiCredentialReferenceApplicationServiceTest {

    @Test
    void createStoresOnlyEnvironmentReference() {
        AiClientApiCatalogUseCase catalog = mock(AiClientApiCatalogUseCase.class);
        AiClientApiCredentialReferenceApplicationService service = new AiClientApiCredentialReferenceApplicationService(catalog);
        when(catalog.create(org.mockito.ArgumentMatchers.any())).thenReturn(true);

        boolean changed = service.create(request(null, "provider-1", "openai_api_key"));

        assertTrue(changed);
        ArgumentCaptor<AiClientApiDefinition> captor = ArgumentCaptor.forClass(AiClientApiDefinition.class);
        verify(catalog).create(captor.capture());
        assertEquals("${env:OPENAI_API_KEY:}", captor.getValue().apiKey());
    }

    @Test
    void queryReferenceNeverReturnsResolvedCredential() {
        AiClientApiCatalogUseCase catalog = mock(AiClientApiCatalogUseCase.class);
        AiClientApiCredentialReferenceApplicationService service = new AiClientApiCredentialReferenceApplicationService(catalog);
        when(catalog.findByApiId("provider-1")).thenReturn(definition("${env:OPENAI_API_KEY:}"));

        AiClientApiCredentialReferenceResponseDTO result = service.queryReference("provider-1");

        assertEquals("OPENAI_API_KEY", result.getCredentialEnvironmentVariable());
        assertTrue(result.isEnvironmentReference());
        assertFalse(result.isLegacyStoredCredential());
    }

    @Test
    void legacyStoredCredentialIsReportedWithoutDisclosure() {
        AiClientApiCatalogUseCase catalog = mock(AiClientApiCatalogUseCase.class);
        AiClientApiCredentialReferenceApplicationService service = new AiClientApiCredentialReferenceApplicationService(catalog);
        when(catalog.findByApiId("provider-1")).thenReturn(definition("legacy-secret-value"));

        AiClientApiCredentialReferenceResponseDTO result = service.queryReference("provider-1");

        assertEquals("", result.getCredentialEnvironmentVariable());
        assertFalse(result.isEnvironmentReference());
        assertTrue(result.isLegacyStoredCredential());
    }

    @Test
    void invalidEnvironmentVariableIsRejected() {
        AiClientApiCatalogUseCase catalog = mock(AiClientApiCatalogUseCase.class);
        AiClientApiCredentialReferenceApplicationService service = new AiClientApiCredentialReferenceApplicationService(catalog);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.create(request(null, "provider-1", "bad-name")));

        assertEquals("CREDENTIAL_ENVIRONMENT_VARIABLE_INVALID", error.getMessage());
    }

    private AiClientApiCredentialReferenceRequestDTO request(Long id, String apiId, String envName) {
        return AiClientApiCredentialReferenceRequestDTO.builder()
                .id(id)
                .apiId(apiId)
                .providerName("Provider")
                .providerType("OPENAI_COMPATIBLE")
                .baseUrl("https://provider.example")
                .credentialEnvironmentVariable(envName)
                .completionsPath("v1/chat/completions")
                .embeddingsPath("v1/embeddings")
                .status(1)
                .build();
    }

    private AiClientApiDefinition definition(String apiKey) {
        return new AiClientApiDefinition(
                1L,
                "provider-1",
                "Provider",
                "OPENAI_COMPATIBLE",
                "https://provider.example",
                apiKey,
                "v1/chat/completions",
                "v1/embeddings",
                1,
                null,
                null);
    }
}
