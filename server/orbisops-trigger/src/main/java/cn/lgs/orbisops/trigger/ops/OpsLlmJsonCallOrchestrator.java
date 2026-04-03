package cn.lgs.orbisops.trigger.ops;

import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;

import java.util.Collection;
import java.util.List;

/** Plain protocol orchestrator for initial JSON call, Skill retry and repair retry. */
final class OpsLlmJsonCallOrchestrator {

    private static final Logger log =
            LoggerFactory.getLogger(OpsLlmJsonCallOrchestrator.class);

    private final OpsLlmJsonProtocol jsonProtocol;
    private final OpsLlmSkillContextService skillContextService;
    private final OpsLlmObservabilityService observabilityService;

    OpsLlmJsonCallOrchestrator(
            OpsLlmJsonProtocol jsonProtocol,
            OpsLlmSkillContextService skillContextService,
            OpsLlmObservabilityService observabilityService) {
        this.jsonProtocol = jsonProtocol;
        this.skillContextService = skillContextService;
        this.observabilityService = observabilityService;
    }

    Result execute(Input input, ContentCaller caller) {
        return executeInternal(input, caller, false);
    }

    Result executeWithEagerSkillContext(Input input, ContentCaller caller) {
        return executeInternal(input, caller, true);
    }

    private Result executeInternal(Input input, ContentCaller caller, boolean eagerInitialSkillContext) {
        String initialContent = caller.call(
                input.agentName(),
                input.chatModel(),
                eagerInitialSkillContext
                        ? skillContextService.withEagerContext(
                                input.systemPrompt(),
                                input.skillNames(),
                                input.skillContextMaxChars())
                        : skillContextService.withLazyContext(
                                input.systemPrompt(),
                                input.skillNames(),
                                input.skillContextEnabled(),
                                input.skillContextMaxChars()),
                input.userPrompt(),
                input.skillNames(),
                !eagerInitialSkillContext);
        OpsLlmJsonProtocol.ParseResult result =
                jsonProtocol.parse(initialContent, input.maxOutputChars());
        if (result.json() != null) {
            return Result.success(result.json());
        }
        recordInvalid(input.agentName(), result, initialContent);

        if (jsonProtocol.shouldRetryWithEagerSkillContext(
                result,
                input.skillNames(),
                input.skillRetryEnabled())) {
            log.warn(
                    "{} LLM JSON 输出为空，准备注入本次绑定 Skill 正文后重试：{}",
                    input.agentName(),
                    result.reason());
            String skillRetryContent = caller.call(
                    input.agentName() + "-skill-context-retry",
                    input.chatModel(),
                    skillContextService.withEagerContext(
                            input.systemPrompt(),
                            input.skillNames(),
                            input.skillRetryMaxChars()),
                    input.userPrompt(),
                    input.skillNames(),
                    false);
            result = jsonProtocol.parse(
                    skillRetryContent,
                    input.maxOutputChars());
            if (result.json() != null) {
                return Result.success(result.json());
            }
            recordInvalid(
                    input.agentName() + "-skill-context-retry",
                    result,
                    skillRetryContent);
        }

        if (input.repairRetryEnabled()) {
            log.warn(
                    "{} LLM JSON 输出无效，准备进行一次模型自修复重试：{}",
                    input.agentName(),
                    result.reason());
            String retryContent = caller.call(
                    input.agentName() + "-json-repair",
                    input.chatModel(),
                    jsonProtocol.repairSystemPrompt(),
                    jsonProtocol.repairUserPrompt(
                            input.systemPrompt(),
                            input.userPrompt(),
                            initialContent,
                            result.reason()),
                    List.of(),
                    false);
            result = jsonProtocol.parse(
                    retryContent,
                    input.maxOutputChars());
            if (result.json() != null) {
                return Result.success(result.json());
            }
            recordInvalid(
                    input.agentName() + "-json-repair",
                    result,
                    retryContent);
        }
        return Result.failed(result.reason(), result.exception());
    }

    private void recordInvalid(
            String agentName,
            OpsLlmJsonProtocol.ParseResult result,
            String output) {
        observabilityService.modelJsonInvalid(
                agentName,
                result.reason(),
                output,
                OpsLlmTraceContext.current());
    }

    @FunctionalInterface
    interface ContentCaller {
        String call(
                String agentName,
                ChatModel chatModel,
                String systemPrompt,
                String userPrompt,
                Collection<String> skillNames,
                boolean enableSkillTool);
    }

    record Input(
            String agentName,
            ChatModel chatModel,
            String systemPrompt,
            String userPrompt,
            Collection<String> skillNames,
            int maxOutputChars,
            boolean skillContextEnabled,
            int skillContextMaxChars,
            boolean skillRetryEnabled,
            int skillRetryMaxChars,
            boolean repairRetryEnabled) {
    }

    record Result(JSONObject json, String reason, Exception exception) {

        static Result success(JSONObject json) {
            return new Result(json, "", null);
        }

        static Result failed(String reason, Exception exception) {
            return new Result(null, reason, exception);
        }

        boolean success() {
            return json != null;
        }
    }
}
