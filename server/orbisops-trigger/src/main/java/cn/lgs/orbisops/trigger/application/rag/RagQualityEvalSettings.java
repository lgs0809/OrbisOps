package cn.lgs.orbisops.trigger.application.rag;

/** Typed rerank policy for offline/online RAG quality evaluation. */
public record RagQualityEvalSettings(
        boolean rerankEnabled,
        String rerankProvider,
        String rerankBaseUrl,
        String rerankApiKey,
        String rerankPath,
        String rerankModel) {

    public RagQualityEvalSettings {
        rerankProvider = text(rerankProvider);
        rerankBaseUrl = text(rerankBaseUrl);
        rerankApiKey = text(rerankApiKey);
        rerankPath = text(rerankPath);
        rerankModel = text(rerankModel);
    }

    public static RagQualityEvalSettings defaults() {
        return new RagQualityEvalSettings(
                true,
                "cohere",
                "http://127.0.0.1:8110",
                "local-rag-models",
                "v1/rerank",
                "Qwen/Qwen3-VL-Reranker-2B");
    }

    static RagQualityEvalSettings legacyConstructorDefaults() {
        return new RagQualityEvalSettings(false, "", "", "", "", "");
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
