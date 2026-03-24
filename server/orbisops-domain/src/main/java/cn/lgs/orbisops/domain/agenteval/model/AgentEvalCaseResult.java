package cn.lgs.orbisops.domain.agenteval.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AgentEvalCaseResult(
        boolean passed,
        double score,
        List<String> reasonCodes,
        Map<String, Object> actual) {

    public AgentEvalCaseResult {
        score = Math.max(0D, Math.min(1D, score));
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
        actual = actual == null || actual.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(actual));
    }

    public Map<String, Object> toMap() {
        return Map.of(
                "passed", passed,
                "score", score,
                "reasonCodes", reasonCodes,
                "actual", actual);
    }
}
