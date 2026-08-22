package cn.lgs.orbisops.trigger.ops;

/** Typed configuration consumed by the RAG knowledge sub-agent. */
public record OpsRagKnowledgeSettings(
        boolean rerankEnabled,
        String rerankProvider,
        String rerankBaseUrl,
        String rerankApiKey,
        String rerankPath,
        String rerankModel,
        int rerankCandidateTopK,
        int rerankTopN,
        int rerankMaxDocChars,
        String queryRewriteMode,
        boolean llmQueryRewriteEnabled,
        String llmQueryRewriteBaseUrl,
        String llmQueryRewriteApiKey,
        String llmQueryRewritePath,
        String llmQueryRewriteModel,
        int llmQueryRewriteMaxQueries,
        int llmQueryRewriteTimeoutSeconds,
        int llmQueryRewriteMinChars,
        boolean llmQueryRewriteOnLowRecall,
        int llmQueryRewriteLowRecallMinCandidates,
        boolean failOnLlmDegradation) {

    public OpsRagKnowledgeSettings {
        rerankProvider = text(rerankProvider);
        rerankBaseUrl = text(rerankBaseUrl);
        rerankApiKey = text(rerankApiKey);
        rerankPath = text(rerankPath);
        rerankModel = text(rerankModel);
        queryRewriteMode = text(queryRewriteMode);
        llmQueryRewriteBaseUrl = text(llmQueryRewriteBaseUrl);
        llmQueryRewriteApiKey = text(llmQueryRewriteApiKey);
        llmQueryRewritePath = text(llmQueryRewritePath);
        llmQueryRewriteModel = text(llmQueryRewriteModel);
    }

    public static OpsRagKnowledgeSettings defaults() {
        return new OpsRagKnowledgeSettings(
                false,
                "",
                "",
                "",
                "v1/rerank",
                "",
                20,
                6,
                1200,
                "rule",
                false,
                "",
                "",
                "v1/chat/completions",
                "",
                4,
                2,
                18,
                true,
                2,
                false);
    }

    OpsRagKnowledgeRetrievalService.Settings retrievalSettings() {
        return new OpsRagKnowledgeRetrievalService.Settings(
                rerankEnabled,
                rerankProvider,
                rerankBaseUrl,
                rerankApiKey,
                rerankPath,
                rerankModel,
                rerankCandidateTopK,
                rerankTopN,
                rerankMaxDocChars,
                queryRewriteMode,
                llmQueryRewriteEnabled,
                llmQueryRewriteBaseUrl,
                llmQueryRewriteApiKey,
                llmQueryRewritePath,
                llmQueryRewriteModel,
                llmQueryRewriteMaxQueries,
                llmQueryRewriteTimeoutSeconds,
                llmQueryRewriteMinChars,
                llmQueryRewriteOnLowRecall,
                llmQueryRewriteLowRecallMinCandidates,
                failOnLlmDegradation);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
