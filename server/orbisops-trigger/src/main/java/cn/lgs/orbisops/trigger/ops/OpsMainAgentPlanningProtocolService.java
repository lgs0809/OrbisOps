package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import com.alibaba.fastjson.JSONObject;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Plain LLM protocol ACL for Main Agent planning and replanning. */
final class OpsMainAgentPlanningProtocolService {

    private static final String PLANNER_SYSTEM_PROMPT = """
            你是运维 multi-agent 系统的主 Agent，采用 Plan-and-Execute。
            你的职责是根据画布提供的 Router choices，自主判断需要进入哪些下游 Agent。
            不要默认全查；只有有信息增益时才查。
            输出必须是 JSON 对象，不要输出 Markdown。
            JSON 格式：
            {
              "intent": "GENERAL_HEALTH_CHECK|LOG_FIRST_INVESTIGATION|METRIC_FIRST_INVESTIGATION|SQL_FIRST_INVESTIGATION|KNOWLEDGE_FIRST_INVESTIGATION|INCIDENT_INVESTIGATION",
              "reason": "路由理由",
              "changeRequested": true|false,
              "changeIntent": "用户明确要求修复时，概括希望改变什么；否则为空字符串",
              "tasks": [{"routeKey":"必须匹配 graphRoutingChoices 中某个 choices[].routeKey","agent":"目标 Agent 名称","goal":"目标","reason":"为什么查","priority":1,"condition":null}],
              "conditionalTasks": [{"routeKey":"...","agent":"...","goal":"...","reason":"...","priority":2,"condition":"何时触发"}],
              "skippedTasks": [{"routeKey":"...","agent":"...","goal":"暂不查询","reason":"为什么不查","priority":3,"condition":null}]
            }
            路由硬约束：
            - 如果提供了 graphRoutingChoices JSON，只能从 choices 中选择 routeKey，不要生成画布没有提供的 routeKey。
            - 每个 choice 的能力由 description/targetDescription 描述；不要只根据 route key 名称猜测。
            - changeRequested 只表示用户是否明确要求实际修复、调整、清理、发布或回滚；仅要求分析、解释、查询、建议时必须为 false。
            """;

    private static final String REPLANNER_SYSTEM_PROMPT = """
            你是运维 multi-agent 系统的主 Agent，正在执行外层 Plan-and-Execute 循环。
            你需要根据已经返回的子 Agent observation、证据缺口和剩余数据源，决定是否重新规划下一轮子 Agent 任务。
            不要重复查询已经执行过且没有新参数增益的数据源；如果证据足够或剩余数据源无信息增益，返回空 tasks。
            输出必须是 JSON 对象，不要输出 Markdown。
            JSON 格式：
            {
              "intent": "REPLAN_CONTINUE|STOP",
              "reason": "为什么继续或停止",
              "tasks": [{"routeKey":"必须匹配 graphRoutingChoices 中某个可用 feedback choice","agent":"目标 Agent 名称","goal":"下一轮查询目标","reason":"为什么现在查","priority":1,"condition":"main-replan"}],
              "conditionalTasks": [],
              "skippedTasks": [{"routeKey":"...","agent":"...","goal":"暂不查询","reason":"为什么不查","priority":3,"condition":null}]
            }
            路由硬约束：
            - 如果提供了 graphRoutingChoices JSON，只能从 choices 中选择 routeKey；feedback 边必须满足 remainingRounds > 0 且 exhausted=false，否则返回空 tasks。
            - 每个 choice 的能力由 description/targetDescription 描述；不要只根据 route key 名称猜测。
            """;

    private final OpsAgentLlmClient llmClient;
    private final OpsMainAgentDeterministicPlanningService deterministicPlanningService;
    private final OpsMainAgentPlanningJsonMapper jsonMapper;

    OpsMainAgentPlanningProtocolService(
            OpsAgentLlmClient llmClient,
            OpsMainAgentDeterministicPlanningService deterministicPlanningService) {
        this.llmClient = llmClient;
        this.deterministicPlanningService = deterministicPlanningService;
        this.jsonMapper = new OpsMainAgentPlanningJsonMapper(deterministicPlanningService);
    }

    Attempt plan(PlanInput input) {
        JSONObject json = llmClient.chatJsonObjectWithEagerSkillContext(
                "ops-main-agent-planner",
                PLANNER_SYSTEM_PROMPT,
                plannerPrompt(input),
                Optional.ofNullable(input.skillNames()).orElse(List.of()));
        List<String> validationErrors = OpsLlmJsonValidator.validatePlanner(
                json,
                !input.graphScoped(),
                !input.graphScoped());
        if (!validationErrors.isEmpty()) {
            llmClient.rejectDegradation(
                    "ops-main-agent-planner",
                    "planner JSON 校验失败：" + validationErrors);
            return Attempt.invalidSchema();
        }
        return Attempt.valid(jsonMapper.toPlan(json, input.graphScoped(), input.availableSources()));
    }

    Attempt replan(ReplanInput input) {
        JSONObject json = llmClient.chatJsonObjectWithEagerSkillContext(
                "ops-main-agent-replanner",
                REPLANNER_SYSTEM_PROMPT,
                replanPrompt(input),
                Optional.ofNullable(input.skillNames()).orElse(List.of()));
        if (json == null) {
            return Attempt.noJson();
        }
        List<String> validationErrors = OpsLlmJsonValidator.validateReplanner(
                json,
                !input.graphScoped());
        if (!validationErrors.isEmpty()) {
            llmClient.rejectDegradation(
                    "ops-main-agent-replanner",
                    "replan JSON 校验失败：" + validationErrors);
            return Attempt.invalidSchema();
        }
        return Attempt.valid(jsonMapper.toPlan(json, input.graphScoped(), input.availableSources()));
    }

    private String plannerPrompt(PlanInput input) {
        String availableCatalog = input.graphScoped()
                ? "以画布路由选择 JSON 中 choices 的 routeKey、description、targetDescription 和 runtime.loop 为准；description 来自连线说明和后续节点说明"
                : input.availableSourceCatalog();
        return """
                用户问题：%s
                已解析信号：
                - blankQuestion=%s
                - hasLogSignal=%s
                - hasMetricSignal=%s
                - hasSlowSqlSignal=%s
                - hasKnowledgeSignal=%s
                - filters=%s
                - availableSubAgents=%s
                默认参数：rangeMinutes=%s, promWindow=%s, includeRecentLogs=%s, maxRounds=%s

                画布路由选择 JSON：
                %s

                规则 planner 的参考计划：
                intent=%s
                reason=%s

                %s
                - 哪些数据源应该立即查；
                - 哪些只作为条件任务；
                - 哪些本轮跳过；
                - 如果规则参考和用户真实意图冲突，以你的 JSON 决策为准。
                """.formatted(
                value(input.context().originalQuestion()),
                input.context().blankQuestion(),
                input.context().hasLogSignal(),
                input.context().hasMetricSignal(),
                input.context().hasSlowSqlSignal(),
                input.context().hasKnowledgeSignal(),
                input.context().describeFilters(),
                availableCatalog,
                input.request().getRangeMinutes(),
                input.request().getPromWindow(),
                input.request().getIncludeRecentLogs(),
                input.request().getMaxRounds(),
                hasText(input.graphRoutingChoices()) ? input.graphRoutingChoices() : "未提供",
                input.fallbackPlan().getIntent(),
                input.fallbackPlan().getReason(),
                input.graphScoped()
                        ? "Graph 模式下规则计划只说明兜底状态，不能发明画布没有提供的 routeKey。请作为主 Agent 主动重新判断："
                        : "规则计划只是兜底参考，不是必须遵守的路由结果。请作为主 Agent 主动重新判断：");
    }

    private String replanPrompt(ReplanInput input) {
        Set<String> executedSources = executedSources(input.results());
        Set<String> remainingSources = new HashSet<>(
                input.graphScoped() ? input.graphRouteSources() : input.availableSources());
        remainingSources.removeAll(executedSources);
        return """
                用户问题：%s
                当前轮次：%s/%s
                已解析过滤条件：%s
                请求参数：rangeMinutes=%s, promWindow=%s, includeRecentLogs=%s, subAgentMaxIterations=%s

                上一轮计划：
                intent=%s
                reason=%s
                tasks=%s

                已执行数据源：%s
                剩余可选数据源：%s

                画布路由选择 JSON：
                %s

                已返回 observation：
                %s

                规则 replan 参考：
                intent=%s
                reason=%s
                tasks=%s

                %s
                - 只在剩余数据源确实能补齐证据缺口时添加 tasks；
                - 如果当前证据足够，返回空 tasks；
                - 如果规则参考和 observation 冲突，以你的 JSON 决策为准。
                """.formatted(
                value(input.context().originalQuestion()),
                input.round(),
                input.maxRounds(),
                input.context().describeFilters(),
                input.request().getRangeMinutes(),
                input.request().getPromWindow(),
                input.request().getIncludeRecentLogs(),
                input.request().getSubAgentMaxIterations(),
                input.previousPlan() == null ? "" : input.previousPlan().getIntent(),
                input.previousPlan() == null ? "" : input.previousPlan().getReason(),
                summarizeTasks(input.previousPlan() == null ? null : input.previousPlan().getTasks()),
                executedSources.isEmpty() ? "无" : String.join(",", executedSources),
                remainingSources.isEmpty() ? "无" : String.join(",", remainingSources),
                hasText(input.graphRoutingChoices()) ? input.graphRoutingChoices() : "未提供",
                summarizeResults(input.results()),
                input.fallbackPlan().getIntent(),
                input.fallbackPlan().getReason(),
                summarizeTasks(input.fallbackPlan().getTasks()),
                input.graphScoped()
                        ? "Graph 模式下规则 replan 只说明兜底状态，不能发明画布没有提供的 routeKey。请作为主 Agent 主动判断下一轮是否继续："
                        : "规则 replan 只是兜底参考。请作为主 Agent 主动判断下一轮是否继续：");
    }

    private Set<String> executedSources(List<OpsAnalysisResponseDTO.InvestigationResultDTO> results) {
        return Optional.ofNullable(results).orElse(List.of()).stream()
                .filter(result -> result != null && hasText(result.getSource()))
                .map(result -> deterministicPlanningService.normalizeSource(result.getSource()))
                .collect(Collectors.toSet());
    }

    private String summarizeResults(List<OpsAnalysisResponseDTO.InvestigationResultDTO> results) {
        String summary = Optional.ofNullable(results).orElse(List.of()).stream()
                .filter(result -> result != null)
                .map(result -> "- " + value(result.getSource()) + " / " + value(result.getAgent())
                        + " / " + value(result.getStatus()) + "：" + value(result.getSummary())
                        + "；gaps=" + String.join(" | ", Optional.ofNullable(result.getGaps()).orElse(List.of()))
                        + "；suggestedAdjustments=" + String.join(" | ", Optional.ofNullable(result.getSuggestedAdjustments()).orElse(List.of())))
                .collect(Collectors.joining("\n"));
        return hasText(summary) ? summary : "无";
    }

    private String summarizeTasks(List<OpsAnalysisResponseDTO.InvestigationTaskDTO> tasks) {
        String summary = Optional.ofNullable(tasks).orElse(List.of()).stream()
                .map(task -> value(task.getSource()) + ":" + value(task.getAgent())
                        + ":" + value(task.getGoal()) + ":" + value(task.getReason()))
                .collect(Collectors.joining("；"));
        return hasText(summary) ? summary : "无";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    record PlanInput(
            OpsAgentRunRequestDTO request,
            OpsQuestionContext context,
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO fallbackPlan,
            boolean graphScoped,
            String graphRoutingChoices,
            String availableSourceCatalog,
            Set<String> availableSources,
            List<String> skillNames) {
    }

    record ReplanInput(
            OpsAgentRunRequestDTO request,
            OpsQuestionContext context,
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO previousPlan,
            List<OpsAnalysisResponseDTO.InvestigationResultDTO> results,
            OpsAnalysisResponseDTO.OpsInvestigationPlanDTO fallbackPlan,
            int round,
            int maxRounds,
            boolean graphScoped,
            String graphRoutingChoices,
            Set<String> graphRouteSources,
            Set<String> availableSources,
            List<String> skillNames) {
    }

    record Attempt(Status status, OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan) {
        static Attempt valid(OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan) {
            return new Attempt(Status.VALID, plan);
        }

        static Attempt noJson() {
            return new Attempt(Status.NO_JSON, null);
        }

        static Attempt invalidSchema() {
            return new Attempt(Status.INVALID_SCHEMA, null);
        }

        boolean valid() {
            return status == Status.VALID;
        }
    }

    enum Status {
        VALID,
        NO_JSON,
        INVALID_SCHEMA
    }
}
