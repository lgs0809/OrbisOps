package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static cn.lgs.orbisops.trigger.ops.OpsMainAgentPlanner.SOURCE_ES;
import static cn.lgs.orbisops.trigger.ops.OpsMainAgentPlanner.SOURCE_MYSQL_SLOW_SQL;
import static cn.lgs.orbisops.trigger.ops.OpsMainAgentPlanner.SOURCE_PROM;
import static cn.lgs.orbisops.trigger.ops.OpsMainAgentPlanner.SOURCE_RAG;

/** Resolves or materializes one Investigation task for a datasource source. */
final class OpsInvestigationTaskResolver {

    OpsAnalysisResponseDTO.InvestigationTaskDTO resolve(
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan,
            String source) {
        return Optional.ofNullable(plan.getTasks()).orElse(List.of()).stream()
                .filter(task -> Objects.equals(source, task.getSource()))
                .findFirst()
                .or(() -> Optional.ofNullable(plan.getConditionalTasks()).orElse(List.of()).stream()
                        .filter(task -> Objects.equals(source, task.getSource()))
                        .findFirst())
                .orElseGet(() -> task(
                        source,
                        defaultAgent(source),
                        "查询 " + source + " 数据源。",
                        "主 Agent follow-up 需要补充该数据源证据。",
                        3,
                        "main reflection"));
    }

    private OpsAnalysisResponseDTO.InvestigationTaskDTO task(
            String source,
            String agent,
            String goal,
            String reason,
            Integer priority,
            String condition) {
        return OpsAnalysisResponseDTO.InvestigationTaskDTO.builder()
                .source(source)
                .agent(agent)
                .goal(goal)
                .reason(reason)
                .priority(priority)
                .condition(condition)
                .build();
    }

    private String defaultAgent(String source) {
        return switch (source) {
            case SOURCE_ES -> "es-log-agent";
            case SOURCE_PROM -> "prometheus-agent";
            case SOURCE_RAG -> "rag-knowledge-agent";
            case SOURCE_MYSQL_SLOW_SQL -> "mysql-slow-sql-agent";
            default -> source + "-agent";
        };
    }
}
