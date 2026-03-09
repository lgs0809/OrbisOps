package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.domain.investigation.service.InvestigationPlanningPolicy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Maintains Main Agent-visible SubAgent capabilities and datasource availability. */
final class OpsMainAgentSubAgentCatalog {

    private static final Set<String> BUILTIN_SOURCES = Set.of(
            InvestigationPlanningPolicy.SOURCE_RAG,
            InvestigationPlanningPolicy.SOURCE_ES,
            InvestigationPlanningPolicy.SOURCE_PROM,
            InvestigationPlanningPolicy.SOURCE_MYSQL_SLOW_SQL);

    private final Map<String, OpsSubAgent> agents = new LinkedHashMap<>();

    void replace(List<OpsSubAgent> subAgents) {
        agents.clear();
        if (subAgents == null) {
            return;
        }
        subAgents.stream()
                .filter(agent -> agent != null && hasText(agent.source()))
                .forEach(agent -> agents.put(agent.source(), agent));
    }

    Set<String> availableSources() {
        return agents.isEmpty() ? BUILTIN_SOURCES : agents.keySet();
    }

    String capabilityCatalog() {
        if (agents.isEmpty()) {
            return """
                    rag: rag-knowledge-agent, SOP/架构/指标字典/历史案例；
                    elasticsearch: es-log-agent, 真实日志/traceId/orderId/URI/ERROR/WARN；
                    prometheus: prometheus-agent, 实例/QPS/错误率/延迟/JVM/CPU；
                    mysql_slow_sql: mysql-slow-sql-agent, MySQL慢查询/高耗时SQL/扫描行数/执行次数
                    """;
        }
        return agents.values().stream()
                .map(agent -> agent.source() + ": " + agent.agentId() + ", " + agent.capability())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
