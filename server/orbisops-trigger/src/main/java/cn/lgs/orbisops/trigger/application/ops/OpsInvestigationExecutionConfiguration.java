package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.trigger.ops.OpsInvestigationExecutorSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring configuration boundary for investigation execution settings. */
@Configuration
public class OpsInvestigationExecutionConfiguration {

    @Bean
    public OpsInvestigationExecutorSettings opsInvestigationExecutorSettings(
            @Value("${orbisops.multi-agent.main-reflection-llm-enabled:true}") boolean mainReflectionLlmEnabled,
            @Value("${orbisops.multi-agent.parallel-execution-enabled:true}") boolean parallelExecutionEnabled,
            @Value("${orbisops.multi-agent.max-task-executions:8}") int maxTaskExecutions,
            @Value("${orbisops.multi-agent.max-adjustments:3}") int maxAdjustmentsLimit,
            @Value("${orbisops.multi-agent.max-evidence-items:12}") int defaultMaxEvidenceItems) {
        return new OpsInvestigationExecutorSettings(
                mainReflectionLlmEnabled,
                parallelExecutionEnabled,
                maxTaskExecutions,
                maxAdjustmentsLimit,
                defaultMaxEvidenceItems);
    }
}
