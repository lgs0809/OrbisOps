package cn.lgs.orbisops.trigger.ops.rag.advisor;

import org.springframework.ai.document.Document;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Recall result annotated with its source, source-local rank and reciprocal-rank score.
 */
public record RagRankedDocument(Document document,
                                String source,
                                int rank,
                                double score) {

    public String key() {
        if (StringUtils.hasText(document.getId())) {
            return document.getId();
        }
        Map<String, Object> metadata = document.getMetadata();
        Object documentSource = metadata.get("source");
        Object chunkIndex = metadata.get("chunk_index");
        if (documentSource != null && chunkIndex != null) {
            return documentSource + "#" + chunkIndex;
        }
        if (documentSource != null && StringUtils.hasText(document.getText())) {
            return documentSource + "#" + document.getText().hashCode();
        }
        return StringUtils.hasText(document.getText())
                ? String.valueOf(document.getText().hashCode())
                : document.getId();
    }
}
