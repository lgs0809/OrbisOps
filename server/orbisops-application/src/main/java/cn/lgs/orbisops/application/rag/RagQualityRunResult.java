package cn.lgs.orbisops.application.rag;

import cn.lgs.orbisops.domain.rageval.model.RagEvalRunAssessment;

import java.util.List;

/** Typed aggregate result for one batch RAG quality evaluation run. */
public record RagQualityRunResult<M>(
        RagEvalRunAssessment assessment,
        List<RagQualityCaseResult<M>> caseResults) {

    public RagQualityRunResult {
        if (assessment == null) throw new IllegalArgumentException("RAG_QUALITY_RUN_ASSESSMENT_REQUIRED");
        caseResults = caseResults == null ? List.of() : List.copyOf(caseResults);
        if (caseResults.size() != assessment.caseCount()) {
            throw new IllegalArgumentException("RAG_QUALITY_RUN_CARDINALITY_MISMATCH");
        }
    }
}
