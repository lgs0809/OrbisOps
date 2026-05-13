package cn.lgs.orbisops.application.rag;

import java.util.List;

/** Typed application view of one persisted RAG quality evaluation case. */
public record RagQualityCaseRecord(
        Long id,
        String caseName,
        String query,
        String knowledgeTag,
        List<String> expectedKeywords,
        int topK,
        boolean enabled) {

    public RagQualityCaseRecord {
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

    public RagQualityEvalCase toEvalCase() {
        return new RagQualityEvalCase(
                caseName,
                query,
                knowledgeTag,
                expectedKeywords,
                topK);
    }
}
