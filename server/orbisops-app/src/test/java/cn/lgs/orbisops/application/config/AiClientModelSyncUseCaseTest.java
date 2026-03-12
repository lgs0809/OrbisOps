package cn.lgs.orbisops.application.config;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiClientModelSyncUseCaseTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-30T06:30:00Z"),
            ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 30, 6, 30);
    private static final String ENDPOINT = "http://127.0.0.1:8080/v1/models";

    @Test
    void missingProviderFailsWithoutProtocolCatalogOrAudit() {
        Fixture fixture = fixture();
        when(fixture.targets.find("missing")).thenReturn(null);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.useCase.sync("missing"));

        assertEquals("未找到对应的 API Provider：missing", error.getMessage());
        verifyNoInteractions(fixture.protocol, fixture.catalog, fixture.audit);
    }

    @Test
    void emptyBaseUrlFailsBeforeAuditedSyncBoundary() {
        Fixture fixture = fixture();
        AiClientModelSyncTarget target = new AiClientModelSyncTarget("openai-main", " ", "key");
        when(fixture.targets.find("openai-main")).thenReturn(target);
        when(fixture.protocol.resolveEndpoint(target))
                .thenThrow(new IllegalArgumentException("Provider Base URL 为空"));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> fixture.useCase.sync("openai-main"));

        assertEquals("Provider Base URL 为空", error.getMessage());
        verify(fixture.protocol, never()).fetch(any(), anyString());
        verifyNoInteractions(fixture.catalog, fixture.audit);
    }

    @Test
    void createsNewModelsAndInfersUsage() {
        Fixture fixture = readyFixture(List.of(
                "text-embedding-3-small",
                "bge-reranker-v2",
                "qwen3-vl-8b",
                "gpt-main"));
        when(fixture.catalog.findByModelId(anyString())).thenReturn(null);
        when(fixture.catalog.insert(any())).thenReturn(true);

        AiClientModelSyncResult result = fixture.useCase.sync("openai-main");

        assertEquals(4, result.fetchedCount());
        assertEquals(4, result.createdCount());
        assertEquals(0, result.updatedCount());
        assertEquals(0, result.skippedCount());
        assertEquals(List.of(
                "text-embedding-3-small",
                "bge-reranker-v2",
                "qwen3-vl-8b",
                "gpt-main"), result.modelIds());
        ArgumentCaptor<AiClientModelDefinition> saved = ArgumentCaptor.forClass(AiClientModelDefinition.class);
        verify(fixture.catalog, org.mockito.Mockito.times(4)).insert(saved.capture());
        assertEquals(List.of("EMBEDDING", "RERANK", "VISION", "CHAT"),
                saved.getAllValues().stream().map(AiClientModelDefinition::modelUsage).toList());
        assertTrue(saved.getAllValues().stream().allMatch(model -> NOW.equals(model.createTime())));
        verify(fixture.audit).succeeded(result);
    }

    @Test
    void updatesSameProviderModelAndPreservesExistingFields() {
        Fixture fixture = readyFixture(List.of("qwen3-vl-8b"));
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 2, 3, 4);
        AiClientModelDefinition existing = new AiClientModelDefinition(
                9L,
                "qwen3-vl-8b",
                "openai-main",
                "Custom Name",
                "CUSTOM_TYPE",
                "CUSTOM_USAGE",
                "keep-description",
                0,
                createdAt,
                LocalDateTime.of(2026, 2, 3, 4, 5));
        when(fixture.catalog.findByModelId("qwen3-vl-8b")).thenReturn(existing);
        when(fixture.catalog.updateByModelId(any())).thenReturn(true);

        AiClientModelSyncResult result = fixture.useCase.sync("openai-main");

        assertEquals(0, result.createdCount());
        assertEquals(1, result.updatedCount());
        ArgumentCaptor<AiClientModelDefinition> updated = ArgumentCaptor.forClass(AiClientModelDefinition.class);
        verify(fixture.catalog).updateByModelId(updated.capture());
        AiClientModelDefinition value = updated.getValue();
        assertEquals(9L, value.id());
        assertEquals("Custom Name", value.modelName());
        assertEquals("CUSTOM_TYPE", value.modelType());
        assertEquals("CUSTOM_USAGE", value.modelUsage());
        assertEquals("keep-description", value.description());
        assertEquals(0, value.status());
        assertEquals(createdAt, value.createTime());
        assertEquals(NOW, value.updateTime());
    }

    @Test
    void skipsModelOwnedByAnotherProvider() {
        Fixture fixture = readyFixture(List.of("shared-model"));
        when(fixture.catalog.findByModelId("shared-model")).thenReturn(new AiClientModelDefinition(
                2L, "shared-model", "other-provider", "Shared", "CHAT", "CHAT",
                "other", 1, NOW, NOW));

        AiClientModelSyncResult result = fixture.useCase.sync("openai-main");

        assertEquals(0, result.createdCount());
        assertEquals(0, result.updatedCount());
        assertEquals(1, result.skippedCount());
        assertEquals(List.of(), result.modelIds());
        verify(fixture.catalog, never()).insert(any());
        verify(fixture.catalog, never()).updateByModelId(any());
    }

    @Test
    void limitsProcessingToTwoHundredAndCountsOverflowAsSkipped() {
        List<String> modelIds = IntStream.range(0, 203)
                .mapToObj(index -> "model-" + index)
                .toList();
        Fixture fixture = readyFixture(modelIds);
        when(fixture.catalog.findByModelId(anyString())).thenReturn(null);
        when(fixture.catalog.insert(any())).thenReturn(true);

        AiClientModelSyncResult result = fixture.useCase.sync("openai-main");

        assertEquals(203, result.fetchedCount());
        assertEquals(200, result.createdCount());
        assertEquals(3, result.skippedCount());
        assertEquals(200, result.modelIds().size());
        verify(fixture.catalog, org.mockito.Mockito.times(200)).insert(any());
    }

    @Test
    void unsuccessfulWritesDoNotIncreaseCountersButStillReturnModelIds() {
        Fixture fixture = readyFixture(List.of("new-model", "existing-model"));
        when(fixture.catalog.findByModelId("new-model")).thenReturn(null);
        when(fixture.catalog.findByModelId("existing-model")).thenReturn(new AiClientModelDefinition(
                3L, "existing-model", "openai-main", "Existing", "CHAT", "CHAT",
                "existing", 1, NOW, NOW));
        when(fixture.catalog.insert(any())).thenReturn(false);
        when(fixture.catalog.updateByModelId(any())).thenReturn(false);

        AiClientModelSyncResult result = fixture.useCase.sync("openai-main");

        assertEquals(0, result.createdCount());
        assertEquals(0, result.updatedCount());
        assertEquals(List.of("new-model", "existing-model"), result.modelIds());
    }

    @Test
    void protocolFailureIsAuditedThenWrapped() {
        Fixture fixture = fixture();
        AiClientModelSyncTarget target = target();
        when(fixture.targets.find("openai-main")).thenReturn(target);
        when(fixture.protocol.resolveEndpoint(target)).thenReturn(ENDPOINT);
        when(fixture.protocol.fetch(target, ENDPOINT)).thenThrow(new IllegalStateException("protocol-down"));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> fixture.useCase.sync("openai-main"));

        assertEquals("protocol-down", error.getMessage());
        ArgumentCaptor<AiClientModelSyncResult> failed = ArgumentCaptor.forClass(AiClientModelSyncResult.class);
        verify(fixture.audit).failed(failed.capture());
        assertEquals("openai-main", failed.getValue().apiId());
        assertEquals(ENDPOINT, failed.getValue().endpoint());
        assertEquals("protocol-down", failed.getValue().errorMessage());
        verify(fixture.audit, never()).succeeded(any());
    }

    @Test
    void successAuditFailureTriggersFailureAuditBeforeWrappedError() {
        Fixture fixture = readyFixture(List.of());
        doThrow(new IllegalStateException("success-audit-down"))
                .when(fixture.audit).succeeded(any());

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> fixture.useCase.sync("openai-main"));

        assertEquals("success-audit-down", error.getMessage());
        InOrder order = inOrder(fixture.audit);
        order.verify(fixture.audit).succeeded(any());
        order.verify(fixture.audit).failed(any());
    }

    private Fixture readyFixture(List<String> modelIds) {
        Fixture fixture = fixture();
        AiClientModelSyncTarget target = target();
        when(fixture.targets.find("openai-main")).thenReturn(target);
        when(fixture.protocol.resolveEndpoint(target)).thenReturn(ENDPOINT);
        when(fixture.protocol.fetch(target, ENDPOINT))
                .thenReturn(new AiClientModelSyncFetchResult(ENDPOINT, 200, modelIds));
        return fixture;
    }

    private Fixture fixture() {
        AiClientModelSyncTargetPort targets = mock(AiClientModelSyncTargetPort.class);
        AiClientModelSyncProtocolPort protocol = mock(AiClientModelSyncProtocolPort.class);
        AiClientModelSyncCatalogPort catalog = mock(AiClientModelSyncCatalogPort.class);
        AiClientModelSyncAuditPort audit = mock(AiClientModelSyncAuditPort.class);
        return new Fixture(
                targets,
                protocol,
                catalog,
                audit,
                new AiClientModelSyncUseCase(targets, protocol, catalog, audit, CLOCK));
    }

    private AiClientModelSyncTarget target() {
        return new AiClientModelSyncTarget(
                "openai-main",
                "http://127.0.0.1:8080/v1",
                "test-api-key");
    }

    private record Fixture(
            AiClientModelSyncTargetPort targets,
            AiClientModelSyncProtocolPort protocol,
            AiClientModelSyncCatalogPort catalog,
            AiClientModelSyncAuditPort audit,
            AiClientModelSyncUseCase useCase) {
    }
}
