package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.skill.service.SkillGovernancePolicy;
import cn.lgs.orbisops.domain.skill.service.SkillPackageManifest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SkillManagementUseCase {

    private final SkillCatalogPort port;
    private final SkillCatalogMutationUseCase mutationUseCase;
    private final SkillRollbackUseCase rollbackUseCase;
    private final SkillEvolutionPublishUseCase evolutionPublishUseCase;
    private final SkillPackageQueryService packageQueryService;
    private final SkillGovernancePolicy governancePolicy;

    public SkillManagementUseCase(SkillCatalogPort port,
                                  SkillCatalogMutationUseCase mutationUseCase,
                                  SkillRollbackUseCase rollbackUseCase,
                                  SkillEvolutionPublishUseCase evolutionPublishUseCase,
                                  SkillPackageQueryService packageQueryService) {
        this(port, mutationUseCase, rollbackUseCase, evolutionPublishUseCase,
                packageQueryService, new SkillGovernancePolicy());
    }

    SkillManagementUseCase(SkillCatalogPort port,
                           SkillCatalogMutationUseCase mutationUseCase,
                           SkillRollbackUseCase rollbackUseCase,
                           SkillEvolutionPublishUseCase evolutionPublishUseCase,
                           SkillPackageQueryService packageQueryService,
                           SkillGovernancePolicy governancePolicy) {
        if (port == null) throw new IllegalArgumentException("SKILL_CATALOG_PORT_REQUIRED");
        if (mutationUseCase == null) throw new IllegalArgumentException("SKILL_MUTATION_USE_CASE_REQUIRED");
        if (rollbackUseCase == null) throw new IllegalArgumentException("SKILL_ROLLBACK_USE_CASE_REQUIRED");
        if (evolutionPublishUseCase == null) {
            throw new IllegalArgumentException("SKILL_EVOLUTION_USE_CASE_REQUIRED");
        }
        if (packageQueryService == null) throw new IllegalArgumentException("SKILL_PACKAGE_QUERY_REQUIRED");
        if (governancePolicy == null) throw new IllegalArgumentException("SKILL_GOVERNANCE_POLICY_REQUIRED");
        this.port = port;
        this.mutationUseCase = mutationUseCase;
        this.rollbackUseCase = rollbackUseCase;
        this.evolutionPublishUseCase = evolutionPublishUseCase;
        this.packageQueryService = packageQueryService;
        this.governancePolicy = governancePolicy;
    }

    public Map<String, Object> createGlobalSkill(Map<String, Object> request, String actor) {
        String operator = required(actor, "SKILL_ACTOR_REQUIRED");
        SkillCatalogWriteOutcome outcome = mutationUseCase.createGlobal(
                governancePolicy.normalizeCreate(createCommand(request, operator)), operator);
        return port.getGlobalEntry(outcome.publishedVersion().key().skillId()).view();
    }

    public Map<String, Object> updateGlobalSkill(String skillId,
                                                 Map<String, Object> request,
                                                 String actor) {
        String id = skillId(skillId);
        String operator = required(actor, "SKILL_ACTOR_REQUIRED");
        Map<String, Object> current = port.getGlobalEntry(id).view();
        Map<String, Object> command = governancePolicy.normalizeUpdate(
                current, mutationCommand(request, current, operator));
        mutationUseCase.updateGlobal(id, current, command, operator);
        return port.getGlobalEntry(id).view();
    }

    public Map<String, Object> updateGlobalStatus(String skillId,
                                                  String status,
                                                  String actor) {
        String id = skillId(skillId);
        String operator = required(actor, "SKILL_ACTOR_REQUIRED");
        Map<String, Object> current = port.getGlobalEntry(id).view();
        String target = governancePolicy.transitionStatus(
                current, required(status, "SKILL_STATUS_REQUIRED"));
        Map<String, Object> command = mutationCommand(
                Map.of("status", target, "changeSummary", "status -> " + target), current, operator);
        mutationUseCase.updateGlobal(id, current, command, operator);
        return port.getGlobalEntry(id).view();
    }

    public Map<String, Object> updateGlobalUpdateMode(String skillId,
                                                       Map<String, Object> request,
                                                       String actor) {
        String id = skillId(skillId);
        String operator = required(actor, "SKILL_ACTOR_REQUIRED");
        Map<String, Object> current = port.getGlobalEntry(id).view();
        Map<String, Object> command = governancePolicy.normalizeUpdateMode(
                current, mutationCommand(request, current, operator));
        mutationUseCase.updateGlobal(id, current, command, operator);
        return port.getGlobalEntry(id).view();
    }

    public Map<String, Object> rollbackGlobalVersion(String skillId,
                                                      int version,
                                                      String actor) {
        String id = skillId(skillId);
        String operator = required(actor, "SKILL_ACTOR_REQUIRED");
        rollbackUseCase.rollbackGlobal(id, version(version), operator);
        return port.getGlobalEntry(id).view();
    }

    public Map<String, Object> createProjectSkill(String projectId,
                                                  Map<String, Object> request,
                                                  String actor) {
        String project = projectId(projectId);
        SkillPublicationOutcome outcome = createProjectSkillOutcome(project, request, actor);
        return port.getProjectEntry(project, outcome.skillId()).view();
    }

    public SkillPublicationOutcome createProjectSkillOutcome(
            String projectId,
            Map<String, Object> request,
            String actor) {
        String project = projectId(projectId);
        String operator = required(actor, "SKILL_ACTOR_REQUIRED");
        SkillCatalogWriteOutcome outcome = mutationUseCase.createProject(
                project, governancePolicy.normalizeCreate(createCommand(request, operator)), operator);
        return SkillPublicationOutcome.published("CREATED", outcome.publishedVersion());
    }

    public Map<String, Object> copyGlobalToProject(String projectId,
                                                   String globalSkillId,
                                                   Map<String, Object> request,
                                                   String actor) {
        String project = projectId(projectId);
        String sourceId = skillId(globalSkillId);
        String operator = required(actor, "SKILL_ACTOR_REQUIRED");
        Map<String, Object> global = port.getGlobalEntry(sourceId).view();
        String resolvedSourceId = text(global.get("skillId"), sourceId);
        int sourceVersion = number(first(global.get("currentVersion"), global.get("version")), 1);
        List<Map<String, Object>> artifacts = packageQueryService.listArtifacts(
                "", resolvedSourceId, sourceVersion,
                text(first(global.get("currentSkillHash"), global.get("skillHash")), ""),
                text(first(global.get("currentPackageHash"), global.get("packageHash")), ""),
                "GLOBAL");

        Map<String, Object> command = new LinkedHashMap<>(global);
        command.putAll(copy(request));
        String targetSkillId = text(copy(request).get("skillId"), project + "-" + resolvedSourceId);
        command.put("skillId", targetSkillId);
        command.put("name", text(command.get("name"), text(global.get("name"), targetSkillId)));
        command.put("sourceGlobalSkillId", resolvedSourceId);
        command.put("status", "ENABLED");
        command.put("artifacts", artifacts.stream()
                .filter(item -> !SkillPackageManifest.ENTRYPOINT.equals(text(item.get("path"), "")))
                .toList());
        Map<String, Object> manifest = manifest(global.get("packageManifestJson"));
        command.put("dependencies", manifest.getOrDefault("dependencies", List.of()));
        command.put("evalSuites", manifest.getOrDefault("evalSuites", List.of()));
        command.remove("id");
        command.remove("currentVersion");
        command.remove("currentSkillHash");
        command.remove("versionSeq");
        command.remove("currentPackageHash");
        command.remove("packageHash");
        command.remove("artifactHashes");

        SkillCatalogWriteOutcome outcome = mutationUseCase.createProject(
                project, governancePolicy.normalizeCreate(createCommand(command, operator)), operator);
        return port.getProjectEntry(project, outcome.publishedVersion().key().skillId()).view();
    }

    public Map<String, Object> updateProjectSkill(String projectId,
                                                  String skillId,
                                                  Map<String, Object> request,
                                                  String actor) {
        String project = projectId(projectId);
        String id = skillId(skillId);
        String operator = required(actor, "SKILL_ACTOR_REQUIRED");
        Map<String, Object> current = port.getProjectEntry(project, id).view();
        Map<String, Object> command = governancePolicy.normalizeUpdate(
                current, mutationCommand(request, current, operator));
        mutationUseCase.updateProject(project, id, current, command, operator);
        return port.getProjectEntry(project, id).view();
    }

    public Map<String, Object> publishEvolvedProjectSkill(String projectId,
                                                          String skillId,
                                                          Map<String, Object> request,
                                                          int baseVersion,
                                                          String baseSkillHash,
                                                          String actor) {
        String project = projectId(projectId);
        String id = skillId(skillId);
        SkillPublicationOutcome outcome = publishEvolvedProjectSkillOutcome(
                project, id, request, baseVersion, baseSkillHash, actor);
        Map<String, Object> current = port.getProjectEntry(project, id).view();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("published", outcome.published());
        result.put("reasonCode", outcome.reasonCode());
        result.put("skill", current);
        if (!outcome.published()) {
            result.put("currentVersion", outcome.version() > 0
                    ? outcome.version()
                    : number(first(current.get("currentVersion"), current.get("version")), 0));
            result.put("baseVersion", baseVersion);
            result.put("currentSkillHash", !outcome.skillHash().isBlank()
                    ? outcome.skillHash()
                    : text(first(current.get("currentSkillHash"), current.get("skillHash")), ""));
            result.put("baseSkillHash", baseSkillHash);
        }
        return Map.copyOf(result);
    }

    public SkillPublicationOutcome publishEvolvedProjectSkillOutcome(
            String projectId,
            String skillId,
            Map<String, Object> request,
            int baseVersion,
            String baseSkillHash,
            String actor) {
        String project = projectId(projectId);
        String id = skillId(skillId);
        SkillEvolutionPublishOutcome outcome = evolutionPublishUseCase.publishProject(
                project, id, request, version(baseVersion),
                required(baseSkillHash, "SKILL_BASE_HASH_REQUIRED"),
                required(actor, "SKILL_ACTOR_REQUIRED"));
        return outcome.published()
                ? SkillPublicationOutcome.published(outcome.reasonCode(), outcome.publishedVersion())
                : SkillPublicationOutcome.rejected(outcome.reasonCode(), outcome.current());
    }

    public Map<String, Object> updateProjectStatus(String projectId,
                                                   String skillId,
                                                   String status,
                                                   String actor) {
        String project = projectId(projectId);
        String id = skillId(skillId);
        String operator = required(actor, "SKILL_ACTOR_REQUIRED");
        Map<String, Object> current = port.getProjectEntry(project, id).view();
        String target = governancePolicy.transitionStatus(
                current, required(status, "SKILL_STATUS_REQUIRED"));
        Map<String, Object> command = mutationCommand(
                Map.of("status", target, "changeSummary", "status -> " + target), current, operator);
        mutationUseCase.updateProject(project, id, current, command, operator);
        return port.getProjectEntry(project, id).view();
    }

    public Map<String, Object> updateProjectUpdateMode(String projectId,
                                                       String skillId,
                                                       Map<String, Object> request,
                                                       String actor) {
        String project = projectId(projectId);
        String id = skillId(skillId);
        String operator = required(actor, "SKILL_ACTOR_REQUIRED");
        Map<String, Object> current = port.getProjectEntry(project, id).view();
        Map<String, Object> command = governancePolicy.normalizeUpdateMode(
                current, mutationCommand(request, current, operator));
        mutationUseCase.updateProject(project, id, current, command, operator);
        return port.getProjectEntry(project, id).view();
    }

    public Map<String, Object> rollbackProjectVersion(String projectId,
                                                       String skillId,
                                                       int version,
                                                       String actor) {
        String project = projectId(projectId);
        String id = skillId(skillId);
        String operator = required(actor, "SKILL_ACTOR_REQUIRED");
        rollbackUseCase.rollbackProject(project, id, version(version), operator);
        return port.getProjectEntry(project, id).view();
    }

    public SkillReleaseRecoveryOutcome recoverProjectRelease(cn.lgs.orbisops.domain.skill.model.SkillReleaseSnapshot release) {
        return rollbackUseCase.recoverRelease(release);
    }

    private int version(int value) {
        if (value <= 0) throw new IllegalArgumentException("SKILL_VERSION_INVALID");
        return value;
    }

    private String projectId(String value) {
        return required(value, "SKILL_PROJECT_ID_REQUIRED");
    }

    private String skillId(String value) {
        return required(value, "SKILL_ID_REQUIRED");
    }

    private String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private Map<String, Object> createCommand(Map<String, Object> request, String actor) {
        Map<String, Object> command = copy(request);
        String operator = required(actor, "SKILL_ACTOR_REQUIRED");
        command.put("createBy", operator);
        command.put("sourceTraceId", operator);
        command.remove("actor");
        return command;
    }

    private Map<String, Object> mutationCommand(Map<String, Object> request,
                                                Map<String, Object> current,
                                                String actor) {
        Map<String, Object> command = copy(request);
        String operator = required(actor, "SKILL_ACTOR_REQUIRED");
        String originalCreator = current == null ? "" : String.valueOf(
                current.getOrDefault("createBy", "")).trim();
        command.put("createBy", originalCreator.isBlank() ? operator : originalCreator);
        command.put("sourceTraceId", operator);
        command.remove("actor");
        return command;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> manifest(Object value) {
        String json = text(value, "");
        if (json.isBlank()) return Map.of();
        try {
            Map<String, Object> parsed = CanonicalJson.parseObject(json);
            return parsed == null ? Map.of() : parsed;
        } catch (Exception e) {
            throw new IllegalArgumentException("Skill Package manifest JSON 无效", e);
        }
    }

    private int number(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(text(value, String.valueOf(fallback)));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private Object first(Object left, Object right) {
        return left == null || text(left, "").isBlank() ? right : left;
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    private Map<String, Object> copy(Map<String, Object> value) {
        return value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
    }
}
