package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Parses Graph route choices and constrains Main Agent plans to canvas-provided routes. */
final class OpsMainAgentGraphRoutingService {

    private static final Logger log = LoggerFactory.getLogger(OpsMainAgentGraphRoutingService.class);

    private final OpsMainAgentDeterministicPlanningService deterministicPlanningService;

    OpsMainAgentGraphRoutingService(
            OpsMainAgentDeterministicPlanningService deterministicPlanningService) {
        this.deterministicPlanningService = deterministicPlanningService;
    }

    boolean scoped(String graphRoutingChoices) {
        return hasText(graphRoutingChoices);
    }

    OpsAnalysisResponseDTO.OpsInvestigationPlanDTO emptyPlan(String reason) {
        return OpsAnalysisResponseDTO.OpsInvestigationPlanDTO.builder()
                .intent("STOP")
                .reason(reason)
                .changeRequested(false)
                .changeIntent("")
                .tasks(new ArrayList<>())
                .conditionalTasks(new ArrayList<>())
                .skippedTasks(new ArrayList<>())
                .build();
    }

    OpsAnalysisResponseDTO.OpsInvestigationPlanDTO filterPlan(
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan,
            String graphRoutingChoices) {
        if (plan == null || !scoped(graphRoutingChoices)) {
            return plan;
        }
        Set<String> allowedSources = routeSources(graphRoutingChoices);
        if (allowedSources.isEmpty()) {
            return plan;
        }
        return OpsAnalysisResponseDTO.OpsInvestigationPlanDTO.builder()
                .intent(plan.getIntent())
                .reason(plan.getReason())
                .changeRequested(plan.getChangeRequested())
                .changeIntent(plan.getChangeIntent())
                .tasks(filterTasks(plan.getTasks(), allowedSources))
                .conditionalTasks(filterTasks(plan.getConditionalTasks(), allowedSources))
                .skippedTasks(filterTasks(plan.getSkippedTasks(), allowedSources))
                .build();
    }

    Set<String> routeSources(String graphRoutingChoices) {
        if (!scoped(graphRoutingChoices)) {
            return Set.of();
        }
        try {
            JSONObject root = JSON.parseObject(graphRoutingChoices);
            JSONArray routers = root.getJSONArray("routers");
            if (routers == null || routers.isEmpty()) {
                return Set.of();
            }
            Set<String> sources = new HashSet<>();
            for (int i = 0; i < routers.size(); i++) {
                JSONObject router = routers.getJSONObject(i);
                JSONArray choices = router == null ? null : router.getJSONArray("choices");
                if (choices == null) {
                    continue;
                }
                for (int j = 0; j < choices.size(); j++) {
                    String source = choiceSource(choices.getJSONObject(j));
                    if (hasText(source)) {
                        sources.add(source);
                    }
                }
            }
            return sources;
        } catch (Exception e) {
            log.debug("解析 graphRoutingChoices 失败，跳过动态 routeKey 过滤：{}", e.getMessage());
            return Set.of();
        }
    }

    private List<OpsAnalysisResponseDTO.InvestigationTaskDTO> filterTasks(
            List<OpsAnalysisResponseDTO.InvestigationTaskDTO> tasks,
            Set<String> allowedSources) {
        return Optional.ofNullable(tasks).orElse(List.of()).stream()
                .filter(task -> task != null
                        && allowedSources.contains(normalizeSource(task.getSource())))
                .toList();
    }

    private String choiceSource(JSONObject choice) {
        if (choice == null) {
            return "";
        }
        String conditionType = normalizedToken(choice.getString("conditionType"));
        if ("default".equals(conditionType) || "always".equals(conditionType)) {
            return "";
        }
        String condition = normalizedToken(choice.getString("condition"));
        if (condition.startsWith("needs:")) {
            return normalizeSource(condition.substring("needs:".length()));
        }
        if ("replan_required".equals(condition)) {
            return condition;
        }
        String routeKey = normalizeSource(choice.getString("routeKey"));
        if (hasText(routeKey)) {
            return routeKey;
        }
        if (hasText(condition)
                && !"always".equals(condition)
                && !"default".equals(condition)
                && !"__default__".equals(condition)) {
            return normalizeSource(condition);
        }
        String hint = normalizedToken(choice.getString("routeOutputHint"));
        String prefix = "tasks[].routekey=";
        int index = hint.indexOf(prefix);
        if (index >= 0) {
            String rest = hint.substring(index + prefix.length()).trim();
            int end = rest.indexOf(' ');
            return normalizeSource(end >= 0 ? rest.substring(0, end) : rest);
        }
        return "";
    }

    private String normalizedToken(String value) {
        return text(value).trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private String normalizeSource(String source) {
        return deterministicPlanningService.normalizeSource(source);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
