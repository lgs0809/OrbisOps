package cn.lgs.orbisops.trigger.ops.rag.advisor;

import java.util.List;

/**
 * Deterministic initial query rewrite decision before any LLM protocol is invoked.
 */
public record RagQueryRewriteDecision(List<String> queries,
                                      boolean rewriteEnabled,
                                      boolean llmEligible) {
    public RagQueryRewriteDecision {
        queries = queries == null ? List.of() : List.copyOf(queries);
    }
}
