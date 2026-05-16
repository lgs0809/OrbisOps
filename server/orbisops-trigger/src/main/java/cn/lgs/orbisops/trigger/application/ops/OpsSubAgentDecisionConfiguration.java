package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.trigger.ops.OpsSubAgentDecisionSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsSubAgentDecisionConfiguration {

    @Bean
    public OpsSubAgentDecisionSettings opsSubAgentDecisionSettings(
            @Value("${orbisops.multi-agent.sub-agent-llm-enabled:true}") boolean decisionLlmEnabled,
            @Value("${orbisops.multi-agent.reflection-llm-enabled:true}") boolean reflectionLlmEnabled) {
        return new OpsSubAgentDecisionSettings(decisionLlmEnabled, reflectionLlmEnabled);
    }
}
