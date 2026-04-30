package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillCatalogRepository;
import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillPackageRepository;
import cn.lgs.orbisops.domain.skill.model.SkillArtifact;
import cn.lgs.orbisops.domain.skill.model.SkillCatalogEntry;
import cn.lgs.orbisops.domain.skill.model.SkillPackageKey;
import cn.lgs.orbisops.domain.skill.service.SkillCatalogFingerprint;
import cn.lgs.orbisops.domain.skill.service.SkillCatalogQueryPolicy;
import cn.lgs.orbisops.domain.skill.service.SkillPackageManifest;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Application read service combining typed database catalog entries with file-backed fallbacks. */
public final class SkillCatalogReadService implements SkillCatalogPort {

    private static final String GLOBAL = "GLOBAL";
    private static final String PROJECT = "PROJECT";
    private static final Set<String> STATUSES = Set.of(
            "DRAFT", "ACTIVE", "PAUSED", "FROZEN", "DEPRECATED", "REJECTED", "ENABLED", "DISABLED");

    private final ISkillCatalogRepository catalogRepository;
    private final ISkillPackageRepository packageRepository;
    private final SkillFileSourcePort fileSourcePort;
    private SkillFileProjectionPort fileProjection;
    private final SkillProjectValidationPort projectValidationPort;
    private final SkillCatalogViewMapper catalogViewMapper;
    private final SkillFileCatalogViewMapper fileViewMapper;
    private final SkillCatalogQueryPolicy queryPolicy;
    private final SkillRuntimeCandidateAssembler candidateAssembler =
            new SkillRuntimeCandidateAssembler();

    public SkillCatalogReadService(ISkillCatalogRepository catalogRepository,
                                   ISkillPackageRepository packageRepository,
                                   SkillFileSourcePort fileSourcePort,
                                   SkillProjectValidationPort projectValidationPort) {
        this(catalogRepository, packageRepository, fileSourcePort, projectValidationPort,
                new SkillCatalogViewMapper(), new SkillFileCatalogViewMapper(), new SkillCatalogQueryPolicy());
    }

    public SkillCatalogReadService(ISkillCatalogRepository catalogRepository,
                                   ISkillPackageRepository packageRepository,
                                   SkillFileSourcePort fileSourcePort,
                                   SkillProjectValidationPort projectValidationPort,
                                   SkillFileProjectionPort fileProjection) {
        this(catalogRepository,packageRepository,fileSourcePort,projectValidationPort);
        this.fileProjection=java.util.Objects.requireNonNull(fileProjection);
    }

    SkillCatalogReadService(ISkillCatalogRepository catalogRepository,
                            ISkillPackageRepository packageRepository,
                            SkillFileSourcePort fileSourcePort,
                            SkillProjectValidationPort projectValidationPort,
                            SkillCatalogViewMapper catalogViewMapper,
                            SkillFileCatalogViewMapper fileViewMapper,
                            SkillCatalogQueryPolicy queryPolicy) {
        if (catalogRepository == null) throw new IllegalArgumentException("SKILL_CATALOG_REPOSITORY_REQUIRED");
        if (packageRepository == null) throw new IllegalArgumentException("SKILL_PACKAGE_REPOSITORY_REQUIRED");
        if (fileSourcePort == null) throw new IllegalArgumentException("SKILL_FILE_SOURCE_REQUIRED");
        if (projectValidationPort == null) throw new IllegalArgumentException("SKILL_PROJECT_VALIDATION_PORT_REQUIRED");
        if (catalogViewMapper == null) throw new IllegalArgumentException("SKILL_CATALOG_VIEW_MAPPER_REQUIRED");
        if (fileViewMapper == null) throw new IllegalArgumentException("SKILL_FILE_VIEW_MAPPER_REQUIRED");
        if (queryPolicy == null) throw new IllegalArgumentException("SKILL_CATALOG_QUERY_POLICY_REQUIRED");
        this.catalogRepository = catalogRepository;
        this.packageRepository = packageRepository;
        this.fileSourcePort = fileSourcePort;
        this.projectValidationPort = projectValidationPort;
        this.catalogViewMapper = catalogViewMapper;
        this.fileViewMapper = fileViewMapper;
        this.queryPolicy = queryPolicy;
    }

    @Override
    public List<SkillCatalogSnapshot> listGlobalEntries() {
        List<Map<String, Object>> databaseEntries = catalogRepository.available()
                ? safeEntries(catalogRepository.findAll(GLOBAL, "", false)).stream()
                .map(entry -> catalogViewMapper.toView(entry, false)).toList()
                : List.of();
        List<Map<String, Object>> fileEntries = fileSourcePort.findAll().stream()
                .filter(this::isGlobalFileSkill)
                .map(skill -> fileViewMapper.toView(skill, false))
                .toList();
        return queryPolicy.mergeDatabaseFirst(databaseEntries, fileEntries, this::skillId)
                .stream()
                .map(this::snapshot)
                .toList();
    }

    @Override
    public SkillCatalogSnapshot getGlobalEntry(String skillId) {
        String requestedId = requiredSkillId(skillId);
        Optional<SkillCatalogEntry> stored = findStored(GLOBAL, "", requestedId, true);
        if (stored.isPresent()) {
            Map<String, Object> view = catalogViewMapper.toView(stored.get(), true);
            return snapshot(withArtifactMetadata(view, stored.get().key()));
        }
        SkillFileDefinition file = fileSourcePort.findById(requestedId)
                .filter(this::isGlobalFileSkill)
                .orElseThrow(() -> new IllegalArgumentException("通用 Skill 不存在：" + skillId));
        return snapshot(fileViewMapper.toView(file, true));
    }

    @Override
    public List<SkillCatalogSnapshot> listRuntimeGlobalEntries() { return runtimeMetadata(GLOBAL, ""); }

    @Override
    public List<SkillCatalogSnapshot> listRuntimeProjectEntries(String projectId) {
        return runtimeMetadata(PROJECT, requiredProject(projectId));
    }

    @Override
    public List<String> configuredGlobalSkillIds(String projectId) {
        return projectValidationPort.configuredSkillIds(requiredProject(projectId));
    }

    private List<SkillCatalogSnapshot> runtimeMetadata(String scope, String project) {
        List<Map<String,Object>> database = catalogRepository.available()
                ? safeEntries(catalogRepository.findEvolutionMetadata(scope, project)).stream()
                    .map(entry -> catalogViewMapper.toView(entry, false)).toList() : List.of();
        List<Map<String,Object>> files = fileSourcePort.findAll().stream()
                .filter(skill -> GLOBAL.equals(scope) ? isGlobalFileSkill(skill) : isProjectFileSkill(skill, project))
                .map(skill -> fileViewMapper.toView(skill, false)).toList();
        if (fileProjection != null && catalogRepository.available()) {
            Set<String> managed = fileProjection.managedIds(scope, project);
            Set<String> live = files.stream().map(this::skillId).collect(java.util.stream.Collectors.toSet());
            // Removed/unreadable files immediately lose runtime visibility; existing user-owned rows remain independent.
            return database.stream().filter(entry -> !managed.contains(skillId(entry)) || live.contains(skillId(entry)))
                    .map(this::snapshot).toList();
        }
        return queryPolicy.mergeDatabaseFirst(database, files, this::skillId).stream().map(this::snapshot).toList();
    }

    @Override
    public List<SkillCatalogSnapshot> listProjectEntries(String projectId) {
        String project = requiredProject(projectId);
        List<Map<String, Object>> databaseEntries = catalogRepository.available()
                ? safeEntries(catalogRepository.findAll(PROJECT, project, false)).stream()
                .map(entry -> catalogViewMapper.toView(entry, false)).toList()
                : List.of();
        List<Map<String, Object>> fileEntries = fileSourcePort.findAll().stream()
                .filter(skill -> isProjectFileSkill(skill, project))
                .map(skill -> fileViewMapper.toView(skill, false))
                .toList();
        return queryPolicy.mergeDatabaseFirst(databaseEntries, fileEntries, this::skillId)
                .stream()
                .map(this::snapshot)
                .toList();
    }

    @Override
    public SkillCatalogSnapshot getProjectEntry(String projectId, String skillId) {
        String project = requiredProject(projectId);
        String requestedId = requiredSkillId(skillId);
        Optional<SkillCatalogEntry> stored = findStored(PROJECT, project, requestedId, true);
        if (stored.isPresent()) {
            Map<String, Object> view = catalogViewMapper.toView(stored.get(), true);
            return snapshot(withArtifactMetadata(view, stored.get().key()));
        }
        SkillFileDefinition file = fileSourcePort.findById(requestedId)
                .filter(skill -> isProjectFileSkill(skill, project))
                .orElseThrow(() -> new IllegalArgumentException("项目 Skill 不存在：" + skillId));
        return snapshot(fileViewMapper.toView(file, true));
    }

    @Override
    public List<String> projectCatalogSkillIds(String projectId) {
        String project = text(projectId);
        if (project.isEmpty() || !catalogRepository.available()) return List.of();
        try {
            return activeCatalogSkillIds(PROJECT, project);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    @Override
    public List<String> globalCatalogSkillIds() {
        if (!catalogRepository.available()) return List.of();
        try {
            return activeCatalogSkillIds(GLOBAL, "");
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private SkillCatalogSnapshot snapshot(Map<String, Object> view) {
        return new SkillCatalogSnapshot(candidateAssembler.fromView(view), view);
    }

    private List<String> activeCatalogSkillIds(String scope, String projectId) {
        return safeEntries(catalogRepository.findAll(scope, projectId, false)).stream()
                .filter(entry -> Set.of("ACTIVE", "ENABLED").contains(normalizeStatus(entry.status())))
                .sorted(Comparator.comparingLong(SkillCatalogEntry::id))
                .map(SkillCatalogEntry::skillId)
                .toList();
    }

    private Optional<SkillCatalogEntry> findStored(String scope,
                                                   String projectId,
                                                   String skillId,
                                                   boolean includeContent) {
        if (!catalogRepository.available()) return Optional.empty();
        return catalogRepository.find(scope, GLOBAL.equals(scope) ? "" : projectId,
                SkillCatalogFingerprint.normalizeId(skillId), includeContent);
    }

    private Map<String, Object> withArtifactMetadata(Map<String, Object> skill, SkillPackageKey key) {
        Map<String, Object> result = new LinkedHashMap<>(skill);
        List<Map<String, Object>> artifacts;
        if (packageRepository.available()) {
            artifacts = packageRepository.findArtifacts(key).stream()
                    .map(this::artifactMetadataView)
                    .toList();
        } else {
            if (catalogRepository.available()) throw new IllegalStateException("SKILL_PACKAGE_STORE_UNAVAILABLE");
            artifacts = List.of();
        }
        if (artifacts.isEmpty()) {
            SkillPackageManifest.Descriptor descriptor = packageDescriptor(skill);
            artifacts = descriptor.artifacts().values().stream()
                    .map(this::artifactMetadataView)
                    .toList();
        }
        result.put("artifacts", artifacts);
        result.put("artifactCount", artifacts.size());
        result.put("packageSize", artifacts.stream()
                .mapToLong(item -> longValue(item.get("sizeBytes"), 0L))
                .sum());
        result.put("entrypoint", SkillPackageManifest.ENTRYPOINT);
        return result;
    }

    private SkillPackageManifest.Descriptor packageDescriptor(Map<String, Object> skill) {
        return SkillPackageManifest.markdown(
                fallback(skill.get("scope"), GLOBAL),
                text(skill.get("projectId")),
                text(skill.get("skillId")),
                fallback(firstNonNull(skill.get("name"), skill.get("skillName")), text(skill.get("skillId"))),
                text(skill.get("description")),
                integer(firstNonNull(skill.get("currentVersion"), skill.get("version")), 1),
                text(firstNonNull(skill.get("content"), skill.get("markdown"))));
    }

    private Map<String, Object> artifactMetadataView(SkillArtifact artifact) {
        return Map.of(
                "path", artifact.path(),
                "role", artifact.role(),
                "mediaType", artifact.mediaType(),
                "encoding", artifact.encoding(),
                "contentHash", artifact.contentHash(),
                "sizeBytes", artifact.sizeBytes());
    }

    private Map<String, Object> artifactMetadataView(SkillPackageManifest.ArtifactContent artifact) {
        return Map.of(
                "path", artifact.path(),
                "role", artifact.role(),
                "mediaType", artifact.mediaType(),
                "encoding", artifact.encoding(),
                "contentHash", artifact.contentHash(),
                "sizeBytes", artifact.sizeBytes());
    }

    private boolean isGlobalFileSkill(SkillFileDefinition skill) {
        return queryPolicy.isGlobalFileSkill(
                text(skill.frontMatter().get("scope")), projectId(skill.frontMatter()));
    }

    private boolean isProjectFileSkill(SkillFileDefinition skill, String expectedProjectId) {
        return queryPolicy.isProjectFileSkill(
                text(skill.frontMatter().get("scope")), projectId(skill.frontMatter()), expectedProjectId);
    }

    private String projectId(Map<String, Object> frontMatter) {
        return text(firstNonNull(frontMatter.get("projectId"), frontMatter.get("project_id")));
    }

    private String requiredProject(String projectId) {
        String project = text(projectId);
        if (project.isEmpty()) throw new IllegalArgumentException("SKILL_PROJECT_ID_REQUIRED");
        projectValidationPort.requireExisting(project);
        return project;
    }

    private String requiredSkillId(String skillId) {
        String normalized = SkillCatalogFingerprint.normalizeId(text(skillId));
        if (normalized.isEmpty()) throw new IllegalArgumentException("SKILL_ID_REQUIRED");
        return normalized;
    }

    private List<SkillCatalogEntry> safeEntries(List<SkillCatalogEntry> values) {
        return values == null ? List.of() : values;
    }

    private String skillId(Map<String, Object> value) {
        return text(value == null ? null : value.get("skillId"));
    }

    private String normalizeStatus(String value) {
        String normalized = text(value).toUpperCase();
        return STATUSES.contains(normalized) ? normalized : "ENABLED";
    }

    private Object firstNonNull(Object left, Object right) {
        return left == null ? right : left;
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(text(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private long longValue(Object value, long fallback) {
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(text(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String fallback(Object value, String fallback) {
        String normalized = text(value);
        return normalized.isEmpty() ? fallback : normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
