package cn.lgs.orbisops.application.rag;

import java.util.List;

/** Typed command for creating or updating one RAG quality evaluation case. */
public record RagQualityCaseSaveCommand(
        Long id,
        String caseName,
        String query,
        String knowledgeTag,
        List<String> expectedKeywords,
        int topK,
        boolean enabled) {

    public RagQualityCaseSaveCommand {
        RagQualityEvalCase normalized = new RagQualityEvalCase(
                caseName,
                query,
                knowledgeTag,
                expectedKeywords,
                topK);
        caseName = normalized.caseName();
        query = normalized.query();
        knowledgeTag = normalized.knowledgeTag();
        expectedKeywords = normalized.expectedKeywords();
        topK = normalized.topK();
    }
}
