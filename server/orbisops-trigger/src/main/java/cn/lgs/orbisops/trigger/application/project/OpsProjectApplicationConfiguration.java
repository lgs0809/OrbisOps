package cn.lgs.orbisops.trigger.application.project;

import cn.lgs.orbisops.application.knowledge.KnowledgeAuthorizationQueryPort;
import cn.lgs.orbisops.application.knowledge.KnowledgeWorkspaceCatalogPort;
import cn.lgs.orbisops.application.project.AuthorizeProjectAccessUseCase;
import cn.lgs.orbisops.application.project.ManageProjectWorkspaceUseCase;
import cn.lgs.orbisops.application.project.ProjectAccessPort;
import cn.lgs.orbisops.application.project.ProjectAuditPort;
import cn.lgs.orbisops.application.project.ProjectDefaultAgentPort;
import cn.lgs.orbisops.application.project.ProjectDefaultAgentPublicationPort;
import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.application.project.ProjectDefinitionSnapshotPort;
import cn.lgs.orbisops.application.project.ProjectEmergencyStopAcceptancePort;
import cn.lgs.orbisops.application.project.ProjectExternalMcpApplicationService;
import cn.lgs.orbisops.application.project.ProjectExternalMcpCredentialReferencePort;
import cn.lgs.orbisops.application.project.ProjectFirstValueEvidencePort;
import cn.lgs.orbisops.application.project.ProjectKnowledgeAuthorizationApplicationService;
import cn.lgs.orbisops.application.project.ProjectKnowledgeBaseValidationPort;
import cn.lgs.orbisops.application.project.ProjectMcpAuthorizationApplicationService;
import cn.lgs.orbisops.application.project.ProjectMcpCatalogApplicationService;
import cn.lgs.orbisops.application.project.ProjectMcpGenerationApplicationService;
import cn.lgs.orbisops.application.project.ProjectMcpGenerationPreparationPort;
import cn.lgs.orbisops.application.project.ProjectMcpManagementApplicationService;
import cn.lgs.orbisops.application.project.ProjectMcpProjectionApplicationService;
import cn.lgs.orbisops.application.project.ProjectMcpReviewedPolicyPort;
import cn.lgs.orbisops.application.project.ProjectProductReadinessApplicationService;
import cn.lgs.orbisops.application.project.ProjectMcpRuntimeDescriptorApplicationService;
import cn.lgs.orbisops.application.project.ProjectMcpSnapshotPort;
import cn.lgs.orbisops.application.project.ProjectMcpTemplateGenerationApplicationService;
import cn.lgs.orbisops.application.project.ProjectMcpTemplateGenerationPreparationPort;
import cn.lgs.orbisops.application.project.ProjectMcpUpdatePreparationPort;
import cn.lgs.orbisops.application.project.ProjectMemberApplicationService;
import cn.lgs.orbisops.application.project.ProjectResourceApplicationService;
import cn.lgs.orbisops.application.project.ProjectResourceCredentialResolutionPort;
import cn.lgs.orbisops.application.project.ProjectResourcePreparationPort;
import cn.lgs.orbisops.application.project.ProjectResourceSnapshotPort;
import cn.lgs.orbisops.application.project.ProjectSkillAuthorizationApplicationService;
import cn.lgs.orbisops.application.project.ProjectWorkspaceProjectionApplicationService;
import cn.lgs.orbisops.application.project.ProjectWorkspaceProjectionPort;
import cn.lgs.orbisops.application.project.ProjectWorkspaceQueryPort;
import cn.lgs.orbisops.application.project.ProjectWorkspaceReadinessFailurePort;
import cn.lgs.orbisops.application.project.ProjectWorkspaceRuntimeDirectoryApplicationService;
import cn.lgs.orbisops.application.project.ProjectWorkspaceRuntimeDirectoryFailurePort;
import cn.lgs.orbisops.application.project.QueryProjectWorkspaceUseCase;
import cn.lgs.orbisops.application.skill.SkillAuthorizationCatalogPort;
import cn.lgs.orbisops.domain.analysis.adapter.repository.IAnalysisRunRepository;
import cn.lgs.orbisops.domain.audit.adapter.repository.IConfigAuditRepository;
import cn.lgs.orbisops.domain.project.adapter.repository.IProjectDefinitionRepository;
import cn.lgs.orbisops.trigger.application.analysis.OpsAnalysisRunPersistenceMapper;
import cn.lgs.orbisops.trigger.application.ops.OpsConfiguredChatModelAvailabilityService;
import cn.lgs.orbisops.trigger.ops.OpsStructuredReportService;
import cn.lgs.orbisops.domain.project.adapter.repository.IProjectMcpRepository;
import cn.lgs.orbisops.domain.project.adapter.repository.IProjectMemberRepository;
import cn.lgs.orbisops.domain.project.adapter.repository.IProjectResourceRepository;
import cn.lgs.orbisops.domain.project.adapter.repository.IProjectWorkspaceReadinessRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsProjectApplicationConfiguration {

    @Bean
    public ProjectDefinitionApplicationService projectDefinitionApplicationService(
            IProjectDefinitionRepository repository,
            ProjectKnowledgeBaseValidationPort knowledgeBaseValidationPort) {
        return new ProjectDefinitionApplicationService(
                repository, knowledgeBaseValidationPort);
    }

    @Bean
    public ProjectResourceApplicationService projectResourceApplicationService(
            IProjectResourceRepository repository,
            ProjectDefinitionApplicationService definitionService,
            ProjectResourcePreparationPort preparationPort) {
        return new ProjectResourceApplicationService(
                repository, definitionService, preparationPort);
    }

    @Bean
    public ProjectMcpCatalogApplicationService projectMcpCatalogApplicationService(
            IProjectMcpRepository repository) {
        return new ProjectMcpCatalogApplicationService(repository);
    }

    @Bean
    public ProjectKnowledgeAuthorizationApplicationService projectKnowledgeAuthorizationApplicationService(
            ProjectDefinitionApplicationService definitionService,
            KnowledgeAuthorizationQueryPort authorizationService,
            KnowledgeWorkspaceCatalogPort catalogPort) {
        return new ProjectKnowledgeAuthorizationApplicationService(
                definitionService, authorizationService, catalogPort);
    }

    @Bean
    public ProjectSkillAuthorizationApplicationService projectSkillAuthorizationApplicationService(
            ProjectDefinitionApplicationService definitionService,
            SkillAuthorizationCatalogPort skillCatalogService) {
        return new ProjectSkillAuthorizationApplicationService(
                definitionService, skillCatalogService);
    }

    @Bean
    public ProjectProductReadinessApplicationService projectProductReadinessApplicationService(
            IConfigAuditRepository audits,
            OpsConfiguredChatModelAvailabilityService chatModelAvailability) {
        ProjectEmergencyStopAcceptancePort emergencyStopAcceptance =
                new OpsProjectEmergencyStopAcceptanceAdapter(audits);
        return new ProjectProductReadinessApplicationService(
                emergencyStopAcceptance,
                chatModelAvailability::anyAvailable);
    }

    @Bean
    public ProjectFirstValueEvidencePort projectFirstValueEvidencePort(
            IAnalysisRunRepository runs,
            OpsAnalysisRunPersistenceMapper persistenceMapper,
            OpsStructuredReportService reports) {
        return new OpsProjectFirstValueEvidenceAdapter(runs, persistenceMapper, reports);
    }

    @Bean
    public ProjectWorkspaceProjectionApplicationService projectWorkspaceProjectionApplicationService(
            IProjectWorkspaceReadinessRepository readinessRepository,
            ProjectDefaultAgentPublicationPort defaultAgentPublicationPort,
            ProjectWorkspaceReadinessFailurePort failurePort,
            ProjectFirstValueEvidencePort firstValueEvidencePort) {
        return new ProjectWorkspaceProjectionApplicationService(
                readinessRepository, defaultAgentPublicationPort, failurePort, firstValueEvidencePort);
    }

    @Bean
    public ProjectWorkspaceRuntimeDirectoryApplicationService projectWorkspaceRuntimeDirectoryApplicationService(
            ProjectDefinitionSnapshotPort definitionSnapshotPort,
            ProjectResourceSnapshotPort resourceSnapshotPort,
            ProjectMcpSnapshotPort mcpSnapshotPort,
            ProjectResourceCredentialResolutionPort credentialResolutionPort,
            ProjectWorkspaceRuntimeDirectoryFailurePort failurePort) {
        return new ProjectWorkspaceRuntimeDirectoryApplicationService(
                definitionSnapshotPort,
                resourceSnapshotPort,
                mcpSnapshotPort,
                credentialResolutionPort,
                failurePort);
    }

    @Bean
    public ProjectMcpRuntimeDescriptorApplicationService projectMcpRuntimeDescriptorApplicationService(
            ProjectMcpCatalogApplicationService catalogService,
            ProjectResourceApplicationService resourceService) {
        return new ProjectMcpRuntimeDescriptorApplicationService(
                catalogService, resourceService);
    }

    @Bean
    public ProjectMcpAuthorizationApplicationService projectMcpAuthorizationApplicationService(
            ProjectMcpCatalogApplicationService catalogService,
            ProjectDefinitionApplicationService definitionService) {
        return new ProjectMcpAuthorizationApplicationService(
                catalogService, definitionService);
    }

    @Bean
    public ProjectExternalMcpApplicationService projectExternalMcpApplicationService(
            ProjectDefinitionApplicationService definitionService,
            ProjectMcpCatalogApplicationService catalogService,
            ProjectExternalMcpCredentialReferencePort credentialReferencePort,
            ProjectWorkspaceProjectionPort workspaceProjection) {
        return new ProjectExternalMcpApplicationService(
                definitionService, catalogService, credentialReferencePort, workspaceProjection);
    }

    @Bean
    public ProjectMcpGenerationApplicationService projectMcpGenerationApplicationService(
            ProjectResourceApplicationService resourceService,
            ProjectMcpCatalogApplicationService catalogService,
            ProjectMcpGenerationPreparationPort preparationPort) {
        return new ProjectMcpGenerationApplicationService(
                resourceService, catalogService, preparationPort);
    }

    @Bean
    public ProjectMcpManagementApplicationService projectMcpManagementApplicationService(
            ProjectMcpCatalogApplicationService catalogService,
            ProjectWorkspaceProjectionPort workspaceProjection,
            ProjectMcpUpdatePreparationPort preparationPort,
            ProjectMcpReviewedPolicyPort runtimeQueries) {
        return new ProjectMcpManagementApplicationService(
                catalogService, workspaceProjection, preparationPort, runtimeQueries);
    }

    @Bean
    public ProjectMcpProjectionApplicationService projectMcpProjectionApplicationService(
            ProjectMcpCatalogApplicationService catalogService,
            ProjectWorkspaceProjectionPort workspaceProjection) {
        return new ProjectMcpProjectionApplicationService(
                catalogService, workspaceProjection);
    }

    @Bean
    public ProjectMcpTemplateGenerationApplicationService projectMcpTemplateGenerationApplicationService(
            ProjectResourceApplicationService resourceService,
            ProjectMcpCatalogApplicationService catalogService,
            ProjectMcpTemplateGenerationPreparationPort preparationPort) {
        return new ProjectMcpTemplateGenerationApplicationService(
                resourceService, catalogService, preparationPort);
    }

    @Bean
    public ProjectMemberApplicationService projectMemberApplicationService(
            IProjectMemberRepository repository) {
        return new ProjectMemberApplicationService(repository);
    }

    @Bean
    public ManageProjectWorkspaceUseCase manageProjectWorkspaceUseCase(
            ProjectWorkspaceQueryPort workspaceQueries,
            ProjectWorkspaceProjectionPort workspaceProjection,
            ProjectDefaultAgentPort defaultAgentPort,
            ProjectAuditPort auditPort,
            ProjectMemberApplicationService memberService,
            ProjectDefinitionApplicationService definitionService,
            ProjectResourceApplicationService resourceService,
            ProjectMcpGenerationApplicationService mcpGenerationService,
            ProjectMcpManagementApplicationService mcpManagementService,
            ProjectMcpCatalogApplicationService mcpCatalogService) {
        return new ManageProjectWorkspaceUseCase(
                workspaceQueries, workspaceProjection, defaultAgentPort, auditPort, memberService,
                definitionService, resourceService, mcpGenerationService,
                mcpManagementService, mcpCatalogService);
    }

    @Bean
    public QueryProjectWorkspaceUseCase queryProjectWorkspaceUseCase(
            ProjectWorkspaceQueryPort workspaceQueries,
            ProjectMemberApplicationService memberService,
            ProjectMcpCatalogApplicationService mcpCatalogService) {
        return new QueryProjectWorkspaceUseCase(
                workspaceQueries, memberService, mcpCatalogService);
    }

    @Bean
    public AuthorizeProjectAccessUseCase authorizeProjectAccessUseCase(
            ProjectAccessPort accessPort,
            ProjectMemberApplicationService memberService) {
        return new AuthorizeProjectAccessUseCase(accessPort, memberService);
    }
}
