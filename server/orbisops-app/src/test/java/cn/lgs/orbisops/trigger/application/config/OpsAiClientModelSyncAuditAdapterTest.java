package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.api.dto.AiClientModelSyncResponseDTO;
import cn.lgs.orbisops.application.config.AiClientModelSyncResult;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OpsAiClientModelSyncAuditAdapterTest {

    @Test
    void projectsSuccessAndFailureAuditPayloads() {
        OpsConfigAuditService auditService = mock(OpsConfigAuditService.class);
        OpsAiClientModelSyncAuditAdapter adapter = new OpsAiClientModelSyncAuditAdapter(auditService);
        LocalDateTime syncedAt = LocalDateTime.of(2026, 7, 30, 7, 30);
        AiClientModelSyncResult success = new AiClientModelSyncResult(
                "openai-main",
                "https://provider.example/v1/models",
                200,
                2,
                1,
                1,
                0,
                List.of("gpt-main", "embedding-main"),
                "",
                syncedAt);
        AiClientModelSyncResult failure = new AiClientModelSyncResult(
                "openai-main",
                "https://provider.example/v1/models",
                null,
                0,
                0,
                0,
                0,
                List.of(),
                "provider-down",
                syncedAt);

        adapter.succeeded(success);
        adapter.failed(failure);

        ArgumentCaptor<AiClientModelSyncResponseDTO> successPayload = ArgumentCaptor.forClass(AiClientModelSyncResponseDTO.class);
        verify(auditService).record(
                org.mockito.ArgumentMatchers.eq("model-config"),
                org.mockito.ArgumentMatchers.eq("sync-from-provider"),
                org.mockito.ArgumentMatchers.eq("openai-main"),
                org.mockito.ArgumentMatchers.isNull(),
                successPayload.capture());
        assertEquals(200, successPayload.getValue().getHttpStatus());
        assertEquals(2, successPayload.getValue().getFetchedCount());
        assertEquals(List.of("gpt-main", "embedding-main"), successPayload.getValue().getModelIds());

        ArgumentCaptor<AiClientModelSyncResponseDTO> failurePayload = ArgumentCaptor.forClass(AiClientModelSyncResponseDTO.class);
        verify(auditService).record(
                org.mockito.ArgumentMatchers.eq("model-config"),
                org.mockito.ArgumentMatchers.eq("sync-from-provider-failed"),
                org.mockito.ArgumentMatchers.eq("openai-main"),
                org.mockito.ArgumentMatchers.isNull(),
                failurePayload.capture());
        assertEquals("provider-down", failurePayload.getValue().getErrorMessage());
        assertEquals(0, failurePayload.getValue().getFetchedCount());
        assertEquals(syncedAt, failurePayload.getValue().getSyncedAt());
    }
}
