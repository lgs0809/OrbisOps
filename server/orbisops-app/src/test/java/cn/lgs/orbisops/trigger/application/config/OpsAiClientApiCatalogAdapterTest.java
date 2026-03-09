package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.api.dto.AiClientApiResponseDTO;
import cn.lgs.orbisops.application.config.AiClientApiDefinition;
import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientApiConfigRepository;
import cn.lgs.orbisops.domain.agent.model.entity.AiClientConfigRecord;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsAiClientApiCatalogAdapterTest {

    @Test
    void mapsTypedDefinitionToLegacyWideRecord() {
        IAiClientApiConfigRepository repository = mock(IAiClientApiConfigRepository.class);
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        OpsAiClientApiCatalogAdapter adapter = new OpsAiClientApiCatalogAdapter(repository, audit);
        AiClientApiDefinition definition = definition("configured-api-key");
        when(repository.insert(any())).thenReturn(1);

        assertTrue(adapter.insert(definition));

        ArgumentCaptor<AiClientConfigRecord> record = ArgumentCaptor.forClass(AiClientConfigRecord.class);
        verify(repository).insert(record.capture());
        assertEquals("openai-main", record.getValue().getApiId());
        assertEquals("OpenAI", record.getValue().getProviderName());
        assertEquals("configured-api-key", record.getValue().getApiKey());
        assertEquals(definition.createTime(), record.getValue().getCreateTime());
    }

    @Test
    void mapsRepositoryRowsToNarrowDefinitions() {
        IAiClientApiConfigRepository repository = mock(IAiClientApiConfigRepository.class);
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        OpsAiClientApiCatalogAdapter adapter = new OpsAiClientApiCatalogAdapter(repository, audit);
        when(repository.queryEnabledApis()).thenReturn(List.of(AiClientConfigRecord.builder()
                .id(7L)
                .apiId("openai-main")
                .providerName("OpenAI")
                .providerType("OPENAI_COMPATIBLE")
                .baseUrl("https://example.test/v1")
                .apiKey("configured-api-key")
                .status(1)
                .build()));

        AiClientApiDefinition definition = adapter.listEnabled().get(0);

        assertEquals(7L, definition.id());
        assertEquals("openai-main", definition.apiId());
        assertEquals("configured-api-key", definition.apiKey());
    }

    @Test
    void auditSnapshotsAlwaysMaskApiKey() {
        IAiClientApiConfigRepository repository = mock(IAiClientApiConfigRepository.class);
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        OpsAiClientApiCatalogAdapter adapter = new OpsAiClientApiCatalogAdapter(repository, audit);
        AiClientApiDefinition before = definition("old-secret");
        AiClientApiDefinition after = definition("new-secret");

        adapter.updatedByApiId("openai-main", before, after);

        ArgumentCaptor<Object> beforeSnapshot = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> afterSnapshot = ArgumentCaptor.forClass(Object.class);
        verify(audit).record(
                org.mockito.ArgumentMatchers.eq("api-config"),
                org.mockito.ArgumentMatchers.eq("update-by-api-id"),
                org.mockito.ArgumentMatchers.eq("openai-main"),
                beforeSnapshot.capture(),
                afterSnapshot.capture());
        assertEquals("******", ((AiClientApiResponseDTO) beforeSnapshot.getValue()).getApiKey());
        assertEquals("******", ((AiClientApiResponseDTO) afterSnapshot.getValue()).getApiKey());
    }

    private AiClientApiDefinition definition(String apiKey) {
        LocalDateTime time = LocalDateTime.of(2026, 7, 30, 3, 0);
        return new AiClientApiDefinition(
                7L,
                "openai-main",
                "OpenAI",
                "OPENAI_COMPATIBLE",
                "https://example.test/v1",
                apiKey,
                "/chat/completions",
                "/embeddings",
                1,
                time,
                time);
    }
}
