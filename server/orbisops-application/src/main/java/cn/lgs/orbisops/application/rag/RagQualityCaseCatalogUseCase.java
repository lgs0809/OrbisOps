package cn.lgs.orbisops.application.rag;

import java.util.List;

/** Application process manager for RAG quality evaluation case catalog operations. */
public final class RagQualityCaseCatalogUseCase {

    private final RagQualityCaseCatalogPort catalogPort;

    public RagQualityCaseCatalogUseCase(RagQualityCaseCatalogPort catalogPort) {
        if (catalogPort == null) {
            throw new IllegalArgumentException("RAG_QUALITY_CASE_CATALOG_PORT_REQUIRED");
        }
        this.catalogPort = catalogPort;
    }

    public void initialize() {
        catalogPort.ensureReady();
    }

    public List<RagQualityCaseRecord> list(Boolean enabled, int limit) {
        catalogPort.ensureReady();
        return List.copyOf(catalogPort.list(enabled, limit));
    }

    public RagQualityCaseRecord save(RagQualityCaseSaveCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("RAG_QUALITY_CASE_SAVE_COMMAND_REQUIRED");
        }
        catalogPort.ensureReady();
        return catalogPort.save(command);
    }

    public boolean delete(Long id) {
        catalogPort.ensureReady();
        return catalogPort.delete(id);
    }

    public List<RagQualityEvalCase> enabledCases(int limit) {
        catalogPort.ensureReady();
        return catalogPort.listEnabled(limit).stream()
                .map(RagQualityCaseRecord::toEvalCase)
                .toList();
    }
}
