package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.trigger.ops.OpsSchemaGovernanceSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsSchemaGovernanceConfiguration {

    @Bean
    public OpsSchemaGovernanceSettings opsSchemaGovernanceSettings(
            @Value("${orbisops.agents.auto-init:false}") boolean agentDefinitionAutoInit,
            @Value("${orbisops.graph-events.auto-init:false}") boolean graphEventsAutoInit,
            @Value("${orbisops.runs.auto-init:false}") boolean runsAutoInit,
            @Value("${orbisops.audit.auto-init:false}") boolean auditAutoInit,
            @Value("${orbisops.alert-triggers.auto-init:false}") boolean alertTriggerAutoInit,
            @Value("${orbisops.chat.memory.auto-init:false}") boolean chatMemoryAutoInit,
            @Value("${orbisops.rag.ingestion.auto-init:false}") boolean ragIngestionAutoInit) {
        return new OpsSchemaGovernanceSettings(
                agentDefinitionAutoInit,
                graphEventsAutoInit,
                runsAutoInit,
                auditAutoInit,
                alertTriggerAutoInit,
                chatMemoryAutoInit,
                ragIngestionAutoInit);
    }
}
