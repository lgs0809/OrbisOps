package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.domain.runtime.llm.model.LlmDegradationMode;
import cn.lgs.orbisops.domain.runtime.llm.model.LlmRuntimeSettings;
import cn.lgs.orbisops.domain.runtime.llm.service.LlmDegradationPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/** Trigger adapter for LLM degradation exceptions, logging and status projection. */
final class OpsLlmRuntimePolicy {

    private static final Logger log =
            LoggerFactory.getLogger(OpsLlmRuntimePolicy.class);

    private final LlmDegradationPolicy domainPolicy =
            new LlmDegradationPolicy();

    Map<String, Object> status(
            Map<String, Object> baseStatus,
            LlmRuntimeSettings settings) {
        LlmRuntimeSettings effective = settings == null
                ? new LlmRuntimeSettings(true, true, true, 1_200, true, 16_000, 240)
                : settings;
        Map<String, Object> data = baseStatus == null
                ? new LinkedHashMap<>()
                : baseStatus;
        data.put("failOnLlmDegradation", effective.failOnLlmDegradation());
        data.put("jsonRepairRetryEnabled", effective.jsonRepairRetryEnabled());
        data.put("jsonResponseFormatEnabled", effective.jsonResponseFormatEnabled());
        data.put("jsonMaxCompletionTokens", effective.jsonMaxCompletionTokens());
        data.put("jsonSkillContextRetryEnabled",
                effective.jsonSkillContextRetryEnabled());
        data.put("jsonSkillContextRetryMaxChars",
                effective.jsonSkillContextRetryMaxChars());
        data.put("modelCallTimeoutSeconds", effective.modelCallTimeoutSeconds());
        data.put("degradationMode", domainPolicy.decide(effective).name());
        return data;
    }

    void reject(
            LlmRuntimeSettings settings,
            String agentName,
            String reason) {
        if (domainPolicy.decide(settings) == LlmDegradationMode.FAIL_CLOSED) {
            throw new OpsLlmDegradationException(agentName, reason);
        }
        log.warn("{} LLM 降级到规则逻辑：{}", agentName, reason);
    }

    void degrade(
            LlmRuntimeSettings settings,
            String agentName,
            String reason,
            Exception error) {
        if (domainPolicy.decide(settings) == LlmDegradationMode.FAIL_CLOSED) {
            throw new OpsLlmDegradationException(
                    agentName,
                    reason,
                    error);
        }
        log.warn("{} LLM 调用失败，降级到规则逻辑：{}", agentName, reason);
    }
}
