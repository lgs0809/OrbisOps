package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.api.dto.AiClientApiResponseDTO;
import cn.lgs.orbisops.application.config.AiClientApiDefinition;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OpsAiClientApiCatalogAdapterTest {

    @Test
    void auditSnapshotsAlwaysMaskApiKey() {
        OpsConfigAuditService audit = mock(OpsConfigAuditService.class);
        OpsAiClientApiCatalogAdapter adapter = new OpsAiClientApiCatalogAdapter(audit);
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
