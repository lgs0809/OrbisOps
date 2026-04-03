package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimePersistenceException;

import cn.lgs.orbisops.application.model.ModelAvailabilityPort;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeSkillResolver;
import com.alibaba.fastjson.JSONObject;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/** Shared LLM facade for the operations multi-agent runtime. */
@Service
public class OpsAgentLlmClient {

    private final OpsLlmModelAccessService modelAccessService;
    private final OpsLlmContentCallService contentCallService;
    private final OpsLlmJsonCallOrchestrator jsonCallOrchestrator;
    private final OpsLlmRuntimePolicy runtimePolicy;
    private final OpsAgentLlmSettings settings;

    public OpsAgentLlmClient(
            ApplicationContext applicationContext,
            ObjectProvider<ChatModel> chatModelProvider,
            ObjectProvider<OpsRuntimeSkillResolver> skillToolProvider,
            ModelAvailabilityPort aiModelAvailability,
            @Qualifier("opsModelCallExecutor") ExecutorService modelCallExecutor) {
        this(
                applicationContext,
                chatModelProvider,
                skillToolProvider,
                aiModelAvailability,
                modelCallExecutor,
                OpsAgentLlmSettings.defaults());
    }

    @Autowired
    public OpsAgentLlmClient(
            ApplicationContext applicationContext,
            ObjectProvider<ChatModel> chatModelProvider,
            ObjectProvider<OpsRuntimeSkillResolver> skillToolProvider,
            ModelAvailabilityPort aiModelAvailability,
            @Qualifier("opsModelCallExecutor") ExecutorService modelCallExecutor,
            OpsAgentLlmSettings settings) {
        OpsLlmSkillContextService skillContextService =
                new OpsLlmSkillContextService(skillToolProvider::getIfAvailable);
        OpsLlmObservabilityService observabilityService =
                new OpsLlmObservabilityService();
        this.modelAccessService = new OpsLlmModelAccessService(
                applicationContext,
                chatModelProvider,
                aiModelAvailability);
        this.contentCallService = new OpsLlmContentCallService(
                new OpsLlmModelCallExecutor(modelCallExecutor),
                skillContextService,
                observabilityService);
        this.jsonCallOrchestrator = new OpsLlmJsonCallOrchestrator(
                new OpsLlmJsonProtocol(),
                skillContextService,
                observabilityService);
        this.runtimePolicy = new OpsLlmRuntimePolicy();
        this.settings = settings == null ? OpsAgentLlmSettings.defaults() : settings;
    }

    OpsAgentLlmClient(
            OpsLlmModelAccessService modelAccessService,
            OpsLlmContentCallService contentCallService,
            OpsLlmJsonCallOrchestrator jsonCallOrchestrator,
            OpsLlmRuntimePolicy runtimePolicy,
            OpsAgentLlmSettings settings) {
        this.modelAccessService = modelAccessService;
        this.contentCallService = contentCallService;
        this.jsonCallOrchestrator = jsonCallOrchestrator;
        this.runtimePolicy = runtimePolicy;
        this.settings = settings == null ? OpsAgentLlmSettings.defaults() : settings;
    }

    public boolean available() {
        return modelAccessService.available(settings.enabled());
    }

    public Map<String, Object> status() {
        return runtimePolicy.status(
                modelAccessService.status(settings.enabled()),
                settings.policySettings());
    }

    public boolean failOnLlmDegradation() {
        return settings.failOnLlmDegradation();
    }

    public void rejectDegradation(String agentName, String reason) {
        runtimePolicy.reject(settings.policySettings(), agentName, reason);
    }

    public JSONObject chatJsonObject(
            String agentName,
            String systemPrompt,
            String userPrompt) {
        return chatJsonObject(agentName, systemPrompt, userPrompt, List.of());
    }

    public String chatText(
            String agentName,
            String systemPrompt,
            String userPrompt) {
        ChatModel chatModel = modelAccessService.resolveAvailable(settings.enabled());
        if (chatModel == null) {
            throw new OpsLlmDegradationException(
                    agentName,
                    "LLM 未启用或 ChatModel 不可用");
        }
        return callContent(
                agentName,
                chatModel,
                systemPrompt,
                userPrompt,
                List.of(),
                false);
    }

    public JSONObject chatJsonObject(
            String agentName,
            String systemPrompt,
            String userPrompt,
            Collection<String> skillNames) {
        return chatJsonObject(agentName, systemPrompt, userPrompt, skillNames, false);
    }

    JSONObject chatJsonObjectWithEagerSkillContext(
            String agentName,
            String systemPrompt,
            String userPrompt,
            Collection<String> skillNames) {
        return chatJsonObject(agentName, systemPrompt, userPrompt, skillNames, true);
    }

    private JSONObject chatJsonObject(
            String agentName,
            String systemPrompt,
            String userPrompt,
            Collection<String> skillNames,
            boolean eagerSkillContext) {
        ChatModel chatModel = modelAccessService.resolveAvailable(settings.enabled());
        if (chatModel == null) {
            return degrade(agentName, "LLM 未启用或 ChatModel 不可用", null);
        }
        try {
            OpsLlmJsonCallOrchestrator.Input input = new OpsLlmJsonCallOrchestrator.Input(
                    agentName,
                    chatModel,
                    systemPrompt,
                    userPrompt,
                    skillNames,
                    settings.maxOutputChars(),
                    settings.skillContextEnabled(),
                    settings.skillContextMaxChars(),
                    settings.jsonSkillContextRetryEnabled(),
                    settings.jsonSkillContextRetryMaxChars(),
                    settings.jsonRepairRetryEnabled());
            OpsLlmJsonCallOrchestrator.Result result = eagerSkillContext
                    ? jsonCallOrchestrator.executeWithEagerSkillContext(input, this::callContent)
                    : jsonCallOrchestrator.execute(input, this::callContent);
            return result.success()
                    ? result.json()
                    : degrade(agentName, result.reason(), result.exception());
        } catch (OpsLlmSkillContextException error) {
            throw error;
        } catch (OpsLlmDegradationException error) {
            throw error;
        } catch (OpsRuntimePersistenceException error) {
            throw error;
        } catch (Exception error) {
            return degrade(agentName, "调用失败：" + error.getMessage(), error);
        }
    }

    private String callContent(
            String agentName,
            ChatModel chatModel,
            String systemPrompt,
            String userPrompt,
            Collection<String> skillNames,
            boolean enableSkillTool) {
        return contentCallService.call(new OpsLlmContentCallService.Input(
                agentName,
                chatModel,
                systemPrompt,
                userPrompt,
                skillNames,
                enableSkillTool,
                settings.jsonResponseFormatEnabled(),
                settings.jsonMaxCompletionTokens(),
                settings.modelCallTimeoutSeconds()));
    }

    private JSONObject degrade(String agentName, String reason, Exception error) {
        runtimePolicy.degrade(settings.policySettings(), agentName, reason, error);
        return null;
    }
}
