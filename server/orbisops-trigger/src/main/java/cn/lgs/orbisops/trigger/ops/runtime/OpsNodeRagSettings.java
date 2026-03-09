package cn.lgs.orbisops.trigger.ops.runtime;

/** Immutable settings for node-level RAG retrieval. */
public record OpsNodeRagSettings(
        Rerank rerank,
        Ttft ttft,
        QueryRewrite queryRewrite,
        boolean failOnLlmDegradation) {

    public OpsNodeRagSettings {
        if (rerank == null || ttft == null || queryRewrite == null) {
            throw new IllegalArgumentException("NODE_RAG_SETTINGS_REQUIRED");
        }
    }

    public record Rerank(
            boolean enabled,
            String provider,
            String baseUrl,
            String apiKey,
            String path,
            String model,
            int candidateTopK,
            int topN,
            int maxDocumentChars) {
    }

    public record Ttft(
            boolean optimizeStreaming,
            boolean disableRerankOnStream,
            int vectorTopK,
            int bm25TopK,
            int finalTopK,
            boolean queryRewriteEnabled) {
    }

    public record QueryRewrite(
            String mode,
            boolean llmEnabled,
            String baseUrl,
            String apiKey,
            String path,
            String model,
            int maxQueries,
            int timeoutSeconds,
            int minChars,
            boolean onLowRecall,
            int lowRecallMinCandidates) {
    }
}
