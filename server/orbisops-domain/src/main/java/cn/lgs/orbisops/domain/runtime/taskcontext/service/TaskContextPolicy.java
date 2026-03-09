package cn.lgs.orbisops.domain.runtime.taskcontext.service;

import cn.lgs.orbisops.domain.runtime.taskcontext.model.TaskContextContent;
import cn.lgs.orbisops.domain.runtime.taskcontext.model.TaskContextEventFact;
import cn.lgs.orbisops.domain.runtime.taskcontext.model.TaskContextSnapshot;
import cn.lgs.orbisops.domain.runtime.taskcontext.model.TaskContextState;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

public final class TaskContextPolicy {

    public TaskContextSnapshot start(
            String runId,
            String sessionId,
            String projectId,
            String agentId,
            String goal,
            List<String> usedSkills,
            TaskContextSnapshot existing,
            LocalDateTime now) {
        TaskContextContent content = new TaskContextContent(
                goal,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("理解问题", "收集证据", "生成结论"),
                List.of(),
                usedSkills);
        return snapshot(runId, sessionId, projectId, agentId, TaskContextState.RUNNING,
                content, "任务已开始，等待 Agent 取证。", null, existing, now);
    }

    public TaskContextSnapshot progress(
            String runId,
            String sessionId,
            String projectId,
            String agentId,
            String goal,
            List<String> usedSkills,
            String state,
            List<TaskContextEventFact> facts,
            String summary,
            TaskContextSnapshot existing,
            LocalDateTime now) {
        TaskContextState taskState = TaskContextState.progress(state);
        TaskContextContent content = content(goal, usedSkills, taskState, facts, false);
        return snapshot(runId, sessionId, projectId, agentId, taskState,
                content, text(summary), null, existing, now);
    }

    public TaskContextSnapshot finish(
            String runId,
            String sessionId,
            String projectId,
            String agentId,
            String goal,
            List<String> usedSkills,
            String status,
            String output,
            List<TaskContextEventFact> facts,
            TaskContextSnapshot existing,
            LocalDateTime now) {
        TaskContextState state = TaskContextState.terminal(status);
        TaskContextContent content = content(goal, usedSkills, state, facts, true);
        return snapshot(runId, sessionId, projectId, agentId, state,
                content, abbreviate(output, 1000), null, existing, now);
    }

    private TaskContextContent content(
            String goal,
            List<String> usedSkills,
            TaskContextState state,
            List<TaskContextEventFact> facts,
            boolean terminal) {
        List<TaskContextEventFact> safeFacts = facts == null ? List.of() : facts;
        List<String> knownFacts = safeFacts.stream()
                .filter(fact -> succeeded(fact.status()))
                .map(fact -> first(fact.content(), fact.summary()))
                .filter(value -> !value.isBlank())
                .map(value -> abbreviate(value, 240))
                .limit(8)
                .toList();
        List<String> ruledOut = safeFacts.stream()
                .filter(this::ruledOut)
                .map(fact -> abbreviate(first(fact.summary(), fact.eventType()), 240))
                .filter(value -> !value.isBlank())
                .limit(8)
                .toList();
        List<String> completedActions = safeFacts.stream()
                .filter(fact -> succeeded(fact.status()))
                .map(fact -> first(fact.summary(), fact.eventType()))
                .filter(value -> !value.isBlank())
                .limit(12)
                .toList();
        List<String> toolSummaries = safeFacts.stream()
                .filter(fact -> upper(fact.eventType()).contains("TOOL"))
                .map(fact -> first(fact.summary(), fact.eventType()))
                .filter(value -> !value.isBlank())
                .limit(8)
                .toList();
        List<String> openQuestions = terminal && state != TaskContextState.COMPLETED
                ? List.of("任务未成功结束，需要查看 run trace 和最近错误。")
                : List.of();
        List<String> pendingActions = terminal || state.terminal()
                ? List.of()
                : List.of("继续收集证据", "补齐结论", "生成最终报告");
        return new TaskContextContent(
                goal,
                List.of(),
                knownFacts,
                ruledOut,
                openQuestions,
                completedActions,
                pendingActions,
                toolSummaries,
                usedSkills);
    }

    private TaskContextSnapshot snapshot(
            String runId,
            String sessionId,
            String projectId,
            String agentId,
            TaskContextState state,
            TaskContextContent content,
            String summary,
            Long lastEventId,
            TaskContextSnapshot existing,
            LocalDateTime now) {
        if (now == null) throw new IllegalArgumentException("TASK_CONTEXT_TIME_REQUIRED");
        int version = existing == null ? 1 : existing.version() + 1;
        LocalDateTime createdAt = existing == null ? now : existing.createdAt();
        return new TaskContextSnapshot(
                existing == null ? null : existing.persistenceId(),
                runId, sessionId, projectId, agentId, state, content, summary,
                lastEventId, version, createdAt, now);
    }

    private boolean ruledOut(TaskContextEventFact fact) {
        if (fact == null) return false;
        String status = upper(fact.status());
        String summary = upper(fact.summary());
        return status.contains("NOT_FOUND") || status.contains("INSUFFICIENT")
                || summary.contains("NOT_FOUND") || summary.contains("无结果")
                || summary.contains("未发现");
    }

    private boolean succeeded(String status) {
        return "SUCCEEDED".equals(upper(status));
    }

    private String first(String first, String second) {
        String value = text(first);
        return value.isBlank() ? text(second) : value;
    }

    private String abbreviate(String value, int maxLength) {
        String normalized = text(value);
        if (normalized.length() <= maxLength) return normalized;
        return normalized.substring(0, Math.max(0, maxLength)) + "...";
    }

    private String upper(String value) {
        return text(value).toUpperCase(Locale.ROOT);
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
