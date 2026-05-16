package cn.lgs.orbisops.trigger.application.migration;

import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationContributor;
import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationOperations;
import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationOrchestrator;
import cn.lgs.orbisops.application.migration.PlatformCapabilityMigrationRunPort;
import cn.lgs.orbisops.trigger.application.agentdefinition.AgentWorkflowDefinitionMigrator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
public class OpsPlatformCapabilityMigrationConfiguration {

    @Bean
    public AgentWorkflowDefinitionMigrator agentWorkflowDefinitionMigrator() {
        return new AgentWorkflowDefinitionMigrator();
    }

    @Bean
    public PlatformCapabilityMigrationOrchestrator platformCapabilityMigrationOrchestrator(
            List<PlatformCapabilityMigrationContributor> contributors) {
        return new PlatformCapabilityMigrationOrchestrator(contributors);
    }

    @Bean
    public PlatformCapabilityMigrationOperations platformCapabilityMigrationOperations(
            PlatformCapabilityMigrationOrchestrator orchestrator,
            PlatformCapabilityMigrationRunPort runs) {
        return new PlatformCapabilityMigrationOperations(
                orchestrator,
                runs,
                Clock.systemUTC(),
                () -> "platform-migration-" + UUID.randomUUID());
    }
}
