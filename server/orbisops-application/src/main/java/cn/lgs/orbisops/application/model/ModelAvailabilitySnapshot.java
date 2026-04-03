package cn.lgs.orbisops.application.model;

/** Typed readiness snapshot exposed by the model-runtime availability port. */
public record ModelAvailabilitySnapshot(
        boolean modelCallsEnabled,
        String openAiBaseUrl,
        String embeddingBaseUrl,
        String chatModel,
        String embeddingModel,
        String rerankProvider,
        String rerankBaseUrl,
        String rerankModel,
        boolean openAiApiKeyConfigured,
        boolean openAiApiKeyUsable,
        boolean embeddingApiKeyConfigured,
        boolean embeddingApiKeyUsable,
        boolean embeddingLocalEndpointReady,
        boolean rerankApiKeyConfigured,
        boolean rerankApiKeyUsable,
        boolean rerankLocalEndpointReady,
        boolean chatAvailable,
        boolean embeddingAvailable,
        boolean rerankAvailable,
        String message) {
}
