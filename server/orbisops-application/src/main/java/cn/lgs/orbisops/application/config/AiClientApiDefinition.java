package cn.lgs.orbisops.application.config;

import java.time.LocalDateTime;

/** Narrow typed Application model for one AI model-provider API configuration. */
public record AiClientApiDefinition(
        Long id,
        String apiId,
        String providerName,
        String providerType,
        String baseUrl,
        String apiKey,
        String completionsPath,
        String embeddingsPath,
        Integer status,
        LocalDateTime createTime,
        LocalDateTime updateTime) {

    public AiClientApiDefinition withApiKey(String resolvedApiKey) {
        return new AiClientApiDefinition(
                id, apiId, providerName, providerType, baseUrl, resolvedApiKey,
                completionsPath, embeddingsPath, status, createTime, updateTime);
    }

    public AiClientApiDefinition withDefaultsAndTimes(
            LocalDateTime resolvedCreateTime,
            LocalDateTime resolvedUpdateTime) {
        String resolvedProviderName = hasText(providerName)
                ? providerName
                : hasText(apiId) ? apiId : "model-provider";
        String resolvedProviderType = hasText(providerType)
                ? providerType
                : "OPENAI_COMPATIBLE";
        return new AiClientApiDefinition(
                id, apiId, resolvedProviderName, resolvedProviderType, baseUrl, apiKey,
                completionsPath, embeddingsPath, status,
                resolvedCreateTime, resolvedUpdateTime);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
