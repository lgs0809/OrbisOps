package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Maps validated Main Agent planning JSON into API DTOs. */
final class OpsMainAgentPlanningJsonMapper {

    private final OpsMainAgentDeterministicPlanningService deterministicPlanningService;

    OpsMainAgentPlanningJsonMapper(
            OpsMainAgentDeterministicPlanningService deterministicPlanningService) {
        this.deterministicPlanningService = deterministicPlanningService;
    }

    OpsAnalysisResponseDTO.OpsInvestigationPlanDTO toPlan(
            JSONObject json,
            boolean graphScoped,
            Set<String> availableSources) {
        if (json == null) {
            return null;
        }
        return OpsAnalysisResponseDTO.OpsInvestigationPlanDTO.builder()
                .intent(text(json, "intent", "INCIDENT_INVESTIGATION"))
                .reason(text(json, "reason", "主 Agent 基于问题意图选择数据源。"))
                .changeRequested(json.getBoolean("changeRequested"))
                .changeIntent(text(json, "changeIntent", ""))
                .tasks(tasks(json.getJSONArray("tasks"), graphScoped, availableSources))
                .conditionalTasks(tasks(json.getJSONArray("conditionalTasks"), graphScoped, availableSources))
                .skippedTasks(tasks(json.getJSONArray("skippedTasks"), graphScoped, availableSources))
                .build();
    }

    private List<OpsAnalysisResponseDTO.InvestigationTaskDTO> tasks(
            JSONArray array,
            boolean graphScoped,
            Set<String> availableSources) {
        List<OpsAnalysisResponseDTO.InvestigationTaskDTO> tasks = new ArrayList<>();
        if (array == null) {
            return tasks;
        }
        for (int i = 0; i < array.size(); i++) {
            JSONObject item = array.getJSONObject(i);
            String routeKey = graphScoped
                    ? item.getString("routeKey")
                    : firstText(item.getString("routeKey"), item.getString("source"));
            String source = deterministicPlanningService.normalizeSource(routeKey);
            if (!hasText(source)
                    || (!graphScoped && !availableSources.contains(source))) {
                continue;
            }
            tasks.add(OpsAnalysisResponseDTO.InvestigationTaskDTO.builder()
                    .source(source)
                    .agent(text(item, "agent", defaultAgent(source)))
                    .goal(text(item, "goal", "查询 " + source + " 数据源。"))
                    .reason(text(item, "reason", "主 Agent 判断该数据源有信息增益。"))
                    .priority(item.getInteger("priority") == null ? i + 1 : item.getInteger("priority"))
                    .condition(item.getString("condition"))
                    .build());
        }
        return tasks;
    }

    private String defaultAgent(String source) {
        return switch (source) {
            case OpsMainAgentPlanner.SOURCE_ES -> "es-log-agent";
            case OpsMainAgentPlanner.SOURCE_PROM -> "prometheus-agent";
            case OpsMainAgentPlanner.SOURCE_RAG -> "rag-knowledge-agent";
            case OpsMainAgentPlanner.SOURCE_MYSQL_SLOW_SQL -> "mysql-slow-sql-agent";
            default -> source + "-agent";
        };
    }

    private String text(JSONObject json, String key, String defaultValue) {
        String value = json.getString(key);
        return hasText(value) ? value : defaultValue;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
