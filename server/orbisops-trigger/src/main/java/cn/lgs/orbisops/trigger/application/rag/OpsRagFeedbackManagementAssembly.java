package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.application.rag.RagFeedbackUseCase;
import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagFeedbackRepository;

/** Immutable composition root result for RAG feedback and knowledge-gap management. */
public record OpsRagFeedbackManagementAssembly(
        RagFeedbackUseCase useCase,
        OpsRagFeedbackViewMapper viewMapper) {

    public OpsRagFeedbackManagementAssembly {
        if (useCase == null || viewMapper == null) {
            throw new IllegalArgumentException("RAG_FEEDBACK_ASSEMBLY_REQUIRED");
        }
    }

    public static OpsRagFeedbackManagementAssembly create(
            IRagFeedbackRepository repository,
            OpsRagQualityEvalManagementAssembly qualityAssembly,
            RagFeedbackSettings settings) {
        if (qualityAssembly == null) {
            throw new IllegalArgumentException("RAG_QUALITY_EVAL_ASSEMBLY_REQUIRED");
        }
        RagFeedbackSettings resolvedSettings = settings == null
                ? new RagFeedbackSettings(true)
                : settings;
        RagFeedbackUseCase useCase = new RagFeedbackUseCase(
                new OpsRagFeedbackCatalogAdapter(repository),
                new OpsRagFeedbackEvalCaseAdapter(qualityAssembly.caseCatalogUseCase()),
                resolvedSettings.autoInit());
        return new OpsRagFeedbackManagementAssembly(
                useCase,
                new OpsRagFeedbackViewMapper());
    }
}
