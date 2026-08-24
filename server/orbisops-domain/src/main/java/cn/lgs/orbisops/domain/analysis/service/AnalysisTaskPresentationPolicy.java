package cn.lgs.orbisops.domain.analysis.service;

import cn.lgs.orbisops.domain.analysis.model.AnalysisTaskSnapshot;
import cn.lgs.orbisops.domain.analysis.model.AnalysisTaskView;
import cn.lgs.orbisops.domain.runtime.graph.model.GraphEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class AnalysisTaskPresentationPolicy {

    /** Server-owned, user-safe label persisted alongside internal runtime requests. */
    public static final String PUBLIC_RUN_GOAL_METADATA_KEY = "publicRunGoal";

    private static final String LANDING_RUNTIME_PROMPT_PREFIX =
            "You are the platform Landing ReAct runtime.";
    private static final Pattern HTTP_ERROR_ENVELOPE = Pattern.compile(
            "(?is)^(?:exception|error):\\s*[45]\\d{2}\\s*-\\s*(?:\\{|\\[|<).*");

    public AnalysisTaskView present(AnalysisTaskSnapshot snapshot) {
        Map<String, Object> metadata = objectMap(snapshot.request().get("metadata"));
        String responseText = firstText(snapshot.response().get("content"),
                snapshot.response().get("markdownReport"));
        boolean failedEnvelope = isErrorEnvelope(responseText);
        String technicalError = failedEnvelope ? responseText : snapshot.storedError();
        String rawSource = firstText(
                metadata.get("source"),
                metadata.get("triggerSource"),
                metadata.get("_trustedTriggerSource"),
                objectMap(metadata.get("opsAnalysisRequest")).get("triggerSource"),
                "CHAT").toUpperCase(Locale.ROOT);
        String taskType = taskType(rawSource);
        return new AnalysisTaskView(
                snapshot.runId(),
                snapshot.projectId(),
                snapshot.sessionId(),
                snapshot.userId(),
                snapshot.agentId(),
                snapshot.agentVersion(),
                snapshot.agentDefinitionHash(),
                snapshot.executionHarness(),
                failedEnvelope ? "FAILED" : snapshot.storedStatus(),
                normalizeSource(taskType),
                taskType,
                publicGoal(snapshot.request(), "运行详情"),
                snapshot.response(),
                failureSummary(technicalError),
                technicalError,
                snapshot.createdAt(),
                snapshot.updatedAt());
    }

    /**
     * Returns the human-facing objective for a run without exposing a server-owned
     * system instruction. Runtime requests remain private execution inputs; this
     * projection is the single boundary used by list/detail/dashboard views.
     */
    public static String publicGoal(Map<String, Object> request, String fallback) {
        Map<String, Object> safeRequest = request == null ? Map.of() : request;
        Map<String, Object> metadata = objectMap(safeRequest.get("metadata"));
        String candidate = firstText(
                metadata.get(PUBLIC_RUN_GOAL_METADATA_KEY),
                safeRequest.get(PUBLIC_RUN_GOAL_METADATA_KEY),
                safeRequest.get("query"),
                safeRequest.get("question"),
                safeRequest.get("goal"),
                fallback);
        if (!isInternalRuntimePrompt(candidate)) return candidate;
        String packageId = firstText(
                metadata.get("changePackageId"),
                safeRequest.get("changePackageId"));
        return packageId.isBlank()
                ? "受控变更落地运行"
                : "受控变更包 " + packageId + " 的落地运行";
    }

    public static boolean isInternalRuntimePrompt(String value) {
        return text(value).startsWith(LANDING_RUNTIME_PROMPT_PREFIX);
    }

    public String responseSummary(AnalysisTaskView task) {
        String responseText = firstText(task.response().get("content"), task.response().get("markdownReport"));
        if (isErrorEnvelope(responseText)) {
            return firstText(task.errorMessage(), failureSummary(responseText));
        }
        return firstText(responseText, task.errorMessage());
    }

    public List<String> summaries(List<GraphEvent> events, String... tokens) {
        return safeEvents(events).stream()
                .filter(event -> containsAny(event.eventType(), tokens))
                .map(event -> publicSummary(event.summary()))
                .filter(summary -> !text(summary).isBlank())
                .distinct()
                .limit(50)
                .toList();
    }

    public List<String> statusSummaries(List<GraphEvent> events, String... tokens) {
        return safeEvents(events).stream()
                // Capability projection hides ungranted tools before any attempted call.
                // Actual TOOL_CALL_BLOCKED events remain visible as unresolved work.
                .filter(event -> !"RUNTIME_TOOL_AUTHORITY_BLOCKED".equals(event.eventType()))
                .filter(event -> containsAny(event.status(), tokens) || containsAny(event.eventType(), tokens))
                .map(event -> publicSummary(event.summary()))
                .filter(summary -> !text(summary).isBlank())
                .distinct()
                .limit(50)
                .toList();
    }

    public boolean isErrorEnvelope(String output) {
        String normalized = text(output);
        return HTTP_ERROR_ENVELOPE.matcher(normalized).matches()
                || normalized.startsWith("Graph 执行失败：")
                || normalized.startsWith("ReAct 工具流执行失败：");
    }

    /** Exact server-owned tool reason codes; raw diagnostics never become user-facing copy. */
    public static String toolFailureSummary(String reasonCode) {
        String label = switch (text(reasonCode)) {
            case "MCP_TOOL_NOT_ENABLED" -> "工具尚未在当前任务中启用，请检查工具启用状态";
            case "MCP_TOOL_SCHEMA_NOT_HYDRATED" -> "工具契约尚未加载，请检查工具连接与契约披露记录";
            case "MCP_POLICY_PENDING_REVIEW", "MCP_POLICY_MISSING", "MCP_TOOL_EFFECT_UNKNOWN" ->
                    "工具策略尚未完成审核，请检查工具审核状态";
            case "MCP_POLICY_STALE" -> "工具契约已变更，原审核已失效，请重新审核当前契约";
            case "MCP_POLICY_DISABLED" -> "工具策略已停用，请检查工具启用与审核状态";
            case "MCP_TOOL_NOT_AUTHORIZED" -> "当前项目未授权此工具，请检查项目工具授权";
            case "READ_ONLY_REQUIRED" -> "工具不满足本次只读要求，请检查工具效果与只读策略";
            case "MCP_TOOL_REQUIRES_CHANGE_PACKAGE", "TARGET_WRITE_TOOL_REQUIRES_APPROVED_CHANGE_PACKAGE" ->
                    "此工具需要已审批的变更包，请按变更审批与落地流程执行";
            case "MCP_STAGE_NOT_ALLOWED" -> "当前阶段不允许调用此工具，请检查工具阶段权限";
            case "MCP_TOOL_ARGUMENT_POLICY_VIOLATION" -> "工具参数未满足已审核策略，请检查允许的参数范围";
            case "MCP_TOOL_BLOCKED", "TOOL_BLOCKED" -> "工具调用被平台策略拦截，请检查工具审核与项目授权";
            default -> "";
        };
        return label.isEmpty() ? "" : label + "。本轮未完成，请在运行详情核对已有工具回执与实际资源状态。";
    }

    public String failureSummary(String technicalError) {
        if (text(technicalError).isBlank()) return "";
        String toolSummary = toolFailureSummary(technicalError);
        if (!toolSummary.isEmpty()) return toolSummary;
        if ("SKILL_RUNTIME_ACCESS_REVOKED".equals(text(technicalError))) {
            return "本轮使用的 Skill 已停用或项目授权已撤回，后续执行已停止。请检查项目 Skill 权限后发起新任务。";
        }
        String normalized = technicalError.toLowerCase(Locale.ROOT);
        if (normalized.contains("422")) {
            return "模型工具协议校验失败（HTTP 422）。本次任务未完成；请检查工具参数 Schema，并核对失败前的工具回执。";
        }
        if (normalized.contains("401") || normalized.contains("unauthorized")) {
            return "模型或工具服务鉴权失败。本次任务未完成；请检查服务凭据，并核对失败前的工具回执。";
        }
        if (normalized.contains("timeout") || normalized.contains("timed out")) {
            return "模型或工具服务调用超时。本次任务未完成；请先核对工具回执和实际资源状态，再决定是否重试。";
        }
        return "任务执行失败，未生成可用分析结论。请在完整运行轨迹中核对失败原因、工具回执和实际资源状态。";
    }

    private String taskType(String source) {
        String normalized = text(source).toUpperCase(Locale.ROOT);
        if (normalized.contains("CHANNEL")) return "CHANNEL";
        if (normalized.contains("ALERT")) return "ALERT";
        if (normalized.contains("SCHEDULE") || normalized.contains("TASK")) return "SCHEDULE";
        if (normalized.contains("LANDING")) return "LANDING";
        return "CHAT";
    }

    private String normalizeSource(String taskType) {
        return switch (taskType) {
            case "CHANNEL" -> "CHANNEL";
            case "ALERT" -> "ALERTMANAGER";
            case "SCHEDULE" -> "SCHEDULE";
            case "LANDING" -> "LANDING";
            default -> "CHAT";
        };
    }

    private boolean containsAny(String value, String... tokens) {
        String normalized = text(value).toUpperCase(Locale.ROOT);
        if (tokens == null) return false;
        for (String token : tokens) {
            if (normalized.contains(text(token).toUpperCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private List<GraphEvent> safeEvents(List<GraphEvent> events) {
        return events == null ? List.of() : events;
    }

    private static Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private static String firstText(Object... values) {
        if (values == null) return "";
        for (Object value : values) {
            String normalized = text(value);
            if (!normalized.isBlank()) return normalized;
        }
        return "";
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String publicSummary(String summary) {
        String value = text(summary);
        if (value.contains("工具超出当前 Agent Authority")
                || value.contains("工具超出当前运行权限")) {
            return "当前运行权限不足，未执行未授权的操作。";
        }
        return value;
    }
}
