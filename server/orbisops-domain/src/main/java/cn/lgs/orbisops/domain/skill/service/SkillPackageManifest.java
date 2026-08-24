package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.skill.model.SkillArtifact;
import cn.lgs.orbisops.domain.skill.model.SkillRoutingProfile;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Canonical Skill Package descriptor for SKILL.md and its versioned bundled resources. */
public final class SkillPackageManifest {

    public static final String FORMAT = "SKILL_PACKAGE_V3";
    public static final String ENTRYPOINT = "SKILL.md";
    public static final String ENCODING_UTF8 = SkillPackageArtifactPolicy.ENCODING_UTF8;
    public static final String ENCODING_BASE64 = SkillPackageArtifactPolicy.ENCODING_BASE64;
    private static final Set<String> ALLOWED_DEPENDENCY_TYPES = Set.of("SKILL", "TOOLSET", "BUILTIN");

    private SkillPackageManifest() {
    }

    public static Descriptor markdown(String scope,
                                      String projectId,
                                      String skillId,
                                      String name,
                                      String description,
                                      int version,
                                      String content) {
        return packageOf(scope, projectId, skillId, name, description, version, content,
                List.of(), List.of(), List.of(), Limits.defaults());
    }

    public static Descriptor rebuild(String scope,
                                     String projectId,
                                     String skillId,
                                     String name,
                                     String description,
                                     int version,
                                     String entrypointContent,
                                     List<SkillArtifact> versionArtifacts,
                                     String sourceManifestJson,
                                     Limits limits) {
        List<Map<String, Object>> artifacts = artifactInputs(versionArtifacts);
        Map<String, Object> sourceManifest = manifest(sourceManifestJson);
        Map<String, Object> routing = map(first(sourceManifest, "routingProfile", "routing"));
        // Restore the historical retrieval boundary, without inferring new rules from today's description.
        SkillRoutingProfile sourceRouting = routing.isEmpty() ? null : new SkillRoutingProfilePolicy()
                .requireCanonicalProfile(text(routing.get("category"), ""),
                        text(first(routing, "subcategory", "subCategory"), ""), "", "",
                        first(routing, "whenToUse", "useCases"), first(routing, "whenNotToUse", "exclusions"),
                        first(routing, "keywords", "capabilityHints"));
        return packageOf(scope, projectId, skillId, name, description, version, entrypointContent,
                artifacts,
                sourceManifest.getOrDefault("dependencies", List.of()),
                sourceManifest.getOrDefault("evalSuites", List.of()),
                sourceRouting,
                limits);
    }

    public static Descriptor create(String scope,
                                    String projectId,
                                    String skillId,
                                    String name,
                                    String description,
                                    int version,
                                    String entrypointContent,
                                    Map<String, Object> command,
                                    Limits limits) {
        Map<String, Object> source = command == null ? Map.of() : command;
        SkillRoutingProfile routingProfile = new SkillRoutingProfilePolicy()
                .requireProfile(
                        text(source.get("category"), ""),
                        text(first(source, "subcategory", "subCategory"), ""),
                        name,
                        description,
                        entrypointContent,
                        first(source, "whenToUse", "useCases"),
                        first(source, "whenNotToUse", "exclusions"),
                        first(source, "keywords", "capabilityHints"));
        return evolve(scope, projectId, skillId, name, description, version, entrypointContent,
                command, List.of(), "", routingProfile, limits);
    }

    public static Descriptor evolve(String scope,
                                    String projectId,
                                    String skillId,
                                    String name,
                                    String description,
                                    int version,
                                    String entrypointContent,
                                    Map<String, Object> mutation,
                                    List<SkillArtifact> currentArtifacts,
                                    String currentManifestJson,
                                    Limits limits) {
        Map<String, Object> currentManifest = manifest(currentManifestJson);
        Map<String, Object> routing = map(
                first(currentManifest, "routingProfile", "routing"));
        Map<String, Object> source = mutation == null ? Map.of() : mutation;
        SkillRoutingProfile routingProfile = new SkillRoutingProfilePolicy()
                .requireProfile(
                        text(firstPresent(source, routing, "category"), ""),
                        text(firstPresent(source, routing, "subcategory", "subCategory"), ""),
                        name,
                        description,
                        entrypointContent,
                        firstPresent(source, routing, "whenToUse", "useCases"),
                        firstPresent(source, routing, "whenNotToUse", "exclusions"),
                        firstPresent(source, routing, "keywords", "capabilityHints"));
        return evolve(scope, projectId, skillId, name, description, version, entrypointContent,
                mutation, currentArtifacts, currentManifestJson, routingProfile, limits);
    }

    private static Descriptor evolve(String scope,
                                     String projectId,
                                     String skillId,
                                     String name,
                                     String description,
                                     int version,
                                     String entrypointContent,
                                     Map<String, Object> mutation,
                                     List<SkillArtifact> currentArtifacts,
                                     String currentManifestJson,
                                     SkillRoutingProfile routingProfile,
                                     Limits limits) {
        Map<String, Object> source = mutation == null ? Map.of() : mutation;
        Object artifacts = mutationArtifacts(source, currentArtifacts);
        Map<String, Object> currentManifest = manifest(currentManifestJson);
        Object dependencies = source.containsKey("dependencies")
                ? source.get("dependencies") : currentManifest.getOrDefault("dependencies", List.of());
        Object evalSuites = source.containsKey("evalSuites")
                ? source.get("evalSuites") : currentManifest.getOrDefault("evalSuites", List.of());
        return packageOf(scope, projectId, skillId, name, description, version, entrypointContent,
                artifacts, dependencies, evalSuites, routingProfile, limits);
    }

    public static Descriptor packageOf(String scope,
                                       String projectId,
                                       String skillId,
                                       String name,
                                       String description,
                                       int version,
                                       String entrypointContent,
                                       Object artifactInput,
                                       Object dependencyInput,
                                       Object evalSuiteInput,
                                       Limits limits) {
        return packageOf(
                scope,
                projectId,
                skillId,
                name,
                description,
                version,
                entrypointContent,
                artifactInput,
                dependencyInput,
                evalSuiteInput,
                null,
                limits);
    }

    private static Descriptor packageOf(String scope,
                                        String projectId,
                                        String skillId,
                                        String name,
                                        String description,
                                        int version,
                                        String entrypointContent,
                                        Object artifactInput,
                                        Object dependencyInput,
                                        Object evalSuiteInput,
                                        SkillRoutingProfile routingProfile,
                                        Limits limits) {
        Limits safeLimits = limits == null ? Limits.defaults() : limits;
        LinkedHashMap<String, ArtifactContent> artifacts = new LinkedHashMap<>();
        addArtifact(artifacts, ENTRYPOINT, "ENTRYPOINT", "text/markdown; charset=utf-8",
                ENCODING_UTF8, entrypointContent == null ? "" : entrypointContent, false, safeLimits);
        if (artifactInput instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (!(item instanceof Map<?, ?> map)) {
                    throw new IllegalArgumentException("SKILL_PACKAGE_ARTIFACT_INVALID：artifact 必须是对象");
                }
                String path = normalizeArtifactPath(first(map, "path", "name"));
                if (ENTRYPOINT.equalsIgnoreCase(path)) {
                    throw new IllegalArgumentException("SKILL_PACKAGE_ENTRYPOINT_DUPLICATE：SKILL.md 由 content 字段管理");
                }
                boolean executable = bool(map.get("executable"));
                String role = SkillPackageArtifactPolicy.normalizeRole(first(map, "role", "type"));
                String mediaType = text(map.get("mediaType"),
                        SkillPackageArtifactPolicy.inferMediaType(path));
                String encoding = SkillPackageArtifactPolicy.normalizeEncoding(
                        first(map, "encoding", "contentEncoding"), path);
                Object suppliedContent = ENCODING_BASE64.equals(encoding)
                        ? first(map, "contentBase64", "content") : map.get("content");
                String content = rawText(suppliedContent);
                addArtifact(artifacts, path, role, mediaType, encoding, content, executable, safeLimits);
            }
        }
        if (artifacts.size() > safeLimits.maxArtifacts()) {
            throw new IllegalArgumentException("SKILL_PACKAGE_TOO_MANY_ARTIFACTS：max=" + safeLimits.maxArtifacts());
        }
        long totalSize = artifacts.values().stream().mapToLong(ArtifactContent::sizeBytes).sum();
        if (totalSize > safeLimits.maxPackageBytes()) {
            throw new IllegalArgumentException("SKILL_PACKAGE_TOO_LARGE：maxBytes=" + safeLimits.maxPackageBytes());
        }

        List<Map<String, Object>> dependencies = dependencies(dependencyInput);
        List<String> evalSuites = evalSuites(evalSuiteInput, artifacts.keySet());
        Map<String, String> artifactHashes = new TreeMap<>();
        List<Map<String, Object>> artifactManifest = new ArrayList<>();
        for (ArtifactContent artifact : artifacts.values()) {
            artifactHashes.put(artifact.path(), artifact.contentHash());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("path", artifact.path());
            item.put("type", artifact.role());
            item.put("mediaType", artifact.mediaType());
            item.put("encoding", artifact.encoding());
            item.put("contentHash", artifact.contentHash());
            item.put("sizeBytes", artifact.sizeBytes());
            item.put("executable", false);
            item.put("sensitivity", "INTERNAL");
            artifactManifest.add(item);
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("scope", text(scope, ""));
        metadata.put("projectId", text(projectId, ""));
        metadata.put("skillId", text(skillId, ""));
        metadata.put("name", text(name, ""));
        metadata.put("description", text(description, ""));
        metadata.put("version", Math.max(1, version));

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("apiVersion", "orbisops/v1");
        manifest.put("kind", "SkillPackage");
        manifest.put("format", FORMAT);
        manifest.put("metadata", metadata);
        if (routingProfile != null) {
            manifest.put(
                    "routingProfile",
                    new SkillRoutingProfilePolicy().toMap(routingProfile));
        }
        manifest.put("entrypoint", ENTRYPOINT);
        manifest.put("artifacts", artifactManifest);
        manifest.put("dependencies", dependencies);
        manifest.put("permissionRequirements", List.of());
        manifest.put("evalSuites", evalSuites);
        manifest.put("securityScan", Map.of("status", "PASSED", "policyVersion", "SKILL_PACKAGE_STATIC_V1"));

        String manifestJson = CanonicalJson.stringify(canonicalize(manifest));
        String manifestHash = sha256(manifestJson.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> packageIdentity = new LinkedHashMap<>();
        packageIdentity.put("manifestHash", manifestHash);
        packageIdentity.put("artifactHashes", artifactHashes);
        String packageHash = sha256(CanonicalJson.stringify(canonicalize(packageIdentity)).getBytes(StandardCharsets.UTF_8));
        return new Descriptor(manifestJson, manifestHash, packageHash, Map.copyOf(artifactHashes),
                ENTRYPOINT, artifacts.size(), totalSize, Map.copyOf(artifacts), List.copyOf(dependencies),
                List.copyOf(evalSuites));
    }

    private static void addArtifact(Map<String, ArtifactContent> artifacts,
                                    String path,
                                    String role,
                                    String mediaType,
                                    String encoding,
                                    String content,
                                    boolean executable,
                                    Limits limits) {
        SkillPackageArtifactPolicy.ValidatedArtifact validated = SkillPackageArtifactPolicy.validate(
                path, role, mediaType, encoding, content, executable, limits.maxArtifactBytes());
        if (artifacts.containsKey(validated.path())) {
            throw new IllegalArgumentException("SKILL_PACKAGE_ARTIFACT_DUPLICATE：" + validated.path());
        }
        artifacts.put(validated.path(), new ArtifactContent(
                validated.path(), validated.role(), validated.mediaType(), validated.encoding(),
                validated.content(), validated.contentHash(), validated.sizeBytes()));
    }

    private static List<Map<String, Object>> dependencies(Object input) {
        if (!(input instanceof Iterable<?> iterable)) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> unique = new LinkedHashSet<>();
        for (Object item : iterable) {
            String type;
            String id;
            String version;
            if (item instanceof Map<?, ?> map) {
                type = text(map.get("type"), "").toUpperCase(Locale.ROOT);
                id = text(first(map, "id", "skillId", "toolsetId"), "");
                version = text(map.get("version"), "");
                if (map.containsKey("url") || map.containsKey("uri") || map.containsKey("path")) {
                    throw new IllegalArgumentException("SKILL_PACKAGE_EXTERNAL_DEPENDENCY_FORBIDDEN");
                }
            } else {
                String value = text(item, "");
                int colon = value.indexOf(':');
                type = colon > 0 ? value.substring(0, colon).toUpperCase(Locale.ROOT) : "";
                String tail = colon > 0 ? value.substring(colon + 1) : "";
                int at = tail.lastIndexOf('@');
                id = at > 0 ? tail.substring(0, at) : tail;
                version = at > 0 ? tail.substring(at + 1) : "";
            }
            if (!ALLOWED_DEPENDENCY_TYPES.contains(type) || !safeIdentifier(id)) {
                throw new IllegalArgumentException("SKILL_PACKAGE_DEPENDENCY_NOT_ALLOWED：" + type + ":" + id);
            }
            String key = type + ":" + id + "@" + version;
            if (unique.add(key)) {
                result.add(Map.of("type", type, "id", id, "version", version));
            }
        }
        return result;
    }

    private static List<String> evalSuites(Object input, Set<String> artifacts) {
        if (!(input instanceof Iterable<?> iterable)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object item : iterable) {
            String path = normalizeArtifactPath(item);
            if (!artifacts.contains(path)) {
                throw new IllegalArgumentException("SKILL_PACKAGE_EVAL_ARTIFACT_MISSING：" + path);
            }
            result.add(path);
        }
        return result.stream().distinct().sorted().toList();
    }

    private static Object mutationArtifacts(Map<String, Object> source,
                                            List<SkillArtifact> currentArtifacts) {
        if (!source.containsKey("artifacts")) return artifactInputs(currentArtifacts);
        Object value = source.get("artifacts");
        if (!(value instanceof Iterable<?> iterable)) {
            throw new IllegalArgumentException("Skill artifacts 必须是文件列表");
        }
        List<Object> items = new ArrayList<>();
        iterable.forEach(items::add);
        if (items.isEmpty()) return List.of();
        boolean containsFileContents = items.stream()
                .allMatch(item -> item instanceof Map<?, ?> map
                        && (map.containsKey("content") || map.containsKey("contentBase64")));
        return containsFileContents ? items : artifactInputs(currentArtifacts);
    }

    private static List<Map<String, Object>> artifactInputs(List<SkillArtifact> artifacts) {
        if (artifacts == null || artifacts.isEmpty()) return List.of();
        return artifacts.stream()
                .filter(artifact -> artifact != null && !ENTRYPOINT.equals(artifact.path()))
                .map(artifact -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("path", artifact.path());
                    item.put("role", artifact.role());
                    item.put("mediaType", artifact.mediaType());
                    item.put("encoding", artifact.encoding());
                    item.put("content", artifact.content());
                    item.put("executable", false);
                    return item;
                })
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> manifest(String json) {
        if (json == null || json.trim().isEmpty()) return Map.of();
        try {
            Map<String, Object> parsed = CanonicalJson.parseObject(json);
            return parsed == null ? Map.of() : parsed;
        } catch (Exception e) {
            throw new IllegalArgumentException("Skill Package manifest JSON 无效", e);
        }
    }

    public static Map<String, Object> routingProfile(String manifestJson) {
        Map<String, Object> source = manifest(manifestJson);
        return Map.copyOf(map(first(source, "routingProfile", "routing")));
    }

    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private static Object firstPresent(Map<String, Object> primary,
                                       Map<String, Object> fallback,
                                       String... keys) {
        for (String key : keys) {
            if (primary.containsKey(key) && primary.get(key) != null) {
                return primary.get(key);
            }
        }
        for (String key : keys) {
            if (fallback.containsKey(key) && fallback.get(key) != null) {
                return fallback.get(key);
            }
        }
        return null;
    }

    public static String normalizeArtifactPath(Object value) {
        return SkillPackageArtifactPolicy.normalizePath(value);
    }

    private static boolean safeIdentifier(String value) {
        return !value.isBlank() && value.length() <= 160 && value.matches("[A-Za-z0-9._:/-]+");
    }

    private static Object first(Map<?, ?> map, String... keys) {
        for (String key : keys) if (map.get(key) != null) return map.get(key);
        return null;
    }

    private static boolean bool(Object value) {
        return value instanceof Boolean b ? b : Boolean.parseBoolean(text(value, "false"));
    }

    private static Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, item) -> sorted.put(String.valueOf(key), canonicalize(item)));
            return sorted;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> items = new ArrayList<>();
            iterable.forEach(item -> items.add(canonicalize(item)));
            if (items.stream().allMatch(item -> item instanceof Map<?, ?> map && map.containsKey("path"))) {
                items.sort(Comparator.comparing(item -> String.valueOf(((Map<?, ?>) item).get("path"))));
            }
            return items;
        }
        return value == null ? "" : value;
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception e) {
            throw new IllegalStateException("计算 Skill Package hash 失败", e);
        }
    }

    private static String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    private static String rawText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public record Limits(int maxArtifacts, long maxArtifactBytes, long maxPackageBytes) {
        public Limits {
            if (maxArtifacts < 1 || maxArtifactBytes < 1 || maxPackageBytes < maxArtifactBytes) {
                throw new IllegalArgumentException("Skill Package limits 无效");
            }
        }

        public static Limits defaults() {
            return new Limits(32, 256L * 1024L, 2L * 1024L * 1024L);
        }
    }

    public record ArtifactContent(String path,
                                  String role,
                                  String mediaType,
                                  String encoding,
                                  String content,
                                  String contentHash,
                                  long sizeBytes) {
    }

    public record Descriptor(String manifestJson,
                             String manifestHash,
                             String packageHash,
                             Map<String, String> artifactHashes,
                             String entrypoint,
                             int artifactCount,
                             long packageSize,
                             Map<String, ArtifactContent> artifacts,
                             List<Map<String, Object>> dependencies,
                             List<String> evalSuites) {
    }
}
