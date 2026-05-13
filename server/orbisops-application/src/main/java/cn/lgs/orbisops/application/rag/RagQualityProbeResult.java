package cn.lgs.orbisops.application.rag;

import cn.lgs.orbisops.domain.rageval.model.RagEvalProbeAssessment;

import java.util.List;

/** Typed result of one online RAG quality probe. */
public record RagQualityProbeResult<M>(
        RagQualityProbeCommand command,
        List<RagQualityScoredHit<M>> hits,
        RagEvalProbeAssessment assessment) {

    public RagQualityProbeResult {
        if (command == null) throw new IllegalArgumentException("RAG_QUALITY_PROBE_COMMAND_REQUIRED");
        hits = hits == null ? List.of() : List.copyOf(hits);
        if (assessment == null) throw new IllegalArgumentException("RAG_QUALITY_ASSESSMENT_REQUIRED");
        if (hits.size() != assessment.hitCount()) {
            throw new IllegalArgumentException("RAG_QUALITY_RESULT_CARDINALITY_MISMATCH");
        }
    }
}
