package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/** Renders router choices and downstream router output contracts. */
final class OpsGraphRouterPromptContract {

    private static final int MAX_ROUTING_CHOICES_CHARS = 12000;

    private OpsGraphRouterPromptContract() {
    }

    static String routerChoiceContext(
            OpsAgentDefinition definition,
            String inputKey,
            Predicate<OpsGraphEdge> activeMatcher,
            Function<OpsGraphEdge, Map<String, Object>> edgeMetadataProvider) {
        List<OpsWorkflowNode> routers = Optional.ofNullable(
                definition == null ? null : definition.getNodes())
                .orElse(List.of()).stream()
                .filter(node -> "ROUTER".equals(normalizeType(node.getType())))
                .filter(node -> {
                    String configuredInputKey = configText(node, "inputKey", "plan");
                    return !StringUtils.hasText(inputKey)
                            || inputKey.equals(configuredInputKey);
                })
                .toList();
        if (routers.isEmpty()) return "";

        JSONObject root = OpsGraphEdgeChoiceRenderer.orderedJson();
        root.put("inputKey", inputKey);
        JSONArray routerArray = new JSONArray();
        for (OpsWorkflowNode router : routers) {
            List<OpsGraphEdge> outgoing =
                    OpsGraphEdgeCatalog.outgoingEdges(definition, router);
            if (outgoing.isEmpty()) continue;
            JSONObject item = OpsGraphEdgeChoiceRenderer.orderedJson();
            item.put("routerNodeId", router.getNodeId());
            item.put("routeMode", configText(router, "routeMode", "multi"));
            item.put("inputKey", configText(router, "inputKey", "plan"));
            item.put("description", value(router.getDescription(), ""));
            item.put(
                    "choices",
                    OpsGraphEdgeChoiceRenderer.edgeChoices(
                            definition,
                            outgoing,
                            activeMatcher,
                            edgeMetadataProvider));
            routerArray.add(item);
        }
        if (routerArray.isEmpty()) return "";
        root.put("routers", routerArray);
        root.put("selectionContract", Map.of(
                "route_match",
                "上游节点输出 selectedRoutes、routeKey 或 tasks[].routeKey 后，由 Router 按候选边 routeKey/condition 命中下游节点。",
                "review_decision",
                "复盘分支字段；上游可输出 review_decision、needs:<route>、route key 或 tasks[].routeKey。达到 loop 上限的 feedback edge 不能再选择。",
                "default",
                "没有条件边命中，或当前节点判断无需进入具体候选分支时，走默认/出口边。",
                "selection",
                "选择前阅读 choices[].description；description 来自连线说明和后续节点说明。"));
        return limit(JSON.toJSONString(root));
    }

    static String downstreamRouterContract(
            OpsAgentDefinition definition,
            OpsWorkflowNode upstream,
            Predicate<OpsGraphEdge> activeMatcher,
            Function<OpsGraphEdge, Map<String, Object>> edgeMetadataProvider) {
        if (definition == null
                || upstream == null
                || !StringUtils.hasText(upstream.getNodeId())) {
            return "";
        }
        Map<String, OpsWorkflowNode> nodeById =
                OpsGraphEdgeCatalog.nodeById(definition);
        List<OpsGraphEdge> routerEdges =
                OpsGraphEdgeCatalog.outgoingEdges(definition, upstream).stream()
                        .filter(edge -> {
                            OpsWorkflowNode target = nodeById.get(edge.getTo());
                            return target != null
                                    && "ROUTER".equals(normalizeType(target.getType()));
                        })
                        .toList();
        if (routerEdges.isEmpty()) return "";

        JSONObject root = OpsGraphEdgeChoiceRenderer.orderedJson();
        root.put("upstreamNodeId", upstream.getNodeId());
        Map<?, ?> outputContract = OpsRuntimePromptAssembler.nodeOutputContract(upstream);
        boolean typedOutput = OpsRuntimePromptAssembler.hasJsonOutputContract(outputContract);
        JSONArray routers = new JSONArray();
        for (OpsGraphEdge routerEdge : routerEdges) {
            OpsWorkflowNode router = nodeById.get(routerEdge.getTo());
            List<OpsGraphEdge> choices =
                    OpsGraphEdgeCatalog.outgoingEdges(definition, router);
            if (choices.isEmpty()) continue;
            String inputKey = configText(router, "inputKey", "plan");
            String routeMode = configText(router, "routeMode", "multi");
            JSONObject item = OpsGraphEdgeChoiceRenderer.orderedJson();
            item.put("routerNodeId", router.getNodeId());
            item.put(
                    "handoffEdge",
                    routerEdge.getFrom() + "->" + routerEdge.getTo());
            item.put("inputKey", inputKey);
            item.put("routeMode", routeMode);
            item.put("description", value(router.getDescription(), ""));
            item.put(
                    "requiredOutput",
                    typedOutput ? outputContract.get("schema")
                        : OpsGraphEdgeChoiceRenderer.routerRequiredOutput(inputKey, routeMode));
            item.put(
                    "choices",
                    OpsGraphEdgeChoiceRenderer.edgeChoices(
                            definition,
                            choices,
                            activeMatcher,
                            edgeMetadataProvider));
            routers.add(item);
        }
        if (routers.isEmpty()) return "";
        root.put("routers", routers);
        root.put(
                "rule",
                typedOutput
                    ? "当前节点只输出符合 requiredOutput JSON Schema 的字段。inputKey 是保存整个节点结果的位置，不是应追加的字段。Router 按已有字段及边条件判定分支，不需要模型另选 route key。"
                    : "当前节点后接 Router 时，当前节点输出必须包含 Router 可解析的路由字段；使用 choices[].routeKey 或 choices[].condition 作为 route key，不要发明新 key；选择前阅读 choices[].description。feedback=true 的 choice 是回边分支，必须同时遵守 runtime.loop 状态。");
        return limit(JSON.toJSONString(root));
    }

    private static String configText(
            OpsWorkflowNode node,
            String key,
            String fallback) {
        if (node == null
                || node.getConfig() == null
                || !node.getConfig().containsKey(key)) {
            return fallback;
        }
        Object value = node.getConfig().get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static String normalizeType(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase().replace('-', '_')
                : "";
    }

    private static String limit(String text) {
        if (text == null || text.length() <= MAX_ROUTING_CHOICES_CHARS) {
            return text;
        }
        return text.substring(0, MAX_ROUTING_CHOICES_CHARS - 6) + "...";
    }

    private static String value(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
