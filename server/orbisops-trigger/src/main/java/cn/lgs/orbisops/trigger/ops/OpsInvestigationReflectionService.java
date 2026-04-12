package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.lgs.orbisops.trigger.ops.OpsMainAgentPlanner.SOURCE_ES;
import static cn.lgs.orbisops.trigger.ops.OpsMainAgentPlanner.SOURCE_MYSQL_SLOW_SQL;
import static cn.lgs.orbisops.trigger.ops.OpsMainAgentPlanner.SOURCE_PROM;
import static cn.lgs.orbisops.trigger.ops.OpsMainAgentPlanner.SOURCE_RAG;

/** LLM protocol ACL for main-agent evidence reflection and follow-up task suggestions. */
final class OpsInvestigationReflectionService {

    private static final String AGENT_NAME = "ops-main-agent-reflection";
    private static final String SYSTEM_PROMPT = """
            你是运维 multi-agent 系统的主 Agent。
            你刚收到一个子 Agent 的真实 observation，需要判断是否继续派发其他子 Agent。
            你不能把未查询的数据源说成已查询；如果当前证据足够，可以停止。
            LLM 决策优先于规则条件任务；只有你无法输出合法 JSON 时系统才会启用规则兜底。
            输出必须是 JSON 对象，不要输出 Markdown。
            JSON 格式：
            {
              "decision": "continue|stop",
              "note": "本轮复盘说明，80字以内",
              "addTasks": [{"source":"prometheus|elasticsearch|rag|mysql_slow_sql","agent":"prometheus-agent|es-log-agent|rag-knowledge-agent|mysql-slow-sql-agent","goal":"目标","reason":"为什么追加","priority":2,"condition":"main reflection"}]
            }
            字符串字段必须简短：note <= 80字，goal/reason <= 50字；addTasks 最多 2 个。
            不要把详细分析写在 note 中，详细结论由最终报告根据真实 observation 生成。
            数据源边界：
            - RAG：SOP、指标字典、日志字典、历史案例，不是实时证据。
            - Elasticsearch：日志、traceId/orderId/URI/error/warn/堆栈。
            - Prometheus：实例、QPS、错误率、延迟、JVM、CPU。
            - MySQL Slow SQL：mysql.slow_log 或 performance_schema 慢 SQL/高耗时 SQL。
            复盘硬约束：
            - 纯 SOP/含义/历史案例/边界/安全策略问题，如果没有当前/最近/线上验证诉求，RAG observation 后应停止，不要追加实时数据源。
            - 慢 SQL/rows_sent/rows_examined/query_time/digest/performance_schema 证据缺口，应补 MySQL Slow SQL，而不是用 RAG 替代数据库事实源。
            """;

    private final OpsAgentLlmClient llmClient;

    OpsInvestigationReflectionService(OpsAgentLlmClient llmClient) {
        if (llmClient == null) throw new IllegalArgumentException("INVESTIGATION_REFLECTION_LLM_CLIENT_REQUIRED");
        this.llmClient = llmClient;
    }

    Decision reflect(Input input) {
        if (input == null || input.latestResult() == null) {
            return Decision.invalid("LLM主 Agent 复盘输入缺失，启用规则兜底派发。");
        }
        JSONObject json;
        try {
            json = llmClient.chatJsonObject(
                    AGENT_NAME,
                    SYSTEM_PROMPT,
                    buildPrompt(input),
                    List.of());
            if (json == null) {
                return Decision.invalid("LLM主 Agent 复盘未返回 JSON，启用规则兜底派发。");
            }
            List<String> validationErrors = OpsLlmJsonValidator.validateMainReflection(json);
            if (!validationErrors.isEmpty()) {
                llmClient.rejectDegradation(
                        AGENT_NAME,
                        "复盘 JSON 校验失败：" + validationErrors);
                return Decision.invalid(
                        "LLM主 Agent 复盘 JSON 校验失败，启用规则兜底派发：" + validationErrors);
            }
        } catch (OpsLlmDegradationException e) {
            return Decision.invalid(
                    "LLM主 Agent 复盘失败，启用规则兜底派发：" + e.getMessage());
        }

        String note = text(json.getString("note"));
        if ("stop".equalsIgnoreCase(json.getString("decision"))) {
            return Decision.stopped(note);
        }
        return Decision.continueWith(note, tasks(json.getJSONArray("addTasks"), input));
    }

    private List<OpsAnalysisResponseDTO.InvestigationTaskDTO> tasks(
            JSONArray addTasks,
            Input input) {
        if (addTasks == null || addTasks.isEmpty()) return List.of();
        List<OpsAnalysisResponseDTO.InvestigationTaskDTO> result = new java.util.ArrayList<>();
        for (int index = 0; index < addTasks.size(); index++) {
            JSONObject item = addTasks.getJSONObject(index);
            if (item == null) continue;
            String source = text(item.getString("source"));
            if (!input.registeredSources().contains(source)
                    || input.executedSources().contains(source)
                    || input.queuedSources().contains(source)) {
                continue;
            }
            result.add(OpsAnalysisResponseDTO.InvestigationTaskDTO.builder()
                    .source(source)
                    .agent(value(item, "agent", defaultAgent(source)))
                    .goal(value(item, "goal", "主 Agent 复盘后追加查询 " + source))
                    .reason(value(item, "reason", "主 Agent 复盘认为该数据源仍有信息增益。"))
                    .priority(item.getInteger("priority") == null ? 3 : item.getInteger("priority"))
                    .condition(value(item, "condition", "main reflection"))
                    .build());
        }
        return List.copyOf(result);
    }

    private String buildPrompt(Input input) {
        String resultSummary = input.results().stream()
                .filter(java.util.Objects::nonNull)
                .map(result -> result.getSource() + ":" + result.getStatus() + ":" + result.getSummary())
                .collect(Collectors.joining("\n"));
        String availableSources = input.registeredSources().stream()
                .filter(source -> !input.executedSources().contains(source)
                        && !input.queuedSources().contains(source))
                .collect(Collectors.joining(","));
        OpsAnalysisResponseDTO.InvestigationResultDTO latest = input.latestResult();
        return """
                用户问题：%s
                已解析过滤条件：%s
                初始计划：%s / %s
                默认参数：rangeMinutes=%s, promWindow=%s, maxRounds=%s, subAgentMaxIterations=%s

                最新子 Agent observation：
                - source=%s
                - status=%s
                - summary=%s
                - evidence=%s
                - gaps=%s
                - suggestedAdjustments=%s

                已执行结果：
                %s

                尚可追加的数据源：%s
                当前队列：%s
                """.formatted(
                value(input.questionContext().originalQuestion()),
                input.questionContext().describeFilters(),
                input.plan().getIntent(),
                input.plan().getReason(),
                input.request().getRangeMinutes(),
                input.request().getPromWindow(),
                input.request().getMaxRounds(),
                input.request().getSubAgentMaxIterations(),
                latest.getSource(),
                latest.getStatus(),
                latest.getSummary(),
                String.join(" | ", Optional.ofNullable(latest.getEvidence()).orElse(List.of())),
                String.join(" | ", Optional.ofNullable(latest.getGaps()).orElse(List.of())),
                String.join(" | ", Optional.ofNullable(latest.getSuggestedAdjustments()).orElse(List.of())),
                resultSummary,
                availableSources,
                String.join(",", input.queuedSources()));
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

    private String value(JSONObject json, String key, String fallback) {
        String value = json.getString(key);
        return hasText(value) ? value : fallback;
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    record Input(OpsAgentRunRequestDTO request,
                 OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan,
                 OpsAnalysisResponseDTO.InvestigationResultDTO latestResult,
                 List<OpsAnalysisResponseDTO.InvestigationResultDTO> results,
                 Set<String> registeredSources,
                 Set<String> executedSources,
                 Set<String> queuedSources,
                 OpsQuestionContext questionContext) {
        Input {
            if (request == null) throw new IllegalArgumentException("INVESTIGATION_REFLECTION_REQUEST_REQUIRED");
            if (plan == null) throw new IllegalArgumentException("INVESTIGATION_REFLECTION_PLAN_REQUIRED");
            results = results == null ? List.of() : List.copyOf(results);
            registeredSources = frozen(registeredSources);
            executedSources = frozen(executedSources);
            queuedSources = frozen(queuedSources);
            if (questionContext == null) {
                throw new IllegalArgumentException("INVESTIGATION_REFLECTION_QUESTION_CONTEXT_REQUIRED");
            }
        }

        private static Set<String> frozen(Set<String> values) {
            return values == null
                    ? Set.of()
                    : java.util.Collections.unmodifiableSet(new LinkedHashSet<>(values));
        }
    }

    record Decision(boolean valid,
                    boolean stop,
                    String note,
                    String fallbackNote,
                    List<OpsAnalysisResponseDTO.InvestigationTaskDTO> tasks) {
        Decision {
            note = note == null ? "" : note.trim();
            fallbackNote = fallbackNote == null ? "" : fallbackNote.trim();
            tasks = tasks == null ? List.of() : List.copyOf(tasks);
        }

        static Decision invalid(String fallbackNote) {
            return new Decision(false, false, "", fallbackNote, List.of());
        }

        static Decision stopped(String note) {
            return new Decision(true, true, note, "", List.of());
        }

        static Decision continueWith(
                String note,
                List<OpsAnalysisResponseDTO.InvestigationTaskDTO> tasks) {
            return new Decision(true, false, note, "", tasks);
        }
    }
}
