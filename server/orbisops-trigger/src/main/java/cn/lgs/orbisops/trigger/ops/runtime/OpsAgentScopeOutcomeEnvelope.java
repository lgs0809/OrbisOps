package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Parses the machine-readable outcome envelope appended to a ReAct final answer. */
final class OpsAgentScopeOutcomeEnvelope {

    static final String VISIBLE_START = "<ops_answer>";
    static final String VISIBLE_END = "</ops_answer>";
    static final String START = "<ops_outcome>";
    static final String END = "</ops_outcome>";

    private static final Set<String> VERIFICATION_STATUSES =
            Set.of("NOT_APPLICABLE", "SUCCEEDED", "FAILED", "INSUFFICIENT");
    private static final Set<String> EVIDENCE_COMPLETENESS =
            Set.of("NOT_APPLICABLE", "COMPLETE", "PARTIAL", "INSUFFICIENT");
    private static final Set<String> OUTCOME_KEYS = Set.of(
            "requiresAction", "verificationStatus", "abstained", "evidenceCompleteness");

    Parsed parse(String output) {
        if (!StringUtils.hasText(output)) return new Parsed(output == null ? "" : output, Map.of(), false);
        int start = output.lastIndexOf(START);
        if (start < 0) return parseTrailingKeyValueBlock(output);
        int end = output.indexOf(END, start + START.length());
        if (end < 0) {
            Parsed trailing = parseTrailingKeyValueBlock(output);
            return trailing.present()
                    ? trailing
                    : new Parsed(stripVisibleMarkers(output), Map.of(), false);
        }
        String json = output.substring(start + START.length(), end).trim();
        Map<String, Object> normalized = normalize(json);
        if (normalized.isEmpty()) {
            Parsed trailing = parseTrailingKeyValueBlock(output);
            return trailing.present()
                    ? trailing
                    : new Parsed(stripVisibleMarkers(output), Map.of(), false);
        }
        String visible = stripVisibleMarkers(
                (output.substring(0, start) + output.substring(end + END.length())).trim());
        return new Parsed(visible, normalized, true);
    }

    private Parsed parseTrailingKeyValueBlock(String output) {
        String[] lines = stripOutcomeMarkers(output).split("\\R", -1);
        Map<String, Object> values = new LinkedHashMap<>();
        int blockStart = lines.length;
        for (int index = lines.length - 1; index >= 0; index--) {
            String line = lines[index].trim();
            if (line.isEmpty() && values.isEmpty()) continue;
            int separator = line.indexOf('=');
            if (separator <= 0) break;
            String key = line.substring(0, separator).trim();
            if (!OUTCOME_KEYS.contains(key) || values.containsKey(key)) break;
            values.put(key, line.substring(separator + 1).trim());
            blockStart = index;
        }
        if (values.size() != OUTCOME_KEYS.size()) {
            return new Parsed(stripVisibleMarkers(output), Map.of(), false);
        }
        Map<String, Object> normalized = normalizeValues(values);
        if (normalized.isEmpty()) return new Parsed(stripVisibleMarkers(output), Map.of(), false);
        StringBuilder visible = new StringBuilder();
        for (int index = 0; index < blockStart; index++) {
            if (visible.length() > 0) visible.append('\n');
            visible.append(lines[index]);
        }
        return new Parsed(stripVisibleMarkers(visible.toString().trim()), normalized, true);
    }

    private String stripOutcomeMarkers(String output) {
        if (output == null || output.isEmpty()) return output == null ? "" : output;
        return output.replace(START, "").replace(END, "");
    }

    private String stripVisibleMarkers(String output) {
        if (output == null || output.isEmpty()) return output == null ? "" : output;
        return output.replace(VISIBLE_START, "").replace(VISIBLE_END, "").trim();
    }

    private Map<String, Object> normalize(String body) {
        Map<String, Object> values = parseValues(body);
        return normalizeValues(values);
    }

    private Map<String, Object> normalizeValues(Map<String, Object> values) {
        if (values == null || values.isEmpty()) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        putBoolean(result, "requiresAction", values.get("requiresAction"));
        putEnum(result, "verificationStatus", values.get("verificationStatus"), VERIFICATION_STATUSES);
        putBoolean(result, "abstained", values.get("abstained"));
        putEnum(result, "evidenceCompleteness", values.get("evidenceCompleteness"), EVIDENCE_COMPLETENESS);
        return result.size() == 4 ? Map.copyOf(result) : Map.of();
    }

    private Map<String, Object> parseValues(String body) {
        if (!StringUtils.hasText(body)) return Map.of();
        if (body.trim().startsWith("{")) {
            try {
                JSONObject object = JSON.parseObject(body);
                return object == null ? Map.of() : object;
            } catch (RuntimeException ignored) {
                return Map.of();
            }
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (String line : body.split("\\R")) {
            int separator = line.indexOf('=');
            if (separator <= 0) continue;
            values.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
        }
        return values;
    }

    private void putEnum(Map<String, Object> target, String key, Object value, Set<String> allowed) {
        String normalized = value == null ? "" : String.valueOf(value).trim().toUpperCase(Locale.ROOT);
        if (allowed.contains(normalized)) target.put(key, normalized);
    }

    private void putBoolean(Map<String, Object> target, String key, Object value) {
        if (value instanceof Boolean bool) {
            target.put(key, bool);
            return;
        }
        String normalized = value == null ? "" : String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized) || "false".equals(normalized)) {
            target.put(key, Boolean.parseBoolean(normalized));
        }
    }

    record Parsed(String visibleOutput, Map<String, Object> outcome, boolean present) {
    }
}
