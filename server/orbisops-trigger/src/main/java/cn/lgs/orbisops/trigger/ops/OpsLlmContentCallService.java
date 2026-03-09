package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimePersistenceException;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.ai.tool.ToolCallback;

import java.util.Collection;

/** Plain application-facing service for one observable LLM content call. */
final class OpsLlmContentCallService {

    private final OpsLlmModelCallExecutor modelCallExecutor;
    private final OpsLlmSkillContextService skillContextService;
    private final OpsLlmObservabilityService observabilityService;

    OpsLlmContentCallService(
            OpsLlmModelCallExecutor modelCallExecutor,
            OpsLlmSkillContextService skillContextService,
            OpsLlmObservabilityService observabilityService) {
        this.modelCallExecutor = modelCallExecutor;
        this.skillContextService = skillContextService;
        this.observabilityService = observabilityService;
    }

    String call(Input input) {
        OpsLlmTraceContext.Trace trace = OpsLlmTraceContext.current();
        long startedNanos = System.nanoTime();
        observabilityService.modelStarted(
                input.agentName(),
                input.systemPrompt(),
                input.userPrompt(),
                input.skillNames(),
                input.enableSkillTool(),
                input.jsonResponseFormatEnabled(),
                trace);
        String content;
        try {
            ToolCallback toolCallback = input.enableSkillTool()
                    ? skillContextService.skillTool(input.skillNames())
                    .map(tool -> observabilityService.traceTool(
                            input.agentName(),
                            tool,
                            input.skillNames(),
                            trace))
                    .orElse(null)
                    : null;
            OpsLlmModelCallExecutor.ExecutionResult<String> execution = modelCallExecutor.execute(
                    new OpsLlmModelCallExecutor.Input(
                            input.chatModel(),
                            input.systemPrompt(),
                            input.userPrompt(),
                            toolCallback,
                            responseOptions(
                                    input.jsonResponseFormatEnabled(),
                                    input.jsonMaxCompletionTokens()),
                            trace,
                            input.modelCallTimeoutSeconds()));
            content = execution.value();
            startedNanos = System.nanoTime() - java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(
                    execution.executionMs());
        } catch (OpsRuntimePersistenceException error) {
            throw error;
        } catch (RuntimeException e) {
            observabilityService.modelFailed(
                    input.agentName(),
                    input.systemPrompt(),
                    input.userPrompt(),
                    input.skillNames(),
                    e,
                    elapsedMs(startedNanos),
                    input.enableSkillTool(),
                    input.jsonResponseFormatEnabled(),
                    trace);
            throw e;
        }
        observabilityService.modelCompleted(
                input.agentName(),
                input.systemPrompt(),
                input.userPrompt(),
                input.skillNames(),
                content,
                elapsedMs(startedNanos),
                input.enableSkillTool(),
                input.jsonResponseFormatEnabled(),
                trace);
        return content;
    }

    OpenAiChatOptions responseOptions(
            boolean jsonResponseFormatEnabled,
            int jsonMaxCompletionTokens) {
        if (!jsonResponseFormatEnabled) {
            return null;
        }
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormat.Type.JSON_OBJECT)
                        .build());
        if (jsonMaxCompletionTokens > 0) {
            builder.maxCompletionTokens(jsonMaxCompletionTokens);
        }
        return builder.build();
    }

    private long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    record Input(
            String agentName,
            ChatModel chatModel,
            String systemPrompt,
            String userPrompt,
            Collection<String> skillNames,
            boolean enableSkillTool,
            boolean jsonResponseFormatEnabled,
            int jsonMaxCompletionTokens,
            int modelCallTimeoutSeconds) {
    }
}
