package cn.lgs.orbisops.application.config;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultModelApiBootstrapUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-30T09:30:00Z"),
            ZoneOffset.UTC);

    @Test
    void createsMissingDefaultProviderWithoutCatalogAuditPath() {
        AiClientApiCatalogPort catalog = mock(AiClientApiCatalogPort.class);
        DefaultModelApiBootstrapUseCase useCase = new DefaultModelApiBootstrapUseCase(catalog, CLOCK);
        when(catalog.findByApiId("1001")).thenReturn(null);

        DefaultModelApiBootstrapResult result = useCase.bootstrap(plan(true));

        ArgumentCaptor<AiClientApiDefinition> created = ArgumentCaptor.forClass(AiClientApiDefinition.class);
        verify(catalog).insert(created.capture());
        assertEquals(DefaultModelApiBootstrapResult.Action.CREATED, result.action());
        assertEquals("1001", created.getValue().apiId());
        assertEquals("1001", created.getValue().providerName());
        assertEquals("OPENAI_COMPATIBLE", created.getValue().providerType());
        assertEquals("https://proxy.example.com", created.getValue().baseUrl());
        assertEquals("${env:OPENAI_API_KEY:}", created.getValue().apiKey());
        assertEquals(LocalDateTime.of(2026, 7, 30, 9, 30), created.getValue().createTime());
        assertEquals(created.getValue().createTime(), created.getValue().updateTime());
    }

    @Test
    void migratesOnlyPlaceholderCredentialAndPreservesExistingPathsStatusAndCreateTime() {
        AiClientApiCatalogPort catalog = mock(AiClientApiCatalogPort.class);
        DefaultModelApiBootstrapUseCase useCase = new DefaultModelApiBootstrapUseCase(catalog, CLOCK);
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 1, 8, 0);
        when(catalog.findByApiId("1001")).thenReturn(new AiClientApiDefinition(
                7L,
                "1001",
                "legacy",
                "OPENAI_COMPATIBLE",
                "https://old.example.com",
                "REPLACE_WITH_REAL_OPENAI_API_KEY",
                "custom/chat",
                "custom/embedding",
                0,
                createdAt,
                createdAt));

        DefaultModelApiBootstrapResult result = useCase.bootstrap(plan(true));

        ArgumentCaptor<AiClientApiDefinition> updated = ArgumentCaptor.forClass(AiClientApiDefinition.class);
        verify(catalog).updateByApiId(updated.capture());
        assertEquals(DefaultModelApiBootstrapResult.Action.MIGRATED, result.action());
        assertEquals(7L, updated.getValue().id());
        assertEquals("https://proxy.example.com", updated.getValue().baseUrl());
        assertEquals("${env:OPENAI_API_KEY:}", updated.getValue().apiKey());
        assertEquals("custom/chat", updated.getValue().completionsPath());
        assertEquals("custom/embedding", updated.getValue().embeddingsPath());
        assertEquals(0, updated.getValue().status());
        assertEquals(createdAt, updated.getValue().createTime());
        assertEquals(LocalDateTime.of(2026, 7, 30, 9, 30), updated.getValue().updateTime());
    }

    @Test
    void preservesAdministratorManagedProvider() {
        AiClientApiCatalogPort catalog = mock(AiClientApiCatalogPort.class);
        DefaultModelApiBootstrapUseCase useCase = new DefaultModelApiBootstrapUseCase(catalog, CLOCK);
        when(catalog.findByApiId("1001")).thenReturn(new AiClientApiDefinition(
                7L, "1001", "managed", "OPENAI_COMPATIBLE", "https://managed.example.com",
                "environment-reference", null, null, 1, null, null));

        DefaultModelApiBootstrapResult result = useCase.bootstrap(plan(true));

        assertEquals(DefaultModelApiBootstrapResult.Action.SKIPPED, result.action());
        verify(catalog, never()).insert(any());
        verify(catalog, never()).updateByApiId(any());
    }

    @Test
    void disabledOrUnusablePlanNeverQueriesCatalog() {
        AiClientApiCatalogPort catalog = mock(AiClientApiCatalogPort.class);
        DefaultModelApiBootstrapUseCase useCase = new DefaultModelApiBootstrapUseCase(catalog, CLOCK);

        assertEquals(DefaultModelApiBootstrapResult.Action.SKIPPED,
                useCase.bootstrap(plan(false)).action());
        assertEquals(DefaultModelApiBootstrapResult.Action.SKIPPED,
                useCase.bootstrap(new DefaultModelApiBootstrapPlan(
                        true, "1001", "https://proxy.example.com", "test-api-key")).action());

        verify(catalog, never()).findByApiId(any());
    }

    private DefaultModelApiBootstrapPlan plan(boolean enabled) {
        return new DefaultModelApiBootstrapPlan(
                enabled,
                "1001",
                "https://proxy.example.com/",
                "configured-value");
    }
}
