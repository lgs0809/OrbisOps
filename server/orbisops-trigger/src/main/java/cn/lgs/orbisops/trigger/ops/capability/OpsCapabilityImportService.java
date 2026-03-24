package cn.lgs.orbisops.trigger.ops.capability;

import cn.lgs.orbisops.application.mcp.ProgressiveMcpProcessManager;
import cn.lgs.orbisops.application.project.AuthorizeProjectAccessUseCase;
import cn.lgs.orbisops.application.project.ProjectExternalMcpApplicationService;
import cn.lgs.orbisops.application.skill.SkillCatalogQueryService;
import cn.lgs.orbisops.application.skill.SkillManagementUseCase;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMcpToolProvider;
import cn.lgs.orbisops.trigger.ops.runtime.OpsProjectMcpRuntimeConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/** Fixed control-plane import facade. Imported capabilities remain unavailable until reviewed. */
@Service
public class OpsCapabilityImportService {

    private final OpsCapabilityImportAccessPolicy accessPolicy;
    private final OpsSkillCapabilityImportCoordinator skillCoordinator;
    private final OpsMcpCapabilityImportCoordinator mcpCoordinator;

    public OpsCapabilityImportService(
            SkillManagementUseCase skillManagementUseCase,
            SkillCatalogQueryService skillCatalogQueryService,
            AuthorizeProjectAccessUseCase projectAccessUseCase,
            OpsCapabilityArtifactFetcher artifactFetcher,
            OpsCapabilityImportUrlPolicy urlPolicy,
            OpsConfigAuditService auditService) {
        this(
                skillManagementUseCase,
                skillCatalogQueryService,
                projectAccessUseCase,
                artifactFetcher,
                urlPolicy,
                auditService,
                null,
                null,
                null,
                null,
                OpsCapabilityImportSettings.legacyConstructorDefaults());
    }

    public OpsCapabilityImportService(
            SkillManagementUseCase skillManagementUseCase,
            SkillCatalogQueryService skillCatalogQueryService,
            AuthorizeProjectAccessUseCase projectAccessUseCase,
            OpsCapabilityArtifactFetcher artifactFetcher,
            OpsCapabilityImportUrlPolicy urlPolicy,
            OpsConfigAuditService auditService,
            ProjectExternalMcpApplicationService externalMcpService,
            OpsProjectMcpRuntimeConfigService projectMcpRuntimeConfigService,
            OpsMcpToolProvider mcpToolProvider,
            ProgressiveMcpProcessManager progressiveMcpProcessManager) {
        this(
                skillManagementUseCase,
                skillCatalogQueryService,
                projectAccessUseCase,
                artifactFetcher,
                urlPolicy,
                auditService,
                externalMcpService,
                projectMcpRuntimeConfigService,
                mcpToolProvider,
                progressiveMcpProcessManager,
                OpsCapabilityImportSettings.legacyConstructorDefaults());
    }

    @Autowired
    public OpsCapabilityImportService(
            SkillManagementUseCase skillManagementUseCase,
            SkillCatalogQueryService skillCatalogQueryService,
            AuthorizeProjectAccessUseCase projectAccessUseCase,
            OpsCapabilityArtifactFetcher artifactFetcher,
            OpsCapabilityImportUrlPolicy urlPolicy,
            OpsConfigAuditService auditService,
            ProjectExternalMcpApplicationService externalMcpService,
            OpsProjectMcpRuntimeConfigService projectMcpRuntimeConfigService,
            OpsMcpToolProvider mcpToolProvider,
            ProgressiveMcpProcessManager progressiveMcpProcessManager,
            OpsCapabilityImportSettings settings) {
        OpsCapabilityImportSettings resolved = settings == null
                ? OpsCapabilityImportSettings.defaults()
                : settings;
        this.accessPolicy = new OpsCapabilityImportAccessPolicy(projectAccessUseCase);
        this.skillCoordinator = new OpsSkillCapabilityImportCoordinator(
                skillManagementUseCase,
                skillCatalogQueryService,
                new OpsSkillPackageMaterializer(artifactFetcher, urlPolicy),
                auditService,
                resolved);
        this.mcpCoordinator = new OpsMcpCapabilityImportCoordinator(
                new OpsMcpCapabilityImporter(
                        urlPolicy,
                        externalMcpService,
                        projectMcpRuntimeConfigService,
                        mcpToolProvider,
                        progressiveMcpProcessManager),
                auditService);
    }

    OpsCapabilityImportService(
            OpsCapabilityImportAccessPolicy accessPolicy,
            OpsSkillCapabilityImportCoordinator skillCoordinator,
            OpsMcpCapabilityImportCoordinator mcpCoordinator) {
        this.accessPolicy = accessPolicy;
        this.skillCoordinator = skillCoordinator;
        this.mcpCoordinator = mcpCoordinator;
    }

    public Map<String, Object> importSkill(
            String projectId,
            String sourceUrl,
            Map<String, Object> options,
            String username,
            String userId,
            boolean platformAdmin) {
        return importTyped(false, projectId, sourceUrl, options, username, userId, platformAdmin);
    }

    public Map<String, Object> importMcp(
            String projectId,
            String sourceUrl,
            Map<String, Object> options,
            String username,
            String userId,
            boolean platformAdmin) {
        return importTyped(true, projectId, sourceUrl, options, username, userId, platformAdmin);
    }

    private Map<String, Object> importTyped(
            boolean mcp,
            String projectId,
            String sourceUrl,
            Map<String, Object> options,
            String username,
            String userId,
            boolean platformAdmin) {
        String normalizedProjectId = accessPolicy.requireProjectId(projectId);
        accessPolicy.assertCanManage(
                normalizedProjectId,
                username,
                userId,
                platformAdmin);
        return importAuthorized(
                mcp,
                normalizedProjectId,
                sourceUrl,
                options == null ? Map.of() : Map.copyOf(options),
                accessPolicy.actor(username, userId));
    }

    private Map<String, Object> importAuthorized(
            boolean mcp,
            String projectId,
            String sourceUrl,
            Map<String, Object> options,
            String actor) {
        if (!StringUtils.hasText(sourceUrl)) {
            return Map.of(
                    "status", "INPUT_REQUIRED",
                    "requiredFields", List.of("sourceUrl"),
                    "message", "请提供要导入的 HTTPS 地址；内部 ID、版本和 hash 由系统生成。");
        }
        return mcp
                ? mcpCoordinator.importCapability(projectId, sourceUrl.trim(), options, actor)
                : skillCoordinator.importCapability(projectId, sourceUrl.trim(), options, actor);
    }
}
