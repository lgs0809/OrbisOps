package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.domain.agentdefinition.service.DirectActionDataPolicy;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.trigger.ops.OpsAlertWebhookProtocolService.AlertView;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded JSON template adapter. Webhook contents remain data, never template source. */
final class OpsAlertWorkflowInputRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z][A-Za-z0-9]*)}");
    private final DirectActionDataPolicy structuredData = new DirectActionDataPolicy();

    String render(String template, OpsAlertTriggerRule rule, AlertView alert, String eventType, long count) {
        var data = structuredData.parseObject(template);
        var fields = Map.<String, Object>ofEntries(
                Map.entry("projectId", text(rule.getProjectId())),
                Map.entry("alertName", text(alert.alertName())),
                Map.entry("severity", text(alert.severity())),
                Map.entry("service", text(alert.service())),
                Map.entry("alertStatus", text(alert.status())),
                Map.entry("eventType", text(eventType)),
                Map.entry("occurrenceCount", Math.max(1, count)),
                Map.entry("startsAt", text(alert.startsAt())),
                Map.entry("fingerprint", text(alert.fingerprint())),
                Map.entry("summary", text(alert.annotation("summary"))),
                Map.entry("description", text(alert.annotation("description"))),
                Map.entry("labels", alert.labels() == null ? Map.of() : alert.labels()),
                Map.entry("annotations", alert.annotations() == null ? Map.of() : alert.annotations()));
        String json = CanonicalJson.stringifyPreservingOrder(bindValue(data, fields));
        structuredData.parseObject(json);
        return json;
    }

    private Object bindValue(Object value, Map<String, Object> fields) {
        if (value instanceof Map<?, ?> source) {
            var result = new LinkedHashMap<String, Object>();
            source.forEach((key, item) -> result.put(String.valueOf(key), bindValue(item, fields)));
            return result;
        }
        if (value instanceof List<?> items) return items.stream().map(item -> bindValue(item, fields)).toList();
        if (!(value instanceof String template)) return value;
        var matcher = PLACEHOLDER.matcher(template);
        if (matcher.matches()) return binding(fields, matcher.group(1));
        // Single-pass substitution prevents alert contents from introducing more bindings.
        return matcher.replaceAll(match -> Matcher.quoteReplacement(String.valueOf(binding(fields, match.group(1)))));
    }

    private Object binding(Map<String, Object> fields, String key) {
        if (!fields.containsKey(key)) throw new IllegalArgumentException("ALERT_WORKFLOW_INPUT_PLACEHOLDER_UNKNOWN:" + key);
        return fields.get(key);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
