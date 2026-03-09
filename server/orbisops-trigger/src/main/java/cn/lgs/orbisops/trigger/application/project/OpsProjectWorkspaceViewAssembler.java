package cn.lgs.orbisops.trigger.application.project;

import cn.lgs.orbisops.application.capability.CapabilityReadinessUseCase;
import cn.lgs.orbisops.application.project.ProjectDefinitionApplicationService;
import cn.lgs.orbisops.application.project.ProjectKnowledgeAuthorizationApplicationService;
import cn.lgs.orbisops.application.project.ProjectProductReadinessApplicationService;
import cn.lgs.orbisops.application.project.ProjectProductReadinessProjection;
import cn.lgs.orbisops.application.project.ProjectSkillAuthorizationApplicationService;
import cn.lgs.orbisops.application.project.ProjectWorkspaceProjection;
import cn.lgs.orbisops.application.project.ProjectWorkspaceProjectionApplicationService;
import cn.lgs.orbisops.application.project.ProjectWorkspaceProjectionRequest;
import cn.lgs.orbisops.application.project.ProjectWorkspaceRuntimeDirectoryApplicationService;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeBaseCatalogEntry;
import cn.lgs.orbisops.domain.project.model.ProjectDefinition;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/** Runtime workspace state to detail/catalog response projection boundary. */
public final class OpsProjectWorkspaceViewAssembler {

    private final ProjectWorkspaceRuntimeDirectoryApplicationService runtimeDirectory;
    private final OpsProjectWorkspaceMaterializationMapper materializationMapper;
    private final OpsProjectWorkspaceCompatibilityPayloadStore compatibilityPayloadStore;
    private final ProjectWorkspaceProjectionApplicationService projectionService;
    private final OpsProjectWorkspaceProjectionMapper projectionMapper;
    private final ProjectSkillAuthorizationApplicationService skillAuthorizationService;
    private final ProjectKnowledgeAuthorizationApplicationService knowledgeAuthorizationService;
    private final ProjectDefinitionApplicationService projectDefinitionService;
    private final ProjectProductReadinessApplicationService productReadinessService;
    private final CapabilityReadinessUseCase capabilityReadinessUseCase;

    public OpsProjectWorkspaceViewAssembler(
            ProjectWorkspaceRuntimeDirectoryApplicationService runtimeDirectory,
            OpsProjectWorkspaceMaterializationMapper materializationMapper,
            OpsProjectWorkspaceCompatibilityPayloadStore compatibilityPayloadStore,
            ProjectWorkspaceProjectionApplicationService projectionService,
            OpsProjectWorkspaceProjectionMapper projectionMapper,
            ProjectSkillAuthorizationApplicationService skillAuthorizationService,
            ProjectKnowledgeAuthorizationApplicationService knowledgeAuthorizationService,
            ProjectDefinitionApplicationService projectDefinitionService) {
        this(runtimeDirectory, materializationMapper, compatibilityPayloadStore, projectionService,
                projectionMapper, skillAuthorizationService, knowledgeAuthorizationService,
                projectDefinitionService, null, null);
    }

    public OpsProjectWorkspaceViewAssembler(
            ProjectWorkspaceRuntimeDirectoryApplicationService runtimeDirectory,
            OpsProjectWorkspaceMaterializationMapper materializationMapper,
            OpsProjectWorkspaceCompatibilityPayloadStore compatibilityPayloadStore,
            ProjectWorkspaceProjectionApplicationService projectionService,
            OpsProjectWorkspaceProjectionMapper projectionMapper,
            ProjectSkillAuthorizationApplicationService skillAuthorizationService,
            ProjectKnowledgeAuthorizationApplicationService knowledgeAuthorizationService,
            ProjectDefinitionApplicationService projectDefinitionService,
            ProjectProductReadinessApplicationService productReadinessService,
            CapabilityReadinessUseCase capabilityReadinessUseCase) {
        this.runtimeDirectory = runtimeDirectory;
        this.materializationMapper = materializationMapper;
        this.compatibilityPayloadStore = compatibilityPayloadStore;
        this.projectionService = projectionService;
        this.projectionMapper = projectionMapper;
        this.skillAuthorizationService = skillAuthorizationService;
        this.knowledgeAuthorizationService = knowledgeAuthorizationService;
        this.projectDefinitionService = projectDefinitionService;
        this.productReadinessService = productReadinessService;
        this.capabilityReadinessUseCase = capabilityReadinessUseCase;
    }

    public Map<String, Object> detail(ProjectDefinition project) {
        ProjectionContext context = context(project);
        Map<String, Object> view = projectionMapper.detail(
                context.projection(),
                context.projectPayload(),
                context.resources(),
                context.mcps());
        if (productReadinessService != null && capabilityReadinessUseCase != null) {
            ProjectProductReadinessProjection readiness = productReadinessService.project(
                    context.projection(), capabilityReadinessUseCase.snapshot());
            view.put("diagnosisReadiness", projectionMapper.readiness(readiness.diagnosis()));
            view.put("remediationReadiness", projectionMapper.readiness(readiness.remediation()));
        }
        return view;
    }

    public Map<String, Object> catalog(ProjectDefinition project) {
        return projectionMapper.catalog(context(project).projection());
    }

    private ProjectionContext context(ProjectDefinition project) {
        String projectId = project.projectId();
        Map<String, Object> projectPayload = materializationMapper.projectView(
                project,
                compatibilityPayloadStore.project(projectId).orElse(Map.of()));
        List<Map<String, Object>> resources = runtimeDirectory.resources(projectId).stream()
                .map(resource -> materializationMapper.resourcePayload(
                        resource,
                        compatibilityPayloadStore
                                .resource(projectId, resource.resourceId())
                                .orElse(Map.of())))
                .toList();
        List<Map<String, Object>> mcps = runtimeDirectory.mcps(projectId).stream()
                .map(mcp -> materializationMapper.mcpPayload(
                        mcp,
                        compatibilityPayloadStore
                                .mcp(projectId, mcp.mcpId())
                                .orElse(Map.of())))
                .toList();
        ProjectWorkspaceProjectionRequest request = projectionMapper.request(
                projectPayload,
                resources,
                mcps,
                authorizedLocalSkillIds(projectId),
                authorizedGlobalSkillIds(projectId),
                authorizedKnowledgeBaseIds(projectId),
                projectKnowledgeBases(projectId),
                enabledGlobalKnowledgeBases(projectId));
        ProjectWorkspaceProjection projection = projectionService.project(request);
        return new ProjectionContext(
                projectPayload,
                resources,
                mcps,
                projection);
    }

    private List<String> authorizedLocalSkillIds(String projectId) {
        if (skillAuthorizationService != null) {
            return skillAuthorizationService.localIds(projectId);
        }
        return runtimeDirectory.project(projectId)
                .map(ProjectDefinition::skillIds)
                .orElseGet(List::of);
    }

    private List<String> authorizedGlobalSkillIds(String projectId) {
        return skillAuthorizationService == null
                ? List.of()
                : skillAuthorizationService.globalIds(projectId);
    }

    private List<String> authorizedKnowledgeBaseIds(String projectId) {
        if (knowledgeAuthorizationService != null) {
            return knowledgeAuthorizationService.enabledIds(projectId);
        }
        String legacy = projectDefinitionService != null
                ? projectDefinitionService.defaultKnowledgeBaseId(projectId)
                : runtimeDirectory.project(projectId)
                        .map(ProjectDefinition::knowledgeBaseId)
                        .orElse("");
        return StringUtils.hasText(legacy)
                ? List.of(legacy)
                : List.of();
    }

    private List<KnowledgeBaseCatalogEntry> projectKnowledgeBases(String projectId) {
        return knowledgeAuthorizationService == null
                ? List.of()
                : knowledgeAuthorizationService.projectEntries(projectId);
    }

    private List<KnowledgeBaseCatalogEntry> enabledGlobalKnowledgeBases(
            String projectId) {
        return knowledgeAuthorizationService == null
                ? List.of()
                : knowledgeAuthorizationService.globalEntries(projectId);
    }

    private record ProjectionContext(
            Map<String, Object> projectPayload,
            List<Map<String, Object>> resources,
            List<Map<String, Object>> mcps,
            ProjectWorkspaceProjection projection) {
    }
}
