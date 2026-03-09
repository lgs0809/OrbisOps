package cn.lgs.orbisops.application.config;

import java.time.LocalDateTime;
import java.util.List;

/** Typed result for one Provider model-catalog synchronization. */
public record AiClientModelSyncResult(
        String apiId,
        String endpoint,
        Integer httpStatus,
        Integer fetchedCount,
        Integer createdCount,
        Integer updatedCount,
        Integer skippedCount,
        List<String> modelIds,
        String errorMessage,
        LocalDateTime syncedAt) {

    public AiClientModelSyncResult {
        modelIds = modelIds == null || modelIds.isEmpty() ? List.of() : List.copyOf(modelIds);
        errorMessage = errorMessage == null ? "" : errorMessage;
    }
}
