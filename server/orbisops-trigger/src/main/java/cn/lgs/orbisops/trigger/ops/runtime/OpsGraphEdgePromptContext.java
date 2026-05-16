package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.fastjson.JSON;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/** Stable prompt-context facade over graph catalog and rendering boundaries. */
final class OpsGraphEdgePromptContext {

    private static final int MAX_SECTION_CHARS = 3200;
    private static final int MAX_COMPACT_CHARS = 900;

    private OpsGraphEdgePromptContext() {
    }

    static String nodePromptContext(
            OpsAgentDefinition definition,
            OpsWorkflowNode node,
            Predicate<OpsGraphEdge> activeMatcher) {
        return nodePromptContext(
                definition,
                node,
                activeMatcher,
                edge -> Map.of());
    }

    static String nodePromptContext(
            OpsAgentDefinition definition,
            OpsWorkflowNode node,
            Predicate<OpsGraphEdge> activeMatcher,
            Function<OpsGraphEdge, Map<String, Object>> edgeMetadataProvider) {
        List<OpsGraphEdge> incoming =
                OpsGraphEdgeCatalog.incomingEdges(definition, node);
        List<OpsGraphEdge> outgoing =
                OpsGraphEdgeCatalog.outgoingEdges(definition, node);
        if (incoming.isEmpty() && outgoing.isEmpty()) return "";

        StringBuilder context = new StringBuilder();
        if (!incoming.isEmpty()) {
            context.append("### 入边交接上下文\n");
            renderEdges(
                    context,
                    OpsGraphEdgeCatalog.selectedEdges(incoming, activeMatcher),
                    activeMatcher);
        }
        if (!outgoing.isEmpty()) {
            if (!context.isEmpty()) context.append('\n');
            context.append("### 当前节点可选出边\n");
            renderEdges(context, outgoing, activeMatcher);
            String json = OpsGraphEdgeChoiceRenderer.edgeChoiceJson(
                    definition,
                    node,
                    outgoing,
                    activeMatcher,
                    edgeMetadataProvider);
            if (StringUtils.hasText(json)) {
                context.append('\n')
                        .append("### 当前节点可选出边 JSON\n")
                        .append(json)
                        .append('\n');
            }
        }
        return limit(context.toString().trim(), MAX_SECTION_CHARS);
    }

    static String routerChoiceContext(
            OpsAgentDefinition definition,
            String inputKey,
            Predicate<OpsGraphEdge> activeMatcher,
            Function<OpsGraphEdge, Map<String, Object>> edgeMetadataProvider) {
        return OpsGraphRouterPromptContract.routerChoiceContext(
                definition,
                inputKey,
                activeMatcher,
                edgeMetadataProvider);
    }

    static String downstreamRouterContract(
            OpsAgentDefinition definition,
            OpsWorkflowNode upstream,
            Predicate<OpsGraphEdge> activeMatcher,
            Function<OpsGraphEdge, Map<String, Object>> edgeMetadataProvider) {
        return OpsGraphRouterPromptContract.downstreamRouterContract(
                definition,
                upstream,
                activeMatcher,
                edgeMetadataProvider);
    }

    static String compactIncomingHandoff(
            OpsAgentDefinition definition,
            OpsWorkflowNode node,
            Predicate<OpsGraphEdge> activeMatcher) {
        List<OpsGraphEdge> incoming = OpsGraphEdgeCatalog.selectedEdges(
                OpsGraphEdgeCatalog.incomingEdges(definition, node),
                activeMatcher);
        if (incoming.isEmpty()) return "";

        StringBuilder context = new StringBuilder();
        for (OpsGraphEdge edge : incoming) {
            if (!context.isEmpty()) context.append("；");
            context.append(edge.getFrom())
                    .append("->")
                    .append(edge.getTo());
            String conditionType = value(edge.getConditionType(), "always");
            String condition = value(edge.getCondition(), "always");
            context.append("(")
                    .append(conditionType)
                    .append(":")
                    .append(condition);
            if (Boolean.TRUE.equals(edge.getFeedback())) {
                context.append(", feedback");
            }
            context.append(")");
            if (StringUtils.hasText(edge.getDescription())) {
                context.append("：").append(edge.getDescription());
            }
            if (edge.getDataMapping() != null
                    && !edge.getDataMapping().isEmpty()) {
                context.append("，dataMapping=")
                        .append(JSON.toJSONString(edge.getDataMapping()));
            }
        }
        return limit(context.toString(), MAX_COMPACT_CHARS);
    }

    private static void renderEdges(
            StringBuilder context,
            List<OpsGraphEdge> edges,
            Predicate<OpsGraphEdge> activeMatcher) {
        for (OpsGraphEdge edge : edges) {
            boolean active = activeMatcher != null && activeMatcher.test(edge);
            context.append("- ")
                    .append(active ? "active " : "")
                    .append(edge.getFrom())
                    .append(" -> ")
                    .append(edge.getTo())
                    .append(" [")
                    .append(value(edge.getConditionType(), "always"))
                    .append(": ")
                    .append(value(edge.getCondition(), "always"));
            if (Boolean.TRUE.equals(edge.getFeedback())) {
                context.append(", feedback");
            }
            if (Boolean.TRUE.equals(edge.getDefaultEdge())) {
                context.append(", default");
            }
            context.append("]");
            if (StringUtils.hasText(edge.getDescription())) {
                context.append("：").append(edge.getDescription());
            }
            if (edge.getDataMapping() != null
                    && !edge.getDataMapping().isEmpty()) {
                context.append(" dataMapping=")
                        .append(JSON.toJSONString(edge.getDataMapping()));
            }
            context.append('\n');
        }
    }

    private static String limit(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) return text;
        return text.substring(0, Math.max(0, maxChars - 6)) + "...";
    }

    private static String value(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
