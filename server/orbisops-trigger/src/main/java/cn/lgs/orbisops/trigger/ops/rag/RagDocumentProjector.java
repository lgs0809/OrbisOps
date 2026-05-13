package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagDocument;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagChunkDraft;
import cn.lgs.orbisops.domain.knowledge.rag.service.RagChunkMaterializer;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Domain chunk materialization and Spring Document ACL projection. */
final class RagDocumentProjector {

    private final RagChunkMaterializer chunkMaterializer;

    RagDocumentProjector(RagChunkMaterializer chunkMaterializer) {
        this.chunkMaterializer = chunkMaterializer;
    }

    List<Document> materialize(List<RagChunkDraft> drafts) {
        return chunkMaterializer.materialize(drafts).stream()
                .map(this::springDocument)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    List<Document> plainText(String text, Map<String, Object> metadata) {
        return materialize(List.of(RagChunkDraft.structureBounded(text, metadata)));
    }

    List<Document> visual(RagVisualFallbackCoordinator.Result result) {
        if (!result.documents().isEmpty()) {
            return result.documents();
        }
        return materialize(result.drafts());
    }

    private Document springDocument(RagDocument document) {
        return new Document(document.id(), document.text(), document.metadata());
    }
}
