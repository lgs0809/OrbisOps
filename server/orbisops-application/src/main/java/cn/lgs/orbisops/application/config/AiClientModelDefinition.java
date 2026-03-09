package cn.lgs.orbisops.application.config;

import java.time.LocalDateTime;

/** Narrow typed Application model for one AI model configuration. */
public record AiClientModelDefinition(
        Long id,
        String modelId,
        String apiId,
        String modelName,
        String modelType,
        String modelUsage,
        String description,
        Integer status,
        LocalDateTime createTime,
        LocalDateTime updateTime) {

    public AiClientModelDefinition withDefaultsAndTimes(
            LocalDateTime resolvedCreateTime,
            LocalDateTime resolvedUpdateTime) {
        return new AiClientModelDefinition(
                id,
                modelId,
                apiId,
                modelName,
                modelType,
                modelUsage,
                hasText(description) ? description : "",
                status,
                resolvedCreateTime,
                resolvedUpdateTime);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
