package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.application.rag.RagQualityCaseCatalogUseCase;
import cn.lgs.orbisops.application.rag.RagQualityProbeUseCase;
import cn.lgs.orbisops.application.rag.RagQualityRunUseCase;
import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagEvalRepository;
import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagKnowledgeRepository;
import cn.lgs.orbisops.domain.rageval.service.RagQualityAssessmentPolicy;
import cn.lgs.orbisops.trigger.ops.rag.RagMultimodalEmbeddingService;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;

/** Immutable composition root result for the RAG quality evaluation capability. */
public record OpsRagQualityEvalManagementAssembly(
        RagQualityEvalSettings settings,
        RagQualityCaseCatalogUseCase caseCatalogUseCase,
        RagQualityProbeUseCase<Map<String, Object>> probeUseCase,
        RagQualityRunUseCase<Map<String, Object>> runUseCase,
        OpsRagQualityEvalViewMapper viewMapper) {

    public OpsRagQualityEvalManagementAssembly {
        if (settings == null
                || caseCatalogUseCase == null
                || probeUseCase == null
                || runUseCase == null
                || viewMapper == null) {
            throw new IllegalArgumentException("RAG_QUALITY_EVAL_ASSEMBLY_REQUIRED");
        }
    }

    public static OpsRagQualityEvalManagementAssembly create(
            ObjectProvider<VectorStore> vectorStoreProvider,
            ObjectProvider<EmbeddingModel> embeddingModelProvider,
            IRagEvalRepository ragEvalRepository,
            IRagKnowledgeRepository ragKnowledgeRepository,
            RagMultimodalEmbeddingService multimodalEmbeddingService,
            RagQualityEvalSettings settings) {
        RagQualityEvalSettings resolvedSettings = settings == null
                ? RagQualityEvalSettings.defaults()
                : settings;
        RagQualityAssessmentPolicy assessmentPolicy = new RagQualityAssessmentPolicy();
        OpsRagQualityEvalViewMapper viewMapper = new OpsRagQualityEvalViewMapper();
        RagQualityCaseCatalogUseCase caseCatalogUseCase = new RagQualityCaseCatalogUseCase(
                new OpsRagQualityCaseCatalogAdapter(ragEvalRepository));
        RagQualityProbeUseCase<Map<String, Object>> probeUseCase = new RagQualityProbeUseCase<>(
                new OpsRagQualityRetrievalAdapter(
                        vectorStoreProvider,
                        embeddingModelProvider,
                        ragKnowledgeRepository,
                        multimodalEmbeddingService,
                        resolvedSettings),
                assessmentPolicy);
        RagQualityRunUseCase<Map<String, Object>> runUseCase = new RagQualityRunUseCase<>(
                probeUseCase,
                assessmentPolicy,
                new OpsRagQualityRunPersistenceAdapter(
                        ragEvalRepository,
                        viewMapper));
        return new OpsRagQualityEvalManagementAssembly(
                resolvedSettings,
                caseCatalogUseCase,
                probeUseCase,
                runUseCase,
                viewMapper);
    }
}
