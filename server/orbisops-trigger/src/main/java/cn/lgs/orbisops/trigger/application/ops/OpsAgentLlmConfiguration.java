package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.trigger.ops.OpsAgentLlmSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsAgentLlmConfiguration {

    @Bean
    public OpsAgentLlmSettings opsAgentLlmSettings(
            @Value("${ops.multi-agent.enabled:true}") boolean enabled,
            @Value("${ops.multi-agent.max-output-chars:6000}") int maxOutputChars,
            @Value("${ops.multi-agent.model-call-timeout-seconds:240}") int modelCallTimeoutSeconds,
            @Value("${ops.multi-agent.skill-context-enabled:true}") boolean skillContextEnabled,
            @Value("${ops.multi-agent.skill-context-max-chars:12000}") int skillContextMaxChars,
            @Value("${ops.multi-agent.fail-on-llm-degradation:false}") boolean failOnLlmDegradation,
            @Value("${ops.multi-agent.json-repair-retry-enabled:true}") boolean jsonRepairRetryEnabled,
            @Value("${ops.multi-agent.json-response-format-enabled:true}") boolean jsonResponseFormatEnabled,
            @Value("${ops.multi-agent.json-max-completion-tokens:1200}") int jsonMaxCompletionTokens,
            @Value("${ops.multi-agent.json-skill-context-retry-enabled:true}") boolean jsonSkillContextRetryEnabled,
            @Value("${ops.multi-agent.json-skill-context-retry-max-chars:16000}") int jsonSkillContextRetryMaxChars) {
        return new OpsAgentLlmSettings(
                enabled,
                maxOutputChars,
                modelCallTimeoutSeconds,
                skillContextEnabled,
                skillContextMaxChars,
                failOnLlmDegradation,
                jsonRepairRetryEnabled,
                jsonResponseFormatEnabled,
                jsonMaxCompletionTokens,
                jsonSkillContextRetryEnabled,
                jsonSkillContextRetryMaxChars);
    }
}
