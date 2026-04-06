package cn.lgs.orbisops.domain.rageval.service;

import cn.lgs.orbisops.domain.rageval.model.RagEvalProbeAssessment;
import cn.lgs.orbisops.domain.rageval.model.RagEvalRunAssessment;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Pure domain policy for RAG probe scoring, pass gates and run aggregation. */
public final class RagQualityAssessmentPolicy {

    public static final double PASS_COVERAGE_THRESHOLD = 0.6D;

    public RagEvalProbeAssessment assessProbe(
            List<String> hitContents,
            String query,
            List<String> expectedKeywords) {
        List<String> contents = hitContents == null ? List.of() : hitContents;
        List<String> keywords = normalizedKeywords(expectedKeywords);
        List<Double> hitScores = contents.stream()
                .map(content -> scoreContent(content, query, keywords))
                .toList();
        String joined = String.join("\n", contents);
        List<String> covered = keywords.stream()
                .filter(keyword -> containsIgnoreCase(joined, keyword))
                .toList();
        List<String> missing = keywords.stream()
                .filter(keyword -> !containsIgnoreCase(joined, keyword))
                .toList();
        double coverage = keywords.isEmpty()
                ? (contents.isEmpty() ? 0D : 1D)
                : covered.size() * 1D / keywords.size();
        double reciprocalRank = reciprocalRank(hitScores);
        boolean passed = !contents.isEmpty() && coverage >= PASS_COVERAGE_THRESHOLD;
        return new RagEvalProbeAssessment(
                contents.size(),
                hitScores,
                covered,
                missing,
                coverage,
                reciprocalRank,
                passed,
                recommendation(contents.size(), coverage, missing));
    }

    public RagEvalRunAssessment aggregate(List<RagEvalProbeAssessment> assessments) {
        List<RagEvalProbeAssessment> values = assessments == null ? List.of() : assessments;
        if (values.isEmpty()) {
            return new RagEvalRunAssessment(0, 0D, 0D, 0D, 0L);
        }
        long hitCases = values.stream().filter(value -> value.hitCount() > 0).count();
        long passed = values.stream().filter(RagEvalProbeAssessment::passed).count();
        double coverage = values.stream()
                .mapToDouble(RagEvalProbeAssessment::keywordCoverage)
                .average()
                .orElse(0D);
        double mrr = values.stream()
                .mapToDouble(RagEvalProbeAssessment::reciprocalRank)
                .average()
                .orElse(0D);
        return new RagEvalRunAssessment(
                values.size(),
                hitCases * 1D / values.size(),
                coverage,
                mrr,
                passed);
    }

    public double scoreContent(
            String content,
            String query,
            List<String> expectedKeywords) {
        double score = 0D;
        if (containsIgnoreCase(content, query)) score += 2D;
        for (String keyword : normalizedKeywords(expectedKeywords)) {
            if (containsIgnoreCase(content, keyword)) score += 1D;
        }
        return score;
    }

    private double reciprocalRank(List<Double> hitScores) {
        for (int index = 0; index < hitScores.size(); index++) {
            Double score = hitScores.get(index);
            if (score != null && score > 0D) return 1D / (index + 1);
        }
        return 0D;
    }

    private String recommendation(
            int hitCount,
            double coverage,
            List<String> missingKeywords) {
        if (hitCount <= 0) {
            return "未命中 chunk，建议检查知识库标签、文档是否入库，以及查询词是否需要补充业务别名。";
        }
        if (coverage < PASS_COVERAGE_THRESHOLD && !missingKeywords.isEmpty()) {
            return "命中结果覆盖不足，建议补充缺失关键词相关文档或调整文档切分粒度："
                    + String.join("、", missingKeywords);
        }
        return "命中结果可用，且已复用线上 RagAnswerAdvisor 检索链路。";
    }

    private List<String> normalizedKeywords(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = text(value);
            if (!normalized.isBlank()) result.add(normalized);
        }
        return List.copyOf(new ArrayList<>(result));
    }

    private boolean containsIgnoreCase(String source, String keyword) {
        String expected = text(keyword);
        if (expected.isBlank() || source == null || source.isBlank()) return false;
        return source.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
