package cn.lgs.orbisops.trigger.ops;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Maps validated SubAgent THINK/REVIEW JSON onto typed decisions. */
final class OpsSubAgentDecisionJsonMapper {

    OpsSubAgentDecision toDecision(JSONObject json, OpsSubAgentDecision fallback) {
        return new OpsSubAgentDecision(
                true,
                text(json, "reason", fallback.reason()),
                clampInt(json.getInteger("rangeMinutes"), 1, 1440, fallback.rangeMinutes()),
                validPromWindow(text(json, "promWindow", fallback.promWindow()), fallback.promWindow()),
                json.getBoolean("includeRecentLogs") == null
                        ? fallback.includeRecentLogs()
                        : json.getBoolean("includeRecentLogs"),
                validRetrievalMode(
                        text(json, "retrievalMode", fallback.retrievalMode()),
                        fallback.retrievalMode()),
                text(json, "queryFocus", fallback.queryFocus()),
                json.getBoolean("requireExactFilters") == null
                        ? fallback.requireExactFilters()
                        : json.getBoolean("requireExactFilters"),
                list(json.getJSONArray("expectedEvidence"), fallback.expectedEvidence()));
    }

    OpsAgentReview toReview(JSONObject json, OpsAgentReview fallback) {
        return new OpsAgentReview(
                true,
                validStatus(text(json, "status", fallback.status()), fallback.status()),
                text(json, "summary", fallback.summary()),
                list(json.getJSONArray("gaps"), fallback.gaps()),
                list(json.getJSONArray("suggestedAdjustments"), fallback.suggestedAdjustments()),
                json.getBoolean("shouldRetry") == null
                        ? fallback.shouldRetry()
                        : json.getBoolean("shouldRetry"),
                clampDouble(json.getDouble("confidence"), 0D, 1D, fallback.confidence()));
    }

    private String text(JSONObject json, String key, String defaultValue) {
        String value = json.getString(key);
        return hasText(value) ? value : defaultValue;
    }

    private List<String> list(JSONArray array, List<String> defaultValue) {
        if (array == null || array.isEmpty()) {
            return defaultValue == null ? new ArrayList<>() : defaultValue;
        }
        List<String> values = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            String value = array.getString(i);
            if (hasText(value)) {
                values.add(value);
            }
        }
        return values.isEmpty()
                ? Optional.ofNullable(defaultValue).orElse(List.of())
                : values;
    }

    private int clampInt(Integer value, int min, int max, Integer defaultValue) {
        int safe = value == null ? Optional.ofNullable(defaultValue).orElse(min) : value;
        return Math.max(min, Math.min(max, safe));
    }

    private Double clampDouble(Double value, double min, double max, Double defaultValue) {
        double safe = value == null ? Optional.ofNullable(defaultValue).orElse(min) : value;
        return Math.max(min, Math.min(max, safe));
    }

    private String validPromWindow(String value, String fallback) {
        return value != null && value.matches("^(1|3|5|10|15|30)m$|^1h$")
                ? value
                : fallback;
    }

    private String validRetrievalMode(String value, String fallback) {
        return "vector".equals(value)
                || "bm25".equals(value)
                || "hybrid".equals(value)
                || "auto".equals(value)
                ? value
                : fallback;
    }

    private String validStatus(String value, String fallback) {
        return "FOUND".equals(value)
                || "NOT_FOUND".equals(value)
                || "INSUFFICIENT".equals(value)
                || "BLOCKED".equals(value)
                || "ERROR".equals(value)
                ? value
                : fallback;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
