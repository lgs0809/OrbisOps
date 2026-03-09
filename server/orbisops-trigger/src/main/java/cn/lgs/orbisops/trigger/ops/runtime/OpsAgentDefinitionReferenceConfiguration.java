package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.application.project.ProjectKnowledgeAuthorizationApplicationService;
import cn.lgs.orbisops.application.project.ProjectMcpAuthorizationApplicationService;
import cn.lgs.orbisops.application.project.ProjectMcpRuntimeDescriptorApplicationService;
import cn.lgs.orbisops.application.project.ProjectSkillAuthorizationApplicationService;
import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientModelConfigRepository;
import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientRagOrderConfigRepository;
import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientToolMcpConfigRepository;
import cn.lgs.orbisops.trigger.ops.skill.SkillRuntimeToolProvider;
import cn.lgs.orbisops.trigger.ops.source.OpsSourceRepositoryService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsAgentDefinitionReferenceConfiguration {

    @Bean
    public OpsAgentProjectCapabilityReferenceValidator
    opsAgentProjectCapabilityReferenceValidator(
            ObjectProvider<IAiClientToolMcpConfigRepository> mcpRepositoryProvider,
            ObjectProvider<SkillRuntimeToolProvider> skillToolProvider,
            ObjectProvider<ProjectDefinitionApplicationService> projectDefinitionProvider,
            ObjectProvider<ProjectMcpAuthorizationApplicationService> mcpAuthorizationProvider,
            ObjectProvider<ProjectSkillAuthorizationApplicationService> skillAuthorizationProvider,
            ObjectProvider<ProjectMcpRuntimeDescriptorApplicationService> mcpDescriptorProvider,
            ObjectProvider<OpsSourceRepositoryService> sourceRepositoryProvider) {
        return new OpsAgentProjectCapabilityReferenceValidator(
                mcpRepositoryProvider::getIfAvailable,
                skillToolProvider::getIfAvailable,
                projectDefinitionProvider::getIfAvailable,
                mcpAuthorizationProvider::getIfAvailable,
                skillAuthorizationProvider::getIfAvailable,
                mcpDescriptorProvider::getIfAvailable,
                sourceRepositoryProvider::getIfAvailable);
    }

    @Bean
    public OpsAgentModelKnowledgeReferenceValidator
    opsAgentModelKnowledgeReferenceValidator(
            ObjectProvider<IAiClientModelConfigRepository> modelRepositoryProvider,
            ObjectProvider<IAiClientRagOrderConfigRepository> ragRepositoryProvider,
            ObjectProvider<ProjectKnowledgeAuthorizationApplicationService>
                    knowledgeAuthorizationProvider) {
        return new OpsAgentModelKnowledgeReferenceValidator(
                modelRepositoryProvider::getIfAvailable,
                ragRepositoryProvider::getIfAvailable,
                knowledgeAuthorizationProvider::getIfAvailable);
    }
}
