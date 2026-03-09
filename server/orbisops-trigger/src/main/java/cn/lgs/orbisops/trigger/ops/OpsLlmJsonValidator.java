package cn.lgs.orbisops.trigger.ops;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime schema checks for LLM JSON decisions.
 */
public final class OpsLlmJsonValidator {

    private static final java.util.Set<String> VALID_STATUS = java.util.Set.of("FOUND", "NOT_FOUND", "INSUFFICIENT", "BLOCKED", "ERROR");
    private static final java.util.Set<String> VALID_PROM_WINDOW = java.util.Set.of("1m", "3m", "5m", "10m", "15m", "30m", "1h");
    private static final java.util.Set<String> VALID_RETRIEVAL_MODE = java.util.Set.of("auto", "vector", "bm25", "hybrid");
    private static final java.util.Set<String> VALID_REFLECTION_DECISION = java.util.Set.of("continue", "stop");

    private OpsLlmJsonValidator() {
    }

    public static List<String> validatePlanner(JSONObject json) {
        return validatePlanner(json, true);
    }

    public static List<String> validatePlanner(JSONObject json, boolean requireTasks) {
        return validatePlanner(json, requireTasks, true);
    }

    public static List<String> validatePlanner(JSONObject json, boolean requireTasks, boolean allowLegacySource) {
        List<String> errors = new ArrayList<>();
        if (json == null) {
            errors.add("planner JSON is null");
            return errors;
        }
        requireText(errors, json, "intent");
        requireText(errors, json, "reason");
        if (json.containsKey("changeRequested")
                && !(json.get("changeRequested") instanceof Boolean)) {
            errors.add("changeRequested must be a boolean");
        }
        JSONArray tasks = json.getJSONArray("tasks");
        if (requireTasks && (tasks == null || tasks.isEmpty())) {
            errors.add("tasks must be a non-empty array");
        } else {
            validateTasks(errors, tasks, "tasks", allowLegacySource);
        }
        validateTasks(errors, json.getJSONArray("conditionalTasks"), "conditionalTasks", allowLegacySource);
        validateTasks(errors, json.getJSONArray("skippedTasks"), "skippedTasks", allowLegacySource);
        return errors;
    }

    public static List<String> validateReplanner(JSONObject json) {
        return validateReplanner(json, true);
    }

    public static List<String> validateReplanner(JSONObject json, boolean allowLegacySource) {
        List<String> errors = new ArrayList<>();
        if (json == null) {
            errors.add("replanner JSON is null");
            return errors;
        }
        requireText(errors, json, "intent");
        requireText(errors, json, "reason");
        validateTasks(errors, json.getJSONArray("tasks"), "tasks", allowLegacySource);
        validateTasks(errors, json.getJSONArray("conditionalTasks"), "conditionalTasks", allowLegacySource);
        validateTasks(errors, json.getJSONArray("skippedTasks"), "skippedTasks", allowLegacySource);
        return errors;
    }

    public static List<String> validateSubAgentDecision(JSONObject json) {
        List<String> errors = new ArrayList<>();
        if (json == null) {
            errors.add("sub-agent decision JSON is null");
            return errors;
        }
        requireText(errors, json, "reason");
        requireText(errors, json, "queryFocus");
        Integer rangeMinutes = json.getInteger("rangeMinutes");
        if (rangeMinutes != null && (rangeMinutes < 1 || rangeMinutes > 1440)) {
            errors.add("rangeMinutes must be between 1 and 1440");
        }
        String promWindow = json.getString("promWindow");
        if (StringUtils.hasText(promWindow) && !VALID_PROM_WINDOW.contains(promWindow)) {
            errors.add("promWindow is invalid: " + promWindow);
        }
        String retrievalMode = json.getString("retrievalMode");
        if (StringUtils.hasText(retrievalMode) && !VALID_RETRIEVAL_MODE.contains(retrievalMode)) {
            errors.add("retrievalMode is invalid: " + retrievalMode);
        }
        if (json.containsKey("expectedEvidence") && !(json.get("expectedEvidence") instanceof JSONArray)) {
            errors.add("expectedEvidence must be an array");
        }
        return errors;
    }

    public static List<String> validateSubAgentReview(JSONObject json) {
        List<String> errors = new ArrayList<>();
        if (json == null) {
            errors.add("sub-agent review JSON is null");
            return errors;
        }
        String status = json.getString("status");
        if (!VALID_STATUS.contains(status)) {
            errors.add("status is invalid: " + status);
        }
        requireText(errors, json, "summary");
        Double confidence = json.getDouble("confidence");
        if (confidence != null && (confidence < 0D || confidence > 1D)) {
            errors.add("confidence must be between 0 and 1");
        }
        if (json.containsKey("gaps") && !(json.get("gaps") instanceof JSONArray)) {
            errors.add("gaps must be an array");
        }
        if (json.containsKey("suggestedAdjustments") && !(json.get("suggestedAdjustments") instanceof JSONArray)) {
            errors.add("suggestedAdjustments must be an array");
        }
        return errors;
    }

    public static List<String> validateMainReflection(JSONObject json) {
        List<String> errors = new ArrayList<>();
        if (json == null) {
            errors.add("main reflection JSON is null");
            return errors;
        }
        String decision = json.getString("decision");
        if (!VALID_REFLECTION_DECISION.contains(decision)) {
            errors.add("decision is invalid: " + decision);
        }
        validateTasks(errors, json.getJSONArray("addTasks"), "addTasks");
        return errors;
    }

    private static void validateTasks(List<String> errors, JSONArray tasks, String fieldName) {
        validateTasks(errors, tasks, fieldName, true);
    }

    private static void validateTasks(List<String> errors, JSONArray tasks, String fieldName, boolean allowLegacySource) {
        if (tasks == null) {
            return;
        }
        for (int i = 0; i < tasks.size(); i++) {
            JSONObject item = tasks.getJSONObject(i);
            if (item == null) {
                errors.add(fieldName + "[" + i + "] must be an object");
                continue;
            }
            if (!StringUtils.hasText(item.getString("routeKey"))
                    && (!allowLegacySource || !StringUtils.hasText(item.getString("source")))) {
                errors.add(fieldName + "[" + i + "].routeKey is required");
            }
            requireText(errors, item, "agent", fieldName + "[" + i + "]");
            requireText(errors, item, "goal", fieldName + "[" + i + "]");
            requireText(errors, item, "reason", fieldName + "[" + i + "]");
        }
    }

    private static void requireText(List<String> errors, JSONObject json, String key) {
        requireText(errors, json, key, "");
    }

    private static void requireText(List<String> errors, JSONObject json, String key, String prefix) {
        if (!StringUtils.hasText(json.getString(key))) {
            errors.add((StringUtils.hasText(prefix) ? prefix + "." : "") + key + " is required");
        }
    }

}
