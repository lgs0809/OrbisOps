package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeEvent;
import cn.lgs.orbisops.trigger.ops.runtime.OpsToolSchemaNormalizer;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.chat.model.ToolContext;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Plain boundary for LLM model/tool runtime events and trace propagation. */
final class OpsLlmObservabilityService {

    void modelStarted(
            String agentName,
            String systemPrompt,
            String userPrompt,
            Collection<String> skillNames,
            boolean skillToolEnabled,
            boolean jsonResponseFormatEnabled,
            OpsLlmTraceContext.Trace trace) {
        recordModelEvent(
                trace,
                "MODEL_CALL_STARTED",
                "RUNNING",
                agentName,
                "自定义运维 LLM 调用开始。",
                modelPayload(
                        agentName,
                        systemPrompt,
                        userPrompt,
                        skillNames,
                        null,
                        0L,
                        skillToolEnabled,
                        jsonResponseFormatEnabled));
    }

    void modelCompleted(
            String agentName,
            String systemPrompt,
            String userPrompt,
            Collection<String> skillNames,
            String output,
            long durationMs,
            boolean skillToolEnabled,
            boolean jsonResponseFormatEnabled,
            OpsLlmTraceContext.Trace trace) {
        recordModelEvent(
                trace,
                "MODEL_CALL_FINISHED",
                "SUCCEEDED",
                agentName,
                "自定义运维 LLM 调用完成。",
                modelPayload(
                        agentName,
                        systemPrompt,
                        userPrompt,
                        skillNames,
                        output,
                        durationMs,
                        skillToolEnabled,
                        jsonResponseFormatEnabled));
    }

    void modelFailed(
            String agentName,
            String systemPrompt,
            String userPrompt,
            Collection<String> skillNames,
            RuntimeException error,
            long durationMs,
            boolean skillToolEnabled,
            boolean jsonResponseFormatEnabled,
            OpsLlmTraceContext.Trace trace) {
        Map<String, Object> payload = modelPayload(
                agentName,
                systemPrompt,
                userPrompt,
                skillNames,
                null,
                durationMs,
                skillToolEnabled,
                jsonResponseFormatEnabled);
        payload.put("error", error == null ? null : error.getMessage());
        recordModelEvent(
                trace,
                "MODEL_CALL_FAILED",
                "FAILED",
                agentName,
                "自定义运维 LLM 调用失败：" + (error == null ? null : error.getMessage()),
                payload);
    }

    void modelJsonInvalid(
            String agentName,
            String reason,
            String output,
            OpsLlmTraceContext.Trace trace) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agentName", value(agentName));
        payload.put("reason", value(reason));
        payload.put("output", abbreviate(output, 4000));
        payload.put("outputChars", output == null ? 0 : output.length());
        recordModelEvent(
                trace,
                "MODEL_JSON_INVALID",
                "FAILED",
                agentName,
                "自定义运维 LLM 未返回合法 JSON：" + value(reason),
                payload);
    }

    ToolCallback traceTool(
            String agentName,
            ToolCallback delegate,
            Collection<String> skillNames,
            OpsLlmTraceContext.Trace trace) {
        if (trace == null) {
            return delegate;
        }
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return OpsToolSchemaNormalizer.normalize(delegate.getToolDefinition());
            }

            @Override
            public ToolMetadata getToolMetadata() {
                return delegate.getToolMetadata();
            }

            @Override
            public String call(String toolInput) {
                return callToolWithTrace(
                        agentName,
                        delegate,
                        skillNames,
                        trace,
                        toolInput,
                        null,
                        false);
            }

            @Override
            public String call(String toolInput, ToolContext toolContext) {
                return callToolWithTrace(
                        agentName,
                        delegate,
                        skillNames,
                        trace,
                        toolInput,
                        toolContext,
                        true);
            }
        };
    }

    private String callToolWithTrace(
            String agentName,
            ToolCallback delegate,
            Collection<String> skillNames,
            OpsLlmTraceContext.Trace trace,
            String toolInput,
            ToolContext toolContext,
            boolean useToolContext) {
        String toolName = toolName(delegate);
        long startedNanos = System.nanoTime();
        recordToolEvent(
                trace,
                "TOOL_CALL_STARTED",
                "RUNNING",
                agentName,
                "Skill 工具调用开始：" + toolName,
                toolPayload(agentName, skillNames, toolName, toolInput, null, 0L));
        try {
            String output = useToolContext
                    ? delegate.call(toolInput, toolContext)
                    : delegate.call(toolInput);
            recordToolEvent(
                    trace,
                    "TOOL_CALL_FINISHED",
                    "SUCCEEDED",
                    agentName,
                    "Skill 工具调用完成：" + toolName,
                    toolPayload(
                            agentName,
                            skillNames,
                            toolName,
                            toolInput,
                            output,
                            elapsedMs(startedNanos)));
            return output;
        } catch (RuntimeException e) {
            Map<String, Object> payload = toolPayload(
                    agentName,
                    skillNames,
                    toolName,
                    toolInput,
                    null,
                    elapsedMs(startedNanos));
            payload.put("error", e.getMessage());
            recordToolEvent(
                    trace,
                    "TOOL_CALL_FAILED",
                    "FAILED",
                    agentName,
                    "Skill 工具调用失败：" + toolName,
                    payload);
            throw e;
        }
    }

    private void recordModelEvent(
            OpsLlmTraceContext.Trace trace,
            String eventType,
            String status,
            String agentName,
            String summary,
            Map<String, Object> payload) {
        if (trace == null) {
            return;
        }
        trace.record(OpsRuntimeEvent.builder()
                .eventType(eventType)
                .nodeId(trace.nodeId())
                .nodeType(trace.nodeType())
                .agent(hasText(trace.agent()) ? trace.agent() : agentName)
                .source(trace.source())
                .status(status)
                .summary(summary)
                .payload(payload)
                .build());
    }

    private void recordToolEvent(
            OpsLlmTraceContext.Trace trace,
            String eventType,
            String status,
            String agentName,
            String summary,
            Map<String, Object> payload) {
        recordModelEvent(trace, eventType, status, agentName, summary, payload);
    }

    private Map<String, Object> modelPayload(
            String agentName,
            String systemPrompt,
            String userPrompt,
            Collection<String> skillNames,
            String output,
            long durationMs,
            boolean skillToolEnabled,
            boolean jsonResponseFormatEnabled) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agentName", value(agentName));
        payload.put("systemPromptChars", systemPrompt == null ? 0 : systemPrompt.length());
        payload.put("userPromptChars", userPrompt == null ? 0 : userPrompt.length());
        payload.put("promptChars",
                (systemPrompt == null ? 0 : systemPrompt.length())
                        + (userPrompt == null ? 0 : userPrompt.length()));
        payload.put("systemPrompt", abbreviate(systemPrompt, 4000));
        payload.put("userPrompt", abbreviate(userPrompt, 4000));
        payload.put("skillNames", normalizedSkillNames(skillNames));
        payload.put("skillToolEnabled", skillToolEnabled);
        payload.put("toolCount",
                skillToolEnabled && skillNames != null && !skillNames.isEmpty() ? 1 : 0);
        payload.put("jsonResponseFormatEnabled", jsonResponseFormatEnabled);
        if (output != null) {
            payload.put("output", abbreviate(output, 4000));
            payload.put("outputChars", output.length());
        }
        if (durationMs > 0) {
            payload.put("durationMs", durationMs);
        }
        return payload;
    }

    private Map<String, Object> toolPayload(
            String agentName,
            Collection<String> skillNames,
            String toolName,
            String input,
            String output,
            long durationMs) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("agentName", value(agentName));
        payload.put("toolKind", "skill");
        payload.put("toolName", value(toolName));
        payload.put("skillNames", normalizedSkillNames(skillNames));
        payload.put("input", abbreviate(input, 4000));
        if (output != null) {
            payload.put("output", abbreviate(output, 4000));
            payload.put("outputChars", output.length());
        }
        if (durationMs > 0) {
            payload.put("durationMs", durationMs);
        }
        return payload;
    }

    private List<String> normalizedSkillNames(Collection<String> skillNames) {
        if (skillNames == null || skillNames.isEmpty()) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        skillNames.stream()
                .filter(this::hasText)
                .map(String::trim)
                .forEach(names::add);
        return List.copyOf(names);
    }

    private String toolName(ToolCallback callback) {
        ToolDefinition definition = callback == null ? null : callback.getToolDefinition();
        return definition == null ? "" : definition.name();
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
