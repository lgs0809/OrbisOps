package cn.lgs.orbisops.application.config;

import java.util.List;

/** Normalized `/models` protocol response. */
public record AiClientModelSyncFetchResult(
        String endpoint,
        Integer httpStatus,
        List<String> modelIds) {

    public AiClientModelSyncFetchResult {
        modelIds = modelIds == null || modelIds.isEmpty() ? List.of() : List.copyOf(modelIds);
    }
}
