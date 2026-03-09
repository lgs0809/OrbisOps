package cn.lgs.orbisops.application.skill;

import java.util.List;
import java.util.Map;

public final class SkillCatalogQueryService implements SkillAuthorizationCatalogPort {

    private final SkillCatalogPort port;
    private final SkillPackageQueryService packageQueryService;

    public SkillCatalogQueryService(SkillCatalogPort port,
                                    SkillPackageQueryService packageQueryService) {
        if (port == null) throw new IllegalArgumentException("SKILL_CATALOG_PORT_REQUIRED");
        if (packageQueryService == null) throw new IllegalArgumentException("SKILL_PACKAGE_QUERY_REQUIRED");
        this.port = port;
        this.packageQueryService = packageQueryService;
    }

    public List<Map<String, Object>> listGlobalSkills() {
        return port.listGlobalEntries().stream().map(SkillCatalogSnapshot::view).toList();
    }

    public Map<String, Object> getGlobalSkill(String skillId) {
        return port.getGlobalEntry(skillId(skillId)).view();
    }

    public List<Map<String, Object>> listProjectSkills(String projectId) {
        return port.listProjectEntries(projectId(projectId)).stream()
                .map(SkillCatalogSnapshot::view)
                .toList();
    }

    public Map<String, Object> getProjectSkill(String projectId, String skillId) {
        return port.getProjectEntry(projectId(projectId), skillId(skillId)).view();
    }

    public List<Map<String, Object>> listSkillArtifacts(String projectId,
                                                         String skillId,
                                                         int version,
                                                         String skillHash,
                                                         String packageHash,
                                                         String scope) {
        return packageQueryService.listArtifacts(
                projectId, skillId, version, skillHash, packageHash, scope);
    }

    public Map<String, Object> getSkillVersion(String projectId,
                                               String skillId,
                                               int version,
                                               String skillHash,
                                               String packageHash,
                                               String scope) {
        return packageQueryService.getVersion(
                projectId, skillId, version, skillHash, packageHash, scope);
    }

    public Map<String, Object> getSkillArtifact(String projectId,
                                               String skillId,
                                               int version,
                                               String skillHash,
                                               String packageHash,
                                               String scope,
                                               String artifactPath) {
        return packageQueryService.getArtifact(
                projectId, skillId, version, skillHash, packageHash, scope, artifactPath);
    }

    public List<Map<String, Object>> listGlobalVersions(String skillId) {
        return packageQueryService.listGlobalVersions(skillId);
    }

    public List<Map<String, Object>> listProjectVersions(String projectId, String skillId) {
        return packageQueryService.listProjectVersions(projectId, skillId);
    }

    @Override
    public List<String> projectCatalogSkillIds(String projectId) {
        return port.projectCatalogSkillIds(projectId(projectId));
    }

    @Override
    public List<String> globalCatalogSkillIds() {
        return port.globalCatalogSkillIds();
    }

    private String projectId(String value) {
        return required(value, "SKILL_PROJECT_ID_REQUIRED");
    }

    private String skillId(String value) {
        return required(value, "SKILL_ID_REQUIRED");
    }

    private String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
