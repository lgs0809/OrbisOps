package cn.lgs.orbisops.trigger.ops.capability;

import cn.lgs.orbisops.application.skill.SkillCatalogQueryService;
import cn.lgs.orbisops.application.skill.SkillManagementUseCase;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;

import java.util.LinkedHashMap;
import java.util.Map;

/** Skill package materialization, idempotent persistence, audit, and result projection boundary. */
final class OpsSkillCapabilityImportCoordinator {

    private final SkillManagementUseCase skillManagementUseCase;
    private final SkillCatalogQueryService skillCatalogQueryService;
    private final OpsSkillPackageMaterializer materializer;
    private final OpsConfigAuditService auditService;
    private final OpsCapabilityImportSettings settings;

    OpsSkillCapabilityImportCoordinator(
            SkillManagementUseCase skillManagementUseCase,
            SkillCatalogQueryService skillCatalogQueryService,
            OpsSkillPackageMaterializer materializer,
            OpsConfigAuditService auditService,
            OpsCapabilityImportSettings settings) {
        this.skillManagementUseCase = skillManagementUseCase;
        this.skillCatalogQueryService = skillCatalogQueryService;
        this.materializer = materializer;
        this.auditService = auditService;
        this.settings = settings == null
                ? OpsCapabilityImportSettings.defaults()
                : settings;
    }

    Map<String, Object> importCapability(
            String projectId,
            String sourceUrl,
            Map<String, Object> slots,
            String actor) {
        OpsSkillPackageMaterializer.PreparedSkill prepared;
        try {
            prepared = materializer.prepare(new OpsSkillPackageMaterializer.Input(
                    sourceUrl,
                    OpsCapabilityImportValues.text(slots.get("capabilityName")),
                    slots,
                    settings.skillPackageSettings()));
        } catch (OpsSkillImportRoutingResolver.RoutingMetadataRequired missing) {
            Map<String, Object> draft = new LinkedHashMap<>();
            draft.put("sourceUrl", sourceUrl);
            draft.put("capabilityName", missing.detectedName());
            draft.put("description", missing.detectedDescription());
            return Map.of(
                    "capabilityType", "SKILL",
                    "status", "INPUT_REQUIRED",
                    "requiredFields", missing.requiredFields(),
                    "draft", draft,
                    "message", missing.question());
        }

        String skillId = prepared.skillId();
        String sourceHash = prepared.sourceHash();
        Map<String, Object> skill;
        boolean idempotent = false;
        try {
            skill = skillManagementUseCase.createProjectSkill(
                    projectId,
                    prepared.createRequest(),
                    actor);
        } catch (IllegalArgumentException error) {
            if (error.getMessage() == null || !error.getMessage().contains("已存在")) {
                throw error;
            }
            skill = skillCatalogQueryService.getProjectSkill(projectId, skillId);
            idempotent = true;
        }
        auditService.recordRuntimeEvent(
                projectId,
                "",
                actor,
                "capability-import",
                "SKILL_IMPORTED",
                skillId,
                "MEDIUM",
                "SUCCEEDED",
                Map.of(
                        "projectId", projectId,
                        "skillId", skillId,
                        "sourceUrl", sourceUrl,
                        "sourceHash", sourceHash,
                        "status", "PAUSED",
                        "updateMode", "MANUAL_ONLY",
                        "idempotent", idempotent));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("capabilityType", "SKILL");
        result.put("status", "IMPORTED_PAUSED");
        result.put(
                "message",
                "Skill 包已完整导入但尚未启用。请先查看正文、资源、脚本和评测用例，再显式启用。");
        result.put("skill", skill);
        result.put("idempotent", idempotent);
        return result;
    }
}
