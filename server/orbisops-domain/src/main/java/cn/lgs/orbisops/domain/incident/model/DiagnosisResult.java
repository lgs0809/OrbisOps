package cn.lgs.orbisops.domain.incident.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Shared product contract for diagnosis surfaces (Chat, Incident, Channel and Dashboard).
 * It deliberately contains conclusions and evidence references, never model chain-of-thought.
 */
public record DiagnosisResult(
        String summary,
        List<String> impact,
        List<Fact> facts,
        List<Inference> inferences,
        List<String> excludedHypotheses,
        List<String> unknowns,
        List<String> recommendations,
        List<SourceStatus> sourceStatus,
        EvidenceCompleteness evidenceCompleteness,
        Confidence confidence,
        boolean requiresAction,
        String suggestedNextAction) {

    public DiagnosisResult {
        summary = text(summary);
        impact = copy(impact);
        facts = facts == null ? List.of() : List.copyOf(facts);
        inferences = inferences == null ? List.of() : List.copyOf(inferences);
        excludedHypotheses = copy(excludedHypotheses);
        unknowns = copy(unknowns);
        recommendations = copy(recommendations);
        sourceStatus = sourceStatus == null ? List.of() : List.copyOf(sourceStatus);
        evidenceCompleteness = evidenceCompleteness == null ? EvidenceCompleteness.INSUFFICIENT : evidenceCompleteness;
        confidence = confidence == null ? Confidence.LOW : confidence;
        suggestedNextAction = text(suggestedNextAction);
        validate(facts, inferences);
    }

    public enum EvidenceCompleteness {
        COMPLETE,
        PARTIAL,
        INSUFFICIENT
    }

    public enum Confidence {
        HIGH,
        MEDIUM,
        LOW
    }

    /** Legacy UI compatibility state. Query execution and business assessment are authoritative. */
    public enum SourceState {
        NOT_CONFIGURED,
        UNAVAILABLE,
        NOT_QUERIED,
        UNKNOWN,
        NORMAL,
        ABNORMAL
    }

    public enum SourceQueryStatus {
        NOT_CONFIGURED,
        NOT_QUERIED,
        SUCCEEDED,
        FAILED,
        INSUFFICIENT
    }

    public enum SourceAssessment {
        NORMAL,
        ABNORMAL,
        UNKNOWN
    }

    public record EvidenceRef(String evidenceRef, String resultId, String outputHash) {
        public EvidenceRef {
            evidenceRef = required(evidenceRef, "DIAGNOSIS_EVIDENCE_REF_REQUIRED");
            resultId = required(resultId, "DIAGNOSIS_RESULT_ID_REQUIRED");
            outputHash = required(outputHash, "DIAGNOSIS_OUTPUT_HASH_REQUIRED");
        }
    }

    public record Fact(String factId, String statement, List<EvidenceRef> evidenceRefs) {
        public Fact {
            factId = required(factId, "DIAGNOSIS_FACT_ID_REQUIRED");
            statement = required(statement, "DIAGNOSIS_FACT_STATEMENT_REQUIRED");
            evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
            if (evidenceRefs.isEmpty()) {
                throw new IllegalArgumentException("DIAGNOSIS_FACT_EVIDENCE_REQUIRED:" + factId);
            }
        }
    }

    public record Inference(String statement, List<String> supports) {
        public Inference {
            statement = required(statement, "DIAGNOSIS_INFERENCE_STATEMENT_REQUIRED");
            supports = copy(supports);
            if (supports.isEmpty()) throw new IllegalArgumentException("DIAGNOSIS_INFERENCE_SUPPORT_REQUIRED");
        }
    }

    public record SourceStatus(
            String sourceId,
            String sourceName,
            SourceQueryStatus queryStatus,
            SourceAssessment assessment,
            SourceState state,
            String detail) {
        public SourceStatus {
            sourceId = required(sourceId, "DIAGNOSIS_SOURCE_ID_REQUIRED");
            sourceName = text(sourceName);
            queryStatus = queryStatus == null ? SourceQueryStatus.NOT_QUERIED : queryStatus;
            assessment = assessment == null ? SourceAssessment.UNKNOWN : assessment;
            state = state == null ? SourceState.UNKNOWN : state;
            detail = text(detail);
        }
    }

    public static DiagnosisResult insufficient(String summary, String unknown, boolean requiresAction, String nextAction) {
        return new DiagnosisResult(
                summary,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                text(unknown).isBlank() ? List.of() : List.of(text(unknown)),
                List.of(),
                List.of(),
                EvidenceCompleteness.INSUFFICIENT,
                Confidence.LOW,
                requiresAction,
                nextAction);
    }

    private static void validate(List<Fact> facts, List<Inference> inferences) {
        Set<String> factIds = new LinkedHashSet<>();
        for (Fact fact : facts) {
            if (!factIds.add(fact.factId())) {
                throw new IllegalArgumentException("DIAGNOSIS_FACT_ID_DUPLICATED:" + fact.factId());
            }
        }
        for (Inference inference : inferences) {
            for (String support : inference.supports()) {
                if (!factIds.contains(support)) {
                    throw new IllegalArgumentException("DIAGNOSIS_INFERENCE_SUPPORT_UNKNOWN:" + support);
                }
            }
        }
    }

    private static List<String> copy(List<String> values) {
        if (values == null) return List.of();
        return values.stream().map(DiagnosisResult::text).filter(value -> !value.isBlank()).toList();
    }

    private static String required(String value, String reason) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reason);
        return normalized;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
