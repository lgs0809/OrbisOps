package cn.lgs.orbisops.application.skill;

import java.util.List;
import java.util.Map;

public final class SkillCatalogQueryService implements SkillAuthorizationCatalogPort {

    private final SkillCatalogPort port;
    private final SkillPackageQueryService packageQueryService;
    private final SkillRuntimePublishedVersionPort runtimePublication;

    public SkillCatalogQueryService(SkillCatalogPort port,
                                    SkillPackageQueryService packageQueryService) {
        this(port,packageQueryService,(project,current)->current);
    }
    public SkillCatalogQueryService(SkillCatalogPort port,SkillPackageQueryService packageQueryService,
                                    SkillRuntimePublishedVersionPort runtimePublication) {
        if (port == null) throw new IllegalArgumentException("SKILL_CATALOG_PORT_REQUIRED");
        if (packageQueryService == null) throw new IllegalArgumentException("SKILL_PACKAGE_QUERY_REQUIRED");
        this.port = port;
        this.packageQueryService = packageQueryService;
        this.runtimePublication=java.util.Objects.requireNonNull(runtimePublication);
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

    public Map<String,Object> getRuntimeSkillVersion(String projectId,String skillId,int version,
            String skillHash,String packageHash,String scope) {
        SkillRuntimeCatalogAccess access=new SkillRuntimeCatalogAccess(port);
        requireUsable(access,projectId,skillId,scope);
        var result=getSkillVersion(projectId,skillId,version,skillHash,packageHash,scope);
        requireUsable(access,projectId,skillId,scope);
        return result;
    }

    public Map<String,Object> getRuntimeSkillArtifact(String projectId,String skillId,int version,
            String skillHash,String packageHash,String scope,String path) {
        SkillRuntimeCatalogAccess access=new SkillRuntimeCatalogAccess(port);
        requireUsable(access,projectId,skillId,scope);
        var result=getSkillArtifact(projectId,skillId,version,skillHash,packageHash,scope,path);
        requireUsable(access,projectId,skillId,scope);
        return result;
    }

    public List<Map<String,Object>> retainRuntimeCatalogRefs(String projectId, List<Map<String,Object>> refs) {
        SkillRuntimeCatalogAccess access = new SkillRuntimeCatalogAccess(port);
        var current = runtimePublication.usableFrozen(projectId,access.active(projectId));
        return refs.stream().filter(ref -> current.stream().anyMatch(c -> c.skillId().equals(ref.get("skillId"))
                && c.scope().equals(ref.get("scope"))
                && ("GLOBAL".equals(c.scope()) || projectId.equals(ref.get("projectId"))))).toList();
    }

    private void requireUsable(SkillRuntimeCatalogAccess access,String project,String id,String scope) {
        if(runtimePublication.usableFrozen(project,access.active(project)).stream().noneMatch(c->id.equals(c.skillId()) && scope.equals(c.scope())))
            throw new SkillRuntimeAccessRevokedException();
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
