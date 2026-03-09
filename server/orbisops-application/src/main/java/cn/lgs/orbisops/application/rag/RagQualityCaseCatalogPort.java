package cn.lgs.orbisops.application.rag;

import java.util.List;

/** Persistence boundary for the RAG quality evaluation case catalog. */
public interface RagQualityCaseCatalogPort {

    void ensureReady();

    List<RagQualityCaseRecord> list(Boolean enabled, int limit);

    RagQualityCaseRecord save(RagQualityCaseSaveCommand command);

    boolean delete(Long id);

    List<RagQualityCaseRecord> listEnabled(int limit);
}
