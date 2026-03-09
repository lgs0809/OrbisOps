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
                firstText(snapshot.request().get("query"), snapshot.request().get("question")),
                snapshot.response(),
                failureSummary(technicalError),
                technicalError,
                snapshot.createdAt(),
                snapshot.updatedAt());
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
                .map(GraphEvent::summary)
                .filter(summary -> !text(summary).isBlank())
                .distinct()
                .limit(50)
                .toList();
    }

    public List<String> statusSummaries(List<GraphEvent> events, String... tokens) {
        return safeEvents(events).stream()
                .filter(event -> containsAny(event.status(), tokens) || containsAny(event.eventType(), tokens))
                .map(GraphEvent::summary)
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

    public String failureSummary(String technicalError) {
        if (text(technicalError).isBlank()) return "";
        String normalized = technicalError.toLowerCase(Locale.ROOT);
        if (normalized.contains("422")) {
            return "模型工具协议校验失败（HTTP 422）。本次任务没有生成分析结论，也没有执行工具或生产变更；请修复工具参数 Schema 后重新运行。";
        }
        if (normalized.contains("401") || normalized.contains("unauthorized")) {
            return "模型或工具服务鉴权失败。本次任务没有生成可信结论，也没有执行生产变更；请检查服务凭据后重新运行。";
        }
        if (normalized.contains("timeout") || normalized.contains("timed out")) {
            return "模型或工具服务调用超时。本次任务已按失败收口，没有执行生产变更；请检查服务状态后重试。";
        }
        return "任务执行失败，未生成可用分析结论，也没有执行生产变更。技术错误已保留在完整运行轨迹中。";
    }

    private String taskType(String source) {
        String normalized = text(source).toUpperCase(Locale.ROOT);
        if (normalized.contains("CHANNEL")) return "CHANNEL";
        if (normalized.contains("ALERT")) return "ALERT";
        if (normalized.contains("SCHEDULE") || normalized.contains("TASK")) return "SCHEDULE";
        return "CHAT";
    }

    private String normalizeSource(String taskType) {
        return switch (taskType) {
            case "CHANNEL" -> "CHANNEL";
            case "ALERT" -> "ALERTMANAGER";
            case "SCHEDULE" -> "SCHEDULE";
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

    private Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private String firstText(Object... values) {
        if (values == null) return "";
        for (Object value : values) {
            String normalized = text(value);
            if (!normalized.isBlank()) return normalized;
        }
        return "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
