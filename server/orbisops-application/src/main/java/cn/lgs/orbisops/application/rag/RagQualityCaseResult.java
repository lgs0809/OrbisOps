package cn.lgs.orbisops.application.rag;

/** Typed result for one case inside a batch RAG evaluation run. */
public record RagQualityCaseResult<M>(
        RagQualityEvalCase evalCase,
        RagQualityProbeResult<M> probeResult) {

    public RagQualityCaseResult {
        if (evalCase == null) throw new IllegalArgumentException("RAG_QUALITY_EVAL_CASE_REQUIRED");
        if (probeResult == null) throw new IllegalArgumentException("RAG_QUALITY_PROBE_RESULT_REQUIRED");
    }
}
