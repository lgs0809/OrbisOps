package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.trigger.ops.OpsAgentLlmSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsAgentLlmConfiguration {

    @Bean
    public OpsAgentLlmSettings opsAgentLlmSettings(
            @Value("${orbisops.multi-agent.enabled:true}") boolean enabled,
            @Value("${orbisops.multi-agent.max-output-chars:6000}") int maxOutputChars,
            @Value("${orbisops.multi-agent.model-call-timeout-seconds:240}") int modelCallTimeoutSeconds,
            @Value("${orbisops.multi-agent.skill-context-enabled:true}") boolean skillContextEnabled,
            @Value("${orbisops.multi-agent.skill-context-max-chars:12000}") int skillContextMaxChars,
            @Value("${orbisops.multi-agent.fail-on-llm-degradation:false}") boolean failOnLlmDegradation,
            @Value("${orbisops.multi-agent.json-repair-retry-enabled:true}") boolean jsonRepairRetryEnabled,
            @Value("${orbisops.multi-agent.json-response-format-enabled:true}") boolean jsonResponseFormatEnabled,
            @Value("${orbisops.multi-agent.json-max-completion-tokens:1200}") int jsonMaxCompletionTokens,
            @Value("${orbisops.multi-agent.json-skill-context-retry-enabled:true}") boolean jsonSkillContextRetryEnabled,
            @Value("${orbisops.multi-agent.json-skill-context-retry-max-chars:16000}") int jsonSkillContextRetryMaxChars) {
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
