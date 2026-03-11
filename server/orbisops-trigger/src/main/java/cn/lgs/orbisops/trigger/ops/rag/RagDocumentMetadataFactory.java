package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;
import cn.lgs.orbisops.domain.knowledge.rag.service.RagParsePolicy;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/** Stable base metadata projection for all structured parser routes. */
final class RagDocumentMetadataFactory {

    private static final String PARSER_VERSION = "ops-rag-structured-v1";

    private final RagVisualFallbackPolicy visualFallbackPolicy;

    RagDocumentMetadataFactory(RagVisualFallbackPolicy visualFallbackPolicy) {
        this.visualFallbackPolicy = visualFallbackPolicy;
    }

    Map<String, Object> create(
            String ragName,
            String knowledgeTag,
            RagFileResource file,
            String fileName,
            RagDocumentKind kind,
            RagParsePolicy parsePolicy) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("knowledge", knowledgeTag);
        metadata.put("rag_name", ragName);
        metadata.put("source", fileName);
        metadata.put("file_name", fileName);
        if (StringUtils.hasText(file.getContentType())) {
            metadata.put("content_type", file.getContentType());
        }
        metadata.put("file_size", file.getSize());
        metadata.put("document_type", kind.documentType());
        metadata.put("parser_version", PARSER_VERSION);
        metadata.put("segmentation_mode", "structure_first");
        metadata.put("structure_preserved", true);
        metadata.put("knowledge_scope", parsePolicy.scope());
        metadata.put("project_id", parsePolicy.projectId());
        metadata.put("max_segment_chars", parsePolicy.maxSegmentChars());
        metadata.put("hard_split_overlap_chars", parsePolicy.hardSplitOverlapChars());
        metadata.put(
                "high_value_candidate",
                visualFallbackPolicy.highValueCandidate(knowledgeTag, fileName));
        return metadata;
    }
}
