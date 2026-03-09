package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.api.dto.AiClientModelRequestDTO;
import cn.lgs.orbisops.api.dto.AiClientModelSyncResponseDTO;
import cn.lgs.orbisops.application.config.AiClientModelCatalogUseCase;
import cn.lgs.orbisops.application.config.AiClientModelSyncResult;
import cn.lgs.orbisops.application.config.AiClientModelSyncUseCase;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiClientModelApplicationServiceTest {

    @Test
    void shouldDelegateFailedUpdateToTypedCatalog() {
        AiClientModelCatalogUseCase catalog = mock(AiClientModelCatalogUseCase.class);
        AiClientModelSyncUseCase sync = mock(AiClientModelSyncUseCase.class);
        AiClientModelApplicationService service = new AiClientModelApplicationService(catalog, sync);
        AiClientModelRequestDTO request = new AiClientModelRequestDTO();
        request.setId(404L);
        request.setModelId("missing-model");
        request.setModelName("Missing Model");
        request.setModelType("CHAT");
        request.setApiId("openai-main");
        request.setStatus(1);
        when(catalog.updateById(any())).thenReturn(false);

        boolean updated = service.updateById(request);

        assertFalse(updated);
        verify(catalog).updateById(argThat(model ->
                Long.valueOf(404L).equals(model.id())
                        && "missing-model".equals(model.modelId())));
    }

    @Test
    void shouldDelegateProviderSyncAndProjectTypedResult() {
        AiClientModelCatalogUseCase catalog = mock(AiClientModelCatalogUseCase.class);
        AiClientModelSyncUseCase sync = mock(AiClientModelSyncUseCase.class);
        AiClientModelApplicationService service = new AiClientModelApplicationService(catalog, sync);
        LocalDateTime syncedAt = LocalDateTime.of(2026, 7, 30, 6, 0);
        when(sync.sync("openai-main")).thenReturn(new AiClientModelSyncResult(
                "openai-main",
                "http://127.0.0.1:8080/v1/models",
                200,
                3,
                1,
                1,
                1,
                List.of("gpt-main", "text-embedding-3-small"),
                "",
                syncedAt));

        AiClientModelSyncResponseDTO result = service.syncFromProvider("openai-main");

        verify(sync).sync("openai-main");
        assertEquals("openai-main", result.getApiId());
        assertEquals("http://127.0.0.1:8080/v1/models", result.getEndpoint());
        assertEquals(200, result.getHttpStatus());
        assertEquals(3, result.getFetchedCount());
        assertEquals(1, result.getCreatedCount());
        assertEquals(1, result.getUpdatedCount());
        assertEquals(1, result.getSkippedCount());
        assertEquals(List.of("gpt-main", "text-embedding-3-small"), result.getModelIds());
        assertEquals("", result.getErrorMessage());
        assertEquals(syncedAt, result.getSyncedAt());
    }
}
