package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.config.AiClientModelCatalogPort;
import cn.lgs.orbisops.application.config.McpClientCatalogPort;
import cn.lgs.orbisops.application.rag.RagOrderCatalogPort;
import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.application.project.ProjectKnowledgeAuthorizationApplicationService;
import cn.lgs.orbisops.application.project.ProjectMcpAuthorizationApplicationService;
import cn.lgs.orbisops.application.project.ProjectMcpRuntimeDescriptorApplicationService;
import cn.lgs.orbisops.application.project.ProjectSkillAuthorizationApplicationService;
import cn.lgs.orbisops.application.skill.SkillCatalogPort;
import cn.lgs.orbisops.trigger.ops.source.OpsSourceRepositoryService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsAgentDefinitionReferenceConfiguration {

    @Bean
    public OpsAgentProjectCapabilityReferenceValidator
    opsAgentProjectCapabilityReferenceValidator(
            ObjectProvider<McpClientCatalogPort> mcpRepositoryProvider,
            SkillCatalogPort skillCatalog,
            ObjectProvider<ProjectDefinitionApplicationService> projectDefinitionProvider,
            ObjectProvider<ProjectMcpAuthorizationApplicationService> mcpAuthorizationProvider,
            ObjectProvider<ProjectSkillAuthorizationApplicationService> skillAuthorizationProvider,
            ObjectProvider<ProjectMcpRuntimeDescriptorApplicationService> mcpDescriptorProvider,
            ObjectProvider<OpsSourceRepositoryService> sourceRepositoryProvider) {
        return new OpsAgentProjectCapabilityReferenceValidator(
                mcpRepositoryProvider::getIfAvailable,
                () -> skillCatalog,
                projectDefinitionProvider::getIfAvailable,
                mcpAuthorizationProvider::getIfAvailable,
                skillAuthorizationProvider::getIfAvailable,
                mcpDescriptorProvider::getIfAvailable,
                sourceRepositoryProvider::getIfAvailable);
    }

    @Bean
    public OpsAgentModelKnowledgeReferenceValidator
    opsAgentModelKnowledgeReferenceValidator(
            ObjectProvider<AiClientModelCatalogPort> modelRepositoryProvider,
            ObjectProvider<RagOrderCatalogPort> ragRepositoryProvider,
            ObjectProvider<ProjectKnowledgeAuthorizationApplicationService>
                    knowledgeAuthorizationProvider) {
        return new OpsAgentModelKnowledgeReferenceValidator(
                modelRepositoryProvider::getIfAvailable,
                ragRepositoryProvider::getIfAvailable,
                knowledgeAuthorizationProvider::getIfAvailable);
    }
}
