package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.springframework.util.StringUtils;
import cn.lgs.orbisops.domain.agentdefinition.service.DirectActionDataPolicy;
import cn.lgs.orbisops.domain.runtime.workflow.service.WorkflowObservabilityPolicy;
import cn.lgs.orbisops.domain.runtime.workflow.service.WorkflowChangeVerificationPolicy;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;

import java.time.Clock;

import java.util.LinkedHashMap;
import java.util.Map;

/** Router nodes project state or apply a declared deterministic evidence policy; edges select routes. */
final class OpsGraphRouterNodeExecutor {
    private final Clock clock;
    private final OpsWorkflowChangeContextReader changes;

    OpsGraphRouterNodeExecutor() { this(Clock.systemUTC()); }
    OpsGraphRouterNodeExecutor(Clock clock) { this(clock,null); }
    OpsGraphRouterNodeExecutor(Clock clock, OpsWorkflowChangeContextReader changes) { this.clock=clock; this.changes=changes; }

    OpsGraphNodeExecutionResult execute(OpsGraphNodeExecutionContext context) {
        if (context.node().getConfig()!=null && context.node().getConfig().get("changeVerification") instanceof Map<?,?> config) {
            return changeVerification(context,config);
        }
        if (context.node().getConfig() != null && context.node().getConfig().get("observability") instanceof Map<?, ?> policyConfig) {
            return observability(context, policyConfig);
        }
        String inputKey = configText(context.node(), "inputKey");
        Object selected = read(context.state(), inputKey);
        String inputFormat = configText(context.node(), "inputFormat");
        if (StringUtils.hasText(inputFormat)) {
            if (!"JSON".equals(inputFormat) || !StringUtils.hasText(inputKey)) {
                throw new IllegalArgumentException("WORKFLOW_ROUTER_INPUT_FORMAT_INVALID");
            }
            selected = declaredInput(context, "inputKey");
        } else if (selected == null) {
            selected = context.input();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        if (selected instanceof Map<?, ?> map) {
            map.forEach((key, value) -> {
                if (key != null) result.put(String.valueOf(key), value);
            });
        }
        String outputKey = StringUtils.hasText(context.node().getOutputKey())
                ? context.node().getOutputKey().trim()
                : "selectedRoutes";
        result.put(outputKey, selected == null ? "" : selected);
        // Lifecycle persists output under the node's outputKey. JSON projections must
        // survive that hand-off as JSON, rather than Map.toString() ("{key=value}").
        String output = "JSON".equals(inputFormat)
                ? CanonicalJson.stringifyPreservingOrder(selected)
                : selected == null ? "" : String.valueOf(selected);
        return new OpsGraphNodeExecutionResult(
                output,
                result);
    }

    private OpsGraphNodeExecutionResult changeVerification(OpsGraphNodeExecutionContext context,Map<?,?> config) {
        var policy=new WorkflowChangeVerificationPolicy();
        policy.validate(config);
        String operation=String.valueOf(config.get("operation"));
        Map<String,Object> value;
        switch (operation) {
            case "SELECT_CHANGE_REQUEST" -> {
                if (changes==null) throw new IllegalStateException("CHANGE_VERIFICATION_REPOSITORY_REQUIRED");
                value=changes.selected(context.request().getProjectId(), context.request().getMetadata());
            }
            case "CONTEXT_CHANGE" -> {
                if (changes==null) throw new IllegalStateException("CHANGE_VERIFICATION_REPOSITORY_REQUIRED");
                value=changes.read(context.request().getProjectId(),declaredInput(context, "requestInputKey"),clock.instant());
            }
            case "EXTEND_CHANGE" -> value=policy.extend(data(context,config.get("contextKey")),data(context,config.get("reportKey")),clock.instant());
            case "REVIEW_CHANGE" -> value=policy.review(data(context,config.get("contextKey")),data(context,config.get("beforeKey")),data(context,config.get("afterKey")),data(context,config.get("versionKey")));
            case "REPORT_CHANGE" -> value=policy.reportUnavailable(data(context,config.get("contextKey")));
            default -> throw new IllegalArgumentException("CHANGE_VERIFICATION_OPERATION_INVALID");
        }
        String output=SetHolder.REPORT_OPERATIONS.contains(operation) ? new OpsChangeVerificationReportFormatter().format(value) : CanonicalJson.stringifyPreservingOrder(value);
        return new OpsGraphNodeExecutionResult(output,Map.of("workflowData_"+config.get("outputKey"),value));
    }

    private static final class SetHolder {
        static final java.util.Set<String> REPORT_OPERATIONS=java.util.Set.of("REVIEW_CHANGE","REPORT_CHANGE");
    }

    private OpsGraphNodeExecutionResult observability(OpsGraphNodeExecutionContext context, Map<?, ?> config) {
        var policy = new WorkflowObservabilityPolicy();
        policy.validate(config);
        String operation = String.valueOf(config.get("operation"));
        Map<String, Object> value;
        if (operation.startsWith("WINDOW_")) {
            var input = declaredInput(context, "requestInputKey");
            value = new LinkedHashMap<>(policy.window(operation, input, context.request().getProjectId(), clock.instant()));
            // Provider input is a resource/window projection, not the entire internal evidence state.
            Map<String, Object> queryScope = new LinkedHashMap<>();
            for (String key : java.util.List.of("projectId", "environment", "serviceId", "startEpoch", "endEpoch")) {
                queryScope.put(key, value.get(key));
            }
            value.put("queryScope", Map.copyOf(queryScope));
        } else if (operation.equals("ALERT_FROM_INSPECTION")) {
            value = policy.alertFromInspection(data(context, config.get("reportKey")), context.request().getProjectId(), context.request().getRunId());
        } else if (operation.equals("SUMMARIZE_INSPECTION")) {
            value = policy.summarizeInspection(data(context, config.get("reportKey")), data(context, config.get("investigationKey")));
        } else {
            value = policy.review(operation, data(context, config.get("windowKey")), data(context, config.get("metricsKey")),
                    data(context, config.get("logsKey")), data(context, config.get("sqlKey")));
            if (operation.equals("REVIEW_ALERT")) value = policy.withExplain(value, data(context, config.get("windowKey")),
                    data(context, config.get("explainKey")));
        }
        String output = operation.startsWith("REVIEW_") || operation.equals("SUMMARIZE_INSPECTION") ? new OpsObservabilityReportFormatter().format(value)
                : CanonicalJson.stringifyPreservingOrder(value);
        return new OpsGraphNodeExecutionResult(output, Map.of("workflowData_" + config.get("outputKey"), value));
    }

    /** A published graph may consume an internal resolver node; a missing result never falls back to user text. */
    private Map<String, Object> declaredInput(OpsGraphNodeExecutionContext context, String configurationKey) {
        String inputKey = configText(context.node(), configurationKey);
        if (!StringUtils.hasText(inputKey)) {
            return new DirectActionDataPolicy().parseObject(context.request().getQuery());
        }
        if (!inputKey.matches("[A-Za-z_][A-Za-z0-9_]{0,63}")) {
            throw new IllegalArgumentException("WORKFLOW_INPUT_KEY_INVALID");
        }
        Object resolved = read(context.state(), inputKey);
        if (resolved == null) throw new IllegalArgumentException("WORKFLOW_RESOLVED_INPUT_MISSING");
        // Parsing a map through the same strict parser preserves size, depth and object checks.
        String json = resolved instanceof String text ? text : CanonicalJson.stringifyPreservingOrder(resolved);
        return new DirectActionDataPolicy().parseObject(json);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(OpsGraphNodeExecutionContext context, Object key) {
        Object value = key == null ? null : read(context.state(), "workflowData_" + key);
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private Object read(OverAllState state, String key) {
        if (state == null || !StringUtils.hasText(key)) return null;
        return state.value(key.trim()).orElse(null);
    }

    private String configText(OpsWorkflowNode node, String key) {
        if (node == null || node.getConfig() == null) return "";
        Object value = node.getConfig().get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }
}
