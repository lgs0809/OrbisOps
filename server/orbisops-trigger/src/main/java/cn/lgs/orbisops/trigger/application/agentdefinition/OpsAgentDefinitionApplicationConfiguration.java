package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionLifecyclePort;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionLifecycleUseCase;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionReleaseGatePort;
import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionValidationPort;
import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.domain.agentdefinition.adapter.repository.IAgentCapabilityBindingRepository;
import cn.lgs.orbisops.domain.agentdefinition.adapter.repository.IAgentDefinitionGraphRepository;
import cn.lgs.orbisops.domain.agentdefinition.adapter.repository.IAgentDefinitionRepository;
import cn.lgs.orbisops.trigger.application.agenteval.OpsAgentEvalAdapter;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinitionValidator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsAgentDefinitionApplicationConfiguration {

    @Bean
    public AgentDefinitionLifecycleUseCase<OpsAgentDefinition> agentDefinitionLifecycleUseCase(
            AgentDefinitionLifecyclePort<OpsAgentDefinition> lifecyclePort,
            AgentDefinitionValidationPort<OpsAgentDefinition> validationPort,
            AgentDefinitionReleaseGatePort<OpsAgentDefinition> releaseGatePort) {
        return new AgentDefinitionLifecycleUseCase<>(lifecyclePort, validationPort, releaseGatePort);
    }

    @Bean
    public OpsAgentDefinitionApplicationAssembly opsAgentDefinitionApplicationAssembly(
            OpsAgentDefinitionValidator definitionValidator,
            ObjectProvider<IAgentDefinitionRepository> definitionRepositoryProvider,
            ObjectProvider<IAgentDefinitionGraphRepository> graphRepositoryProvider,
            ObjectProvider<IAgentCapabilityBindingRepository> capabilityRepositoryProvider,
            OpsAgentDefinitionYamlLoader yamlDefinitionLoader,
            OpsAgentDefinitionSnapshotMapper snapshotMapper,
            ObjectProvider<ProjectDefinitionApplicationService> projectDefinitionServiceProvider,
            OpsAgentDefinitionRuntimeSettings runtimeSettings) {
        return OpsAgentDefinitionApplicationAssembly.create(
                definitionValidator,
                definitionRepositoryProvider.getIfAvailable(),
                graphRepositoryProvider.getIfAvailable(),
                capabilityRepositoryProvider.getIfAvailable(),
                yamlDefinitionLoader,
                snapshotMapper,
                projectDefinitionServiceProvider::getIfAvailable,
                runtimeSettings::effectiveDefaultAgentId);
    }

    @Bean
    public OpsAgentDefinitionManagementAssembly opsAgentDefinitionManagementAssembly(
            OpsAgentDefinitionGateway definitionGateway,
            AgentDefinitionLifecycleUseCase<OpsAgentDefinition> lifecycleUseCase,
            OpsConfigAuditService auditService,
            OpsAgentCapabilityApplicationService capabilityService,
            OpsAgentEvalAdapter evalAdapter) {
        return OpsAgentDefinitionManagementAssembly.create(
                definitionGateway,
                lifecycleUseCase,
                auditService,
                capabilityService,
                evalAdapter);
    }
}
