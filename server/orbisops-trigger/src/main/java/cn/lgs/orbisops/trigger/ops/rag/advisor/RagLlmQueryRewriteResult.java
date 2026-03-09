package cn.lgs.orbisops.trigger.ops.rag.advisor;

import java.util.List;

/**
 * Typed result of the LLM query rewrite protocol.
 */
public record RagLlmQueryRewriteResult(List<String> queries,
                                       boolean generated,
                                       String degradationError,
                                       Exception cause) {
    public RagLlmQueryRewriteResult {
        queries = queries == null ? List.of() : List.copyOf(queries);
    }

    public boolean degraded() {
        return degradationError != null;
    }

    public static RagLlmQueryRewriteResult success(List<String> queries) {
        return new RagLlmQueryRewriteResult(queries, true, null, null);
    }

    public static RagLlmQueryRewriteResult degraded(List<String> fallbackQueries,
                                                     String error,
                                                     Exception cause) {
        return new RagLlmQueryRewriteResult(fallbackQueries, false, error, cause);
    }
}
