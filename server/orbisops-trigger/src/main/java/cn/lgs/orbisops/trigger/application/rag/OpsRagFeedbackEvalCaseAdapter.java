package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.application.rag.RagFeedbackEvalCasePort;
import cn.lgs.orbisops.application.rag.RagQualityCaseCatalogUseCase;
import cn.lgs.orbisops.application.rag.RagQualityCaseRecord;
import cn.lgs.orbisops.application.rag.RagQualityCaseSaveCommand;

/** Adapter from feedback triage to the existing typed RAG quality case catalog. */
public final class OpsRagFeedbackEvalCaseAdapter implements RagFeedbackEvalCasePort {

    private final RagQualityCaseCatalogUseCase caseCatalogUseCase;

    public OpsRagFeedbackEvalCaseAdapter(RagQualityCaseCatalogUseCase caseCatalogUseCase) {
        if (caseCatalogUseCase == null) {
            throw new IllegalArgumentException("RAG_QUALITY_CASE_CATALOG_USE_CASE_REQUIRED");
        }
        this.caseCatalogUseCase = caseCatalogUseCase;
    }

    @Override
    public RagQualityCaseRecord save(RagQualityCaseSaveCommand command) {
        return caseCatalogUseCase.save(command);
    }
}
