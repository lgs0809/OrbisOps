package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.trigger.ops.OpsMainAgentPlannerSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsMainAgentPlannerConfiguration {

    @Bean
    public OpsMainAgentPlannerSettings opsMainAgentPlannerSettings(
            @Value("${ops.multi-agent.planner-llm-enabled:true}") boolean llmEnabled,
            @Value("${ops.multi-agent.planner-mode:smart}") String mode) {
        return new OpsMainAgentPlannerSettings(llmEnabled, mode);
    }
}
