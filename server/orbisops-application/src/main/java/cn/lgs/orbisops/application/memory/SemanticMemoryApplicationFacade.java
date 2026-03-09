package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.SemanticMemoryDocumentSnapshot;

import java.util.List;

/** Unified typed application boundary for semantic-memory write, retrieval and clear. */
public class SemanticMemoryApplicationFacade {

    private final SemanticMemoryWriteApplicationService writeService;
    private final SemanticMemoryRetrievalApplicationService retrievalService;
    private final SemanticMemoryClearApplicationService clearService;

    public SemanticMemoryApplicationFacade(
            SemanticMemoryWriteApplicationService writeService,
            SemanticMemoryRetrievalApplicationService retrievalService,
            SemanticMemoryClearApplicationService clearService) {
        this.writeService = writeService;
        this.retrievalService = retrievalService;
        this.clearService = clearService;
    }

    public SemanticMemoryWriteResult write(SemanticMemoryWriteCommand command) {
        return writeService == null
                ? SemanticMemoryWriteResult.skipped()
                : writeService.write(command);
    }

    public List<SemanticMemoryDocumentSnapshot> search(SemanticMemoryRetrievalQuery query) {
        return retrievalService == null ? List.of() : retrievalService.search(query);
    }

    public boolean clear(String sessionId) {
        return clearService != null && clearService.clear(sessionId);
    }
}
