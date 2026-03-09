package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.application.rag.RagQualityCaseRecord;
import cn.lgs.orbisops.application.rag.RagQualityCaseResult;
import cn.lgs.orbisops.application.rag.RagQualityProbeResult;
import cn.lgs.orbisops.application.rag.RagQualityRunResult;
import cn.lgs.orbisops.application.rag.RagQualityScoredHit;
import cn.lgs.orbisops.domain.rageval.model.RagEvalProbeAssessment;
import cn.lgs.orbisops.domain.rageval.model.RagEvalRunAssessment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compatibility projection for typed RAG quality probe and run results. */
public final class OpsRagQualityEvalViewMapper {

    public List<Map<String, Object>> caseViews(List<RagQualityCaseRecord> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        return records.stream().map(this::caseView).toList();
    }

    public Map<String, Object> caseView(RagQualityCaseRecord record) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", record.id());
        data.put("caseName", record.caseName());
        data.put("query", record.query());
        data.put("knowledgeTag", record.knowledgeTag());
        data.put("expectedKeywords", record.expectedKeywords());
        data.put("topK", record.topK());
        data.put("enabled", record.enabled());
        return data;
    }

    public Map<String, Object> probeView(
            RagQualityProbeResult<Map<String, Object>> result) {
        RagEvalProbeAssessment assessment = result.assessment();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("query", result.command().query());
        data.put("knowledgeTag", result.command().knowledgeTag());
        data.put("retrievalMode", result.command().retrievalMode());
        data.put("retrievalChain", "RagAnswerAdvisor(vector+bm25+multimodal+RRF+rerank)");
        data.put("rerankEnabled", result.command().rerankEnabled());
        data.put("topK", result.command().topK());
        data.put("hitCount", assessment.hitCount());
        data.put("hits", result.hits().stream().map(this::hitView).toList());
        data.put("expectedKeywords", result.command().expectedKeywords());
        data.put("coveredKeywords", assessment.coveredKeywords());
        data.put("missingKeywords", assessment.missingKeywords());
        data.put("keywordCoverage", assessment.keywordCoverage());
        data.put("reciprocalRank", assessment.reciprocalRank());
        data.put("passed", assessment.passed());
        data.put("recommendation", assessment.recommendation());
        return data;
    }

    public Map<String, Object> runView(
            RagQualityRunResult<Map<String, Object>> result) {
        RagEvalRunAssessment assessment = result.assessment();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("caseCount", assessment.caseCount());
        data.put("hitRate", assessment.hitRate());
        data.put("averageKeywordCoverage", assessment.averageKeywordCoverage());
        data.put("mrr", assessment.meanReciprocalRank());
        data.put("passedCount", assessment.passedCount());
        data.put("results", result.caseResults().stream().map(this::caseView).toList());
        return data;
    }

    private Map<String, Object> caseView(
            RagQualityCaseResult<Map<String, Object>> result) {
        Map<String, Object> row = new LinkedHashMap<>(probeView(result.probeResult()));
        row.put("caseName", result.evalCase().caseName());
        row.put("mrrContribution", result.probeResult().assessment().reciprocalRank());
        row.put("passed", result.probeResult().assessment().passed());
        return row;
    }

    private Map<String, Object> hitView(
            RagQualityScoredHit<Map<String, Object>> scoredHit) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("rank", scoredHit.rank());
        row.put("chunkId", scoredHit.hit().chunkId());
        row.put("docName", scoredHit.hit().docName());
        row.put("knowledgeTag", scoredHit.hit().knowledgeTag());
        row.put("documentType", scoredHit.hit().documentType());
        row.put("chunkStrategy", scoredHit.hit().chunkStrategy());
        row.put("preview", abbreviate(scoredHit.hit().content(), 700));
        row.put("score", scoredHit.score());
        row.put("metadata", scoredHit.hit().metadata() == null
                ? Map.of()
                : scoredHit.hit().metadata());
        return row;
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength) + "...";
    }
}
