package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import com.alibaba.fastjson.JSONObject;

import java.util.List;

/** Plain LLM protocol ACL for datasource SubAgent THINK and REVIEW. */
final class OpsSubAgentDecisionProtocolService {

    private static final String THINK_SYSTEM_PROMPT = """
            你是运维 multi-agent 系统中的数据源子 Agent。
            你只负责为自己的数据源生成查询策略，不要编造查询结果。
            输出必须是 JSON 对象，不要输出 Markdown。
            JSON 字段：
            {
              "reason": "为什么这样查",
              "rangeMinutes": 15,
              "promWindow": "5m",
              "includeRecentLogs": true,
              "retrievalMode": "auto|vector|bm25|hybrid",
              "queryFocus": "本次查询关注点",
              "requireExactFilters": true,
              "expectedEvidence": ["需要看到什么证据"]
            }
            只使用已给出的可用资源能力；如果没有必要修改某字段，可以沿用默认值。
            如果上一轮 observation 证据不足，应按当前子 Agent 专用 skill 选择更有信息增益的参数；没有有价值调整时不要为了循环而循环。
            """;

    private static final String REVIEW_SYSTEM_PROMPT = """
            你是运维 multi-agent 系统中的数据源子 Agent。
            现在你已经完成一次真实查询，只能基于 observation 判断证据是否足够。
            输出必须是 JSON 对象，不要输出 Markdown。
            JSON 字段：
            {
              "status": "FOUND|NOT_FOUND|INSUFFICIENT|BLOCKED|ERROR",
              "summary": "一句话结论",
              "gaps": ["证据缺口"],
              "suggestedAdjustments": ["下一轮应该如何调整查询"],
              "shouldRetry": true,
              "confidence": 0.0
            }
            不能把未查询的数据源说成已经查询。证据不足时要明确告诉主 Agent。
            你需要自己决定是否继续子 Agent 内部循环：只有剩余循环预算且下一轮有明确参数调整会提升召回时，shouldRetry 才能为 true；如果已足够、被阻塞、无可执行调整或继续查询收益很低，shouldRetry 必须为 false。
            """;

    private final OpsAgentLlmClient llmClient;
    private final OpsSubAgentDecisionJsonMapper jsonMapper;

    OpsSubAgentDecisionProtocolService(OpsAgentLlmClient llmClient) {
        this.llmClient = llmClient;
        this.jsonMapper = new OpsSubAgentDecisionJsonMapper();
    }

    DecisionAttempt decide(DecisionInput input) {
        JSONObject json = llmClient.chatJsonObjectWithEagerSkillContext(
                input.source() + "-react-think",
                THINK_SYSTEM_PROMPT,
                decisionPrompt(input),
                skillNames(input.source()));
        if (json == null) {
            return DecisionAttempt.noJson();
        }
        List<String> validationErrors = OpsLlmJsonValidator.validateSubAgentDecision(json);
        if (!validationErrors.isEmpty()) {
            llmClient.rejectDegradation(
                    input.source() + "-react-think",
                    "子 Agent THINK JSON 校验失败：" + validationErrors);
            return DecisionAttempt.invalidSchema(validationErrors);
        }
        return DecisionAttempt.valid(jsonMapper.toDecision(json, input.fallback()));
    }

    ReviewAttempt review(ReviewInput input) {
        JSONObject json = llmClient.chatJsonObjectWithEagerSkillContext(
                input.source() + "-react-review",
                REVIEW_SYSTEM_PROMPT,
                reviewPrompt(input),
                skillNames(input.source()));
        if (json == null) {
            return ReviewAttempt.noJson();
        }
        List<String> validationErrors = OpsLlmJsonValidator.validateSubAgentReview(json);
        if (!validationErrors.isEmpty()) {
            llmClient.rejectDegradation(
                    input.source() + "-react-review",
                    "子 Agent REFLECT JSON 校验失败：" + validationErrors);
            return ReviewAttempt.invalidSchema(validationErrors);
        }
        return ReviewAttempt.valid(jsonMapper.toReview(json, input.fallback()));
    }

    private String decisionPrompt(DecisionInput input) {
        return """
                数据源：%s
                可用资源能力：
                %s

                子任务：
                - agent: %s
                - goal: %s
                - reason: %s

                用户问题：%s
                已解析过滤条件：%s
                默认参数：rangeMinutes=%s, promWindow=%s, includeRecentLogs=%s, subAgentMaxIterations=%s
                上一轮 observation：
                %s

                请生成本子 Agent 的 ReAct THINK 查询策略。
                """.formatted(
                input.source(),
                input.datasourceProfile(),
                input.task().getAgent(),
                input.task().getGoal(),
                input.task().getReason(),
                value(input.questionContext().originalQuestion()),
                input.questionContext().describeFilters(),
                input.request().getRangeMinutes(),
                input.request().getPromWindow(),
                input.request().getIncludeRecentLogs(),
                input.request().getSubAgentMaxIterations(),
                hasText(input.previousObservation())
                        ? input.previousObservation()
                        : "无，当前是第一次查询。");
    }

    private String reviewPrompt(ReviewInput input) {
        return """
                数据源：%s
                子任务：%s / %s
                用户问题：%s
                已解析过滤条件：%s
                查询参数：rangeMinutes=%s, promWindow=%s
                子 Agent 最大循环次数：%s

                observation:
                %s

                请完成 ReAct OBSERVE/REFLECT 判断。
                """.formatted(
                input.source(),
                input.task().getAgent(),
                input.task().getGoal(),
                value(input.questionContext().originalQuestion()),
                input.questionContext().describeFilters(),
                input.request().getRangeMinutes(),
                input.request().getPromWindow(),
                input.request().getSubAgentMaxIterations(),
                input.observation());
    }

    private List<String> skillNames(String source) {
        String normalized = source == null ? "" : source.trim().toLowerCase();
        return switch (normalized) {
            case "rag" -> List.of("rag-knowledge-agent");
            case "elasticsearch" -> List.of("es-log-agent");
            case "prometheus" -> List.of("prometheus-agent");
            case "mysql_slow_sql", "mysql-slow-sql" -> List.of("mysql-slow-sql-agent");
            default -> List.of();
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    record DecisionInput(
            String source,
            OpsAnalysisResponseDTO.InvestigationTaskDTO task,
            OpsAgentRunRequestDTO request,
            OpsQuestionContext questionContext,
            String datasourceProfile,
            String previousObservation,
            OpsSubAgentDecision fallback) {
    }

    record ReviewInput(
            String source,
            OpsAnalysisResponseDTO.InvestigationTaskDTO task,
            OpsAgentRunRequestDTO request,
            OpsQuestionContext questionContext,
            String observation,
            OpsAgentReview fallback) {
    }

    record DecisionAttempt(
            Status status,
            OpsSubAgentDecision decision,
            List<String> validationErrors) {
        static DecisionAttempt valid(OpsSubAgentDecision decision) {
            return new DecisionAttempt(Status.VALID, decision, List.of());
        }

        static DecisionAttempt noJson() {
            return new DecisionAttempt(Status.NO_JSON, null, List.of());
        }

        static DecisionAttempt invalidSchema(List<String> validationErrors) {
            return new DecisionAttempt(Status.INVALID_SCHEMA, null, validationErrors);
        }

        boolean valid() {
            return status == Status.VALID;
        }
    }

    record ReviewAttempt(
            Status status,
            OpsAgentReview review,
            List<String> validationErrors) {
        static ReviewAttempt valid(OpsAgentReview review) {
            return new ReviewAttempt(Status.VALID, review, List.of());
        }

        static ReviewAttempt noJson() {
            return new ReviewAttempt(Status.NO_JSON, null, List.of());
        }

        static ReviewAttempt invalidSchema(List<String> validationErrors) {
            return new ReviewAttempt(Status.INVALID_SCHEMA, null, validationErrors);
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
