package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/** Renders deterministic edge choice JSON and route-output contracts. */
final class OpsGraphEdgeChoiceRenderer {

    private OpsGraphEdgeChoiceRenderer() {
    }

    static String edgeChoiceJson(
            OpsAgentDefinition definition,
            OpsWorkflowNode node,
            List<OpsGraphEdge> outgoing,
            Predicate<OpsGraphEdge> activeMatcher,
            Function<OpsGraphEdge, Map<String, Object>> edgeMetadataProvider) {
        if (outgoing == null || outgoing.isEmpty()) return "";
        JSONObject root = orderedJson();
        root.put("nodeId", node == null ? "" : value(node.getNodeId(), ""));
        root.put("agent", node == null ? "" : value(node.getAgent(), ""));
        root.put(
                "choices",
                edgeChoices(
                        definition,
                        outgoing,
                        activeMatcher,
                        edgeMetadataProvider));
        return JSON.toJSONString(root);
    }

    static JSONArray edgeChoices(
            OpsAgentDefinition definition,
            List<OpsGraphEdge> edges,
            Predicate<OpsGraphEdge> activeMatcher,
            Function<OpsGraphEdge, Map<String, Object>> edgeMetadataProvider) {
        Map<String, OpsWorkflowNode> nodeById =
                OpsGraphEdgeCatalog.nodeById(definition);
        JSONArray choices = new JSONArray();
        for (OpsGraphEdge edge : edges) {
            JSONObject choice = orderedJson();
            OpsWorkflowNode target = nodeById.get(edge.getTo());
            String conditionType = value(edge.getConditionType(), "always");
            String condition = value(edge.getCondition(), "always");
            String routeKey = routeKey(conditionType, condition);
            choice.put("edge", edge.getFrom() + "->" + edge.getTo());
            choice.put("targetNodeId", edge.getTo());
            choice.put(
                    "targetAgent",
                    target == null ? "" : value(target.getAgent(), ""));
            choice.put(
                    "targetMode",
                    target == null ? "" : value(target.getMode(), ""));
            choice.put(
                    "targetMcpIds",
                    target == null || target.getMcpIds() == null
                            ? List.of()
                            : target.getMcpIds());
            choice.put(
                    "targetRagEnabled",
                    target != null && Boolean.TRUE.equals(target.getRagEnabled()));
            choice.put(
                    "targetDescription",
                    target == null ? "" : value(target.getDescription(), ""));
            choice.put("conditionType", conditionType);
            choice.put("condition", condition);
            choice.put("routeKey", routeKey);
            choice.put(
                    "routeOutputHint",
                    routeOutputHint(conditionType, condition));
            choice.put(
                    "active",
                    activeMatcher != null && activeMatcher.test(edge));
            choice.put("feedback", Boolean.TRUE.equals(edge.getFeedback()));
            choice.put(
                    "defaultEdge",
                    Boolean.TRUE.equals(edge.getDefaultEdge())
                            || "default".equalsIgnoreCase(conditionType));
            choice.put("description", choiceDescription(edge, target));
            if (edge.getDataMapping() != null
                    && !edge.getDataMapping().isEmpty()) {
                choice.put("dataMapping", edge.getDataMapping());
            }
            Map<String, Object> metadata = edgeMetadataProvider == null
                    ? Map.of()
                    : edgeMetadataProvider.apply(edge);
            if (metadata != null && !metadata.isEmpty()) {
                choice.put("runtime", metadata);
            }
            choices.add(choice);
        }
        return choices;
    }

    static JSONObject routerRequiredOutput(String inputKey, String routeMode) {
        JSONObject output = orderedJson();
        String key = StringUtils.hasText(inputKey) ? inputKey : "plan";
        output.put("field", key);
        output.put(
                "routeMode",
                StringUtils.hasText(routeMode) ? routeMode : "multi");
        if ("plan".equals(key)) {
            output.put(
                    "shape",
                    "JSON object with selectedRoutes array, or tasks array where each tasks[].routeKey matches one route key.");
            output.put(
                    "example",
                    Map.of(
                            "selectedRoutes",
                            List.of("prometheus"),
                            "tasks",
                            List.of(Map.of(
                                    "routeKey",
                                    "prometheus",
                                    "reason",
                                    "需要指标证据"))));
        } else if ("review_decision".equals(key)) {
            output.put(
                    "shape",
                    "JSON object or text containing review_decision; use needs:<route> for follow-up, or final_report/default when evidence is enough.");
            output.put(
                    "example",
                    Map.of("review_decision", List.of("needs:prometheus")));
        } else {
            output.put(
                    "shape",
                    "JSON object containing the configured field as a route key or route key array.");
            output.put("example", Map.of(key, List.of("prometheus")));
        }
        return output;
    }

    static JSONObject orderedJson() {
        return new JSONObject(new LinkedHashMap<>());
    }

    private static String choiceDescription(
            OpsGraphEdge edge,
            OpsWorkflowNode target) {
        String edgeDescription = edge == null
                ? ""
                : value(edge.getDescription(), "");
        String targetDescription = target == null
                ? ""
                : value(target.getDescription(), "");
        if (StringUtils.hasText(edgeDescription)
                && StringUtils.hasText(targetDescription)) {
            return edgeDescription + "；后续节点：" + targetDescription;
        }
        return StringUtils.hasText(edgeDescription)
                ? edgeDescription
                : targetDescription;
    }

    private static String routeKey(String conditionType, String condition) {
        String normalized = conditionType == null
                ? ""
                : conditionType.trim().toLowerCase();
        if ("default".equals(normalized)) return "__default__";
        return condition;
    }

    private static String routeOutputHint(
            String conditionType,
            String condition) {
        String normalized = conditionType == null
                ? ""
                : conditionType.trim().toLowerCase();
        if ("route_match".equals(normalized)) {
            return "selectedRoutes[]="
                    + condition
                    + " 或 tasks[].routeKey="
                    + condition;
        }
        if ("review_decision".equals(normalized)) {
            if ("replan_required".equalsIgnoreCase(condition)) {
                return "review_decision=replan_required";
            }
            String route = condition.replace("needs:", "");
            return "review_decision="
                    + condition
                    + " 或 tasks[].routeKey="
                    + route;
        }
        if ("default".equals(normalized)) {
            return "无需选择具体分支时走默认出口";
        }
        return condition;
    }

    private static String value(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
