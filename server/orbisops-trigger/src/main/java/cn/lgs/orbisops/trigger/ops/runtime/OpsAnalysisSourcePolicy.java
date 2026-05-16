package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.OpsMainAgentPlanner;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Owns canonical analysis sources, graph route keys and node roles. */
final class OpsAnalysisSourcePolicy {

    String normalizeSource(String source) {
        if (!StringUtils.hasText(source)) return "";
        String value = source.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "pgvector", "vector", "knowledge", "knowledge_base" ->
                    OpsMainAgentPlanner.SOURCE_RAG;
            case "es", "elastic", "logs", "log" -> OpsMainAgentPlanner.SOURCE_ES;
            case "prom", "metrics", "metric" -> OpsMainAgentPlanner.SOURCE_PROM;
            case "mysql", "slow_sql", "mysql-slow-sql", "mysql_slow_log" ->
                    OpsMainAgentPlanner.SOURCE_MYSQL_SLOW_SQL;
            default -> value;
        };
    }

    boolean isInvestigationRoute(String source) {
        return Set.of(
                OpsMainAgentPlanner.SOURCE_RAG,
                OpsMainAgentPlanner.SOURCE_ES,
                OpsMainAgentPlanner.SOURCE_PROM,
                OpsMainAgentPlanner.SOURCE_MYSQL_SLOW_SQL)
                .contains(normalizeSource(source));
    }

    String routeConditionSource(OpsGraphEdge edge) {
        String condition = edge == null || !StringUtils.hasText(edge.getCondition())
                ? ""
                : edge.getCondition().trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (!StringUtils.hasText(condition)
                || "always".equals(condition)
                || "default".equals(condition)
                || "__default__".equals(condition)) {
            return "";
        }
        return condition.startsWith("needs:")
                ? normalizeSource(condition.substring("needs:".length()))
                : normalizeSource(condition);
    }

    String incomingRouteKey(OpsAgentDefinition definition, OpsWorkflowNode node) {
        if (definition == null
                || node == null
                || !StringUtils.hasText(node.getNodeId())) {
            return "";
        }
        return Optional.ofNullable(definition.getEdges()).orElse(List.of()).stream()
                .filter(edge -> node.getNodeId().equals(edge.getTo()))
                .map(this::routeConditionSource)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
    }

    String resolveSource(OpsAgentDefinition definition,
                         OpsWorkflowNode node,
                         String type) {
        String routeKey = incomingRouteKey(definition, node);
        if (StringUtils.hasText(routeKey)) return normalizeSource(routeKey);
        return switch (type) {
            case "RAG" -> OpsMainAgentPlanner.SOURCE_RAG;
            case "ELASTICSEARCH", "ES" -> OpsMainAgentPlanner.SOURCE_ES;
            case "PROMETHEUS" -> OpsMainAgentPlanner.SOURCE_PROM;
            case "MYSQL_SLOW_SQL" -> OpsMainAgentPlanner.SOURCE_MYSQL_SLOW_SQL;
            default -> "";
        };
    }

    String agentRole(OpsWorkflowNode node) {
        Object configured = Optional.ofNullable(node)
                .map(OpsWorkflowNode::getConfig)
                .map(config -> config.get("role"))
                .orElse(null);
        String role = configured == null
                ? ""
                : String.valueOf(configured)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_');
        if (StringUtils.hasText(role)) return role;
        return node != null && "ops-main-agent".equalsIgnoreCase(value(node.getAgent()))
                ? "main_planner"
                : "general";
    }

    String defaultAgent(String source) {
        return switch (normalizeSource(source)) {
            case OpsMainAgentPlanner.SOURCE_RAG -> "rag-knowledge-agent";
            case OpsMainAgentPlanner.SOURCE_ES -> "es-log-agent";
            case OpsMainAgentPlanner.SOURCE_PROM -> "prometheus-agent";
            case OpsMainAgentPlanner.SOURCE_MYSQL_SLOW_SQL -> "mysql-slow-sql-agent";
            default -> source + "-agent";
        };
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
