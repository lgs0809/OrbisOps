package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillPackageRepository;
import cn.lgs.orbisops.domain.skill.model.SkillArtifact;
import cn.lgs.orbisops.domain.skill.model.SkillPackageKey;
import cn.lgs.orbisops.domain.skill.model.SkillPackageVersion;
import cn.lgs.orbisops.domain.skill.service.SkillCatalogFingerprint;
import cn.lgs.orbisops.domain.skill.service.SkillPackageManifest;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Typed immutable Skill Package query boundary with exact identity verification. */
public final class SkillPackageQueryService {

    private static final String GLOBAL = "GLOBAL";
    private static final String PROJECT = "PROJECT";

    private final SkillCatalogPort catalogPort;
    private final ISkillPackageRepository packageRepository;
    private final SkillProjectValidationPort projectValidationPort;

    public SkillPackageQueryService(SkillCatalogPort catalogPort,
                                    ISkillPackageRepository packageRepository,
                                    SkillProjectValidationPort projectValidationPort) {
        if (catalogPort == null) throw new IllegalArgumentException("SKILL_CATALOG_PORT_REQUIRED");
        if (packageRepository == null) throw new IllegalArgumentException("SKILL_PACKAGE_REPOSITORY_REQUIRED");
        if (projectValidationPort == null) {
            throw new IllegalArgumentException("SKILL_PROJECT_VALIDATION_PORT_REQUIRED");
        }
        this.catalogPort = catalogPort;
        this.packageRepository = packageRepository;
        this.projectValidationPort = projectValidationPort;
    }

    public List<Map<String, Object>> listGlobalVersions(String skillId) {
        return listVersions(GLOBAL, "", skillId);
    }

    public List<Map<String, Object>> listProjectVersions(String projectId, String skillId) {
        String project = required(projectId, "SKILL_PROJECT_ID_REQUIRED");
        projectValidationPort.requireExisting(project);
        return listVersions(PROJECT, project, skillId);
    }

    public Map<String, Object> getVersion(String projectId,
                                          String skillId,
                                          int version,
                                          String expectedSkillHash,
                                          String expectedPackageHash,
                                          String scope) {
        Identity identity = identity(projectId, skillId, version, expectedSkillHash, scope);
        Map<String, Object> current = current(identity.scope(), identity.projectId(), identity.skillId());
        if (currentMatches(current, identity.version(), identity.skillHash(), expectedPackageHash)) {
            return withArtifactMetadata(current, identity.key());
        }

        SkillPackageVersion exact = packageRepository.available()
                ? packageRepository.findVersion(identity.key())
                .filter(item -> identity.skillHash().equals(item.skillHash()))
                .filter(item -> packageHashMatches(versionView(item), expectedPackageHash))
                .orElseThrow(() -> notFound(identity))
                : null;
        if (exact == null) throw notFound(identity);

        Map<String, Object> result = new LinkedHashMap<>(current);
        result.putAll(versionView(exact));
        result.put("currentVersion", identity.version());
        result.put("currentSkillHash", identity.skillHash());
        result.put("currentPackageHash", result.get("packageHash"));
        result.put("statusAtUse", current.get("status"));
        return withArtifactMetadata(result, identity.key());
    }

    public List<Map<String, Object>> listArtifacts(String projectId,
                                                    String skillId,
                                                    int version,
                                                    String expectedSkillHash,
                                                    String expectedPackageHash,
                                                    String scope) {
        Identity identity = identity(projectId, skillId, version, expectedSkillHash, scope);
        Map<String, Object> skill = getVersion(projectId, skillId, version,
                expectedSkillHash, expectedPackageHash, scope);
        List<SkillArtifact> stored = packageRepository.available()
                ? packageRepository.findArtifacts(identity.key()) : List.of();
        if (stored.isEmpty()) return List.of(entrypointContent(skill));
        return stored.stream().map(this::artifactContentView).toList();
    }

    public Map<String, Object> getArtifact(String projectId,
                                           String skillId,
                                           int version,
                                           String expectedSkillHash,
                                           String expectedPackageHash,
                                           String scope,
                                           String artifactPath) {
        Identity identity = identity(projectId, skillId, version, expectedSkillHash, scope);
        String path = SkillPackageManifest.normalizeArtifactPath(artifactPath);
        Map<String, Object> skill = getVersion(projectId, skillId, version,
                expectedSkillHash, expectedPackageHash, scope);
        Optional<SkillArtifact> stored = packageRepository.available()
                ? packageRepository.findArtifact(identity.key(), path) : Optional.empty();
        if (stored.isPresent()) return artifactContentView(stored.get());
        if (SkillPackageManifest.ENTRYPOINT.equals(path)) return entrypointContent(skill);
        throw new IllegalArgumentException("Skill artifact 不存在：" + path);
    }

    private List<Map<String, Object>> listVersions(String scope, String projectId, String skillId) {
        String normalizedSkillId = normalizeSkillId(skillId);
        if (!packageRepository.available()) return List.of();
        return packageRepository.findVersions(scope, projectId, normalizedSkillId)
                .stream().map(this::versionView).toList();
    }

    private Identity identity(String projectId,
                              String skillId,
                              int version,
                              String expectedSkillHash,
                              String scope) {
        String normalizedScope = GLOBAL.equalsIgnoreCase(text(scope)) ? GLOBAL : PROJECT;
        String normalizedProjectId = "";
        if (PROJECT.equals(normalizedScope)) {
            normalizedProjectId = required(projectId, "SKILL_PROJECT_ID_REQUIRED");
            projectValidationPort.requireExisting(normalizedProjectId);
        }
        String normalizedSkillId = normalizeSkillId(skillId);
        if (version <= 0) throw new IllegalArgumentException("SKILL_VERSION_INVALID");
        String skillHash = required(expectedSkillHash, "SKILL_HASH_REQUIRED");
        return new Identity(normalizedScope, normalizedProjectId, normalizedSkillId, version, skillHash);
    }

    private Map<String, Object> current(String scope, String projectId, String skillId) {
        return GLOBAL.equals(scope)
                ? catalogPort.getGlobalEntry(skillId).view()
                : catalogPort.getProjectEntry(projectId, skillId).view();
    }

    private boolean currentMatches(Map<String, Object> current,
                                   int version,
                                   String expectedSkillHash,
                                   String expectedPackageHash) {
        return integer(first(current.get("currentVersion"), current.get("version")), 0) == version
                && expectedSkillHash.equals(text(first(current.get("currentSkillHash"), current.get("skillHash"))))
                && packageHashMatches(current, expectedPackageHash);
    }

    private boolean packageHashMatches(Map<String, Object> skill, String expectedPackageHash) {
        if (text(expectedPackageHash).isBlank()) return true;
        String actual = text(first(skill.get("currentPackageHash"), skill.get("packageHash")));
        if (actual.isBlank()) actual = packageDescriptor(skill).packageHash();
        return expectedPackageHash.trim().equals(actual);
    }

    private Map<String, Object> withArtifactMetadata(Map<String, Object> skill, SkillPackageKey key) {
        Map<String, Object> result = new LinkedHashMap<>(skill);
        List<Map<String, Object>> artifacts = packageRepository.available()
                ? packageRepository.findArtifacts(key).stream().map(this::artifactMetadataView).toList()
                : List.of();
        if (artifacts.isEmpty()) {
            SkillPackageManifest.Descriptor descriptor = packageDescriptor(skill);
            artifacts = descriptor.artifacts().values().stream().map(this::artifactMetadataView).toList();
        }
        result.put("artifacts", artifacts);
        result.put("artifactCount", artifacts.size());
        result.put("packageSize", artifacts.stream()
                .mapToLong(item -> longValue(item.get("sizeBytes"), 0L)).sum());
        result.put("entrypoint", SkillPackageManifest.ENTRYPOINT);
        if (!result.containsKey("statusAtUse") && result.containsKey("status")) {
            result.put("statusAtUse", result.get("status"));
        }
        return result;
    }

    private Map<String, Object> versionView(SkillPackageVersion version) {
        SkillPackageManifest.Descriptor fallback = SkillPackageManifest.markdown(
                version.key().scope(), version.key().projectId(), version.key().skillId(),
                version.key().skillId(), "", version.key().version(), version.content());
        String manifestJson = fallback(version.manifestJson(), fallback.manifestJson());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", version.id());
        data.put("skillId", version.key().skillId());
        data.put("projectId", version.key().projectId());
        data.put("scope", version.key().scope());
        data.put("version", version.key().version());
        Map<String,Object> manifest = CanonicalJson.parseObject(manifestJson);
        Map<?,?> metadata = manifest.get("metadata") instanceof Map<?,?> values ? values : Map.of();
        data.put("name", fallback(metadata.get("name"), version.key().skillId()));
        data.put("skillName", data.get("name"));
        data.put("description", text(metadata.get("description")));
        data.put("content", version.content());
        data.put("sourceType", version.sourceType());
        data.put("sourceTraceId", version.sourceTraceId());
        data.put("changeSummary", version.changeSummary());
        data.put("skillHash", version.skillHash());
        data.put("baseVersion", version.baseVersion());
        data.put("baseSkillHash", version.baseSkillHash());
        data.put("sourceRunId", version.sourceRunId());
        data.put("sourceSessionId", version.sourceSessionId());
        data.put("evolutionJobId", version.evolutionJobId());
        data.put("publishMode", version.publishMode());
        data.put("packageHash", fallback(version.packageHash(), fallback.packageHash()));
        data.put("packageManifestJson", manifestJson);
        data.put("manifestHash", sha256(manifestJson));
        data.put("artifactHashes", artifactHashes(version.artifactHashesJson(), fallback.artifactHashes()));
        data.put("entrypoint", fallback(version.entrypoint(), fallback.entrypoint()));
        data.put("artifactCount", version.artifactCount() <= 0 ? fallback.artifactCount() : version.artifactCount());
        data.put("packageSize", version.packageSize() <= 0 ? fallback.packageSize() : version.packageSize());
        data.put("createTime", String.valueOf(version.createdAt()));
        return Map.copyOf(data);
    }

    private SkillPackageManifest.Descriptor packageDescriptor(Map<String, Object> skill) {
        return SkillPackageManifest.markdown(
                fallback(skill.get("scope"), GLOBAL),
                fallback(skill.get("projectId"), ""),
                fallback(skill.get("skillId"), ""),
                fallback(first(skill.get("name"), skill.get("skillName")), fallback(skill.get("skillId"), "")),
                fallback(skill.get("description"), ""),
                integer(first(skill.get("currentVersion"), skill.get("version")), 1),
                fallback(first(skill.get("content"), skill.get("markdown")), ""));
    }

    private Map<String, Object> entrypointContent(Map<String, Object> skill) {
        String content = rawText(skill.get("content"));
        return Map.of(
                "path", SkillPackageManifest.ENTRYPOINT,
                "role", "ENTRYPOINT",
                "mediaType", "text/markdown; charset=utf-8",
                "encoding", SkillPackageManifest.ENCODING_UTF8,
                "contentHash", artifactHashes(skill.get("artifactHashes"), Map.of())
                        .getOrDefault(SkillPackageManifest.ENTRYPOINT, ""),
                "sizeBytes", content.getBytes(StandardCharsets.UTF_8).length,
                "content", content);
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

    private Map<String, Object> artifactContentView(SkillArtifact artifact) {
        Map<String, Object> result = new LinkedHashMap<>(artifactMetadataView(artifact));
        result.put("content", artifact.content());
        return Map.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> artifactHashes(Object value, Map<String, String> fallback) {
        if (value instanceof Map<?, ?> map) {
            Map<String, String> hashes = new TreeMap<>();
            map.forEach((key, item) -> hashes.put(String.valueOf(key), text(item)));
            return Map.copyOf(hashes);
        }
        if (value instanceof String json && !json.trim().isEmpty()) {
            try {
                Map<String, Object> parsed = CanonicalJson.parseObject(json);
                return artifactHashes(parsed, fallback);
            } catch (Exception ignored) {
                // Legacy rows are reconstructed from immutable Markdown content.
            }
        }
        return fallback == null ? Map.of() : Map.copyOf(fallback);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text(value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("生成 Skill manifest hash 失败", e);
        }
    }

    private IllegalStateException notFound(Identity identity) {
        return new IllegalStateException(
                "Skill 版本不存在或 hash 不匹配：" + identity.skillId() + "@" + identity.version());
    }

    private String normalizeSkillId(String value) {
        String normalized = SkillCatalogFingerprint.normalizeId(required(value, "SKILL_ID_REQUIRED"));
        if (normalized.isBlank()) throw new IllegalArgumentException("SKILL_ID_REQUIRED");
        return normalized;
    }

    private Object first(Object left, Object right) {
        return left == null || text(left).isBlank() ? right : left;
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

    private String required(String value, String reasonCode) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private String fallback(Object value, String fallback) {
        String normalized = text(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private String rawText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private record Identity(String scope,
                            String projectId,
                            String skillId,
                            int version,
                            String skillHash) {
        SkillPackageKey key() {
            return new SkillPackageKey(scope, projectId, skillId, version);
        }
    }
}
