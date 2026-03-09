package cn.lgs.orbisops.application.rag;

import cn.lgs.orbisops.domain.rageval.model.RagEvalRunAssessment;
import cn.lgs.orbisops.domain.rageval.service.RagQualityAssessmentPolicy;

import java.util.ArrayList;
import java.util.List;

/** Application process manager for sequential batch RAG quality evaluation. */
public final class RagQualityRunUseCase<M> {

    private final RagQualityProbeUseCase<M> probeUseCase;
    private final RagQualityAssessmentPolicy assessmentPolicy;
    private final RagQualityRunPersistencePort<M> persistencePort;

    public RagQualityRunUseCase(
            RagQualityProbeUseCase<M> probeUseCase,
            RagQualityAssessmentPolicy assessmentPolicy,
            RagQualityRunPersistencePort<M> persistencePort) {
        if (probeUseCase == null || assessmentPolicy == null || persistencePort == null) {
            throw new IllegalArgumentException("RAG_QUALITY_RUN_DEPENDENCIES_REQUIRED");
        }
        this.probeUseCase = probeUseCase;
        this.assessmentPolicy = assessmentPolicy;
        this.persistencePort = persistencePort;
    }

    public RagQualityRunResult<M> run(RagQualityRunCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("RAG_QUALITY_RUN_COMMAND_REQUIRED");
        }
        List<RagQualityCaseResult<M>> results = new ArrayList<>(command.cases().size());
        for (RagQualityEvalCase evalCase : command.cases()) {
            RagQualityProbeResult<M> probe = probeUseCase.probe(
                    new RagQualityProbeCommand(
                            evalCase.query(),
                            evalCase.knowledgeTag(),
                            evalCase.expectedKeywords(),
                            evalCase.topK(),
                            command.retrievalMode(),
                            command.rerankEnabled()));
            results.add(new RagQualityCaseResult<>(evalCase, probe));
        }
        RagEvalRunAssessment assessment = assessmentPolicy.aggregate(
                results.stream()
                        .map(RagQualityCaseResult::probeResult)
                        .map(RagQualityProbeResult::assessment)
                        .toList());
        RagQualityRunResult<M> result = new RagQualityRunResult<>(assessment, results);
        persistencePort.save(result);
        return result;
    }
}
