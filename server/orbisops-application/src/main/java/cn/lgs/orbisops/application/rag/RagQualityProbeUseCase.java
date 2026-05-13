package cn.lgs.orbisops.application.rag;

import cn.lgs.orbisops.domain.rageval.model.RagEvalProbeAssessment;
import cn.lgs.orbisops.domain.rageval.service.RagQualityAssessmentPolicy;

import java.util.ArrayList;
import java.util.List;

/** Application use case joining online retrieval with pure domain quality assessment. */
public final class RagQualityProbeUseCase<M> {

    private final RagQualityRetrievalPort<M> retrievalPort;
    private final RagQualityAssessmentPolicy assessmentPolicy;

    public RagQualityProbeUseCase(
            RagQualityRetrievalPort<M> retrievalPort,
            RagQualityAssessmentPolicy assessmentPolicy) {
        if (retrievalPort == null || assessmentPolicy == null) {
            throw new IllegalArgumentException("RAG_QUALITY_PROBE_DEPENDENCIES_REQUIRED");
        }
        this.retrievalPort = retrievalPort;
        this.assessmentPolicy = assessmentPolicy;
    }

    public RagQualityProbeResult<M> probe(RagQualityProbeCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("RAG_QUALITY_PROBE_COMMAND_REQUIRED");
        }
        List<RagQualityRetrievalHit<M>> hits = retrievalPort.retrieve(command);
        List<RagQualityRetrievalHit<M>> safeHits = hits == null ? List.of() : List.copyOf(hits);
        RagEvalProbeAssessment assessment = assessmentPolicy.assessProbe(
                safeHits.stream().map(RagQualityRetrievalHit::content).toList(),
                command.query(),
                command.expectedKeywords());
        List<RagQualityScoredHit<M>> scored = new ArrayList<>(safeHits.size());
        for (int index = 0; index < safeHits.size(); index++) {
            scored.add(new RagQualityScoredHit<>(
                    index + 1,
                    safeHits.get(index),
                    assessment.hitScores().get(index)));
        }
        return new RagQualityProbeResult<>(command, scored, assessment);
    }
}
