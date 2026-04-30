package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.skill.model.SkillCatalogEntry;
import cn.lgs.orbisops.domain.skill.service.SkillPackageManifest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Maps typed catalog entries to the legacy read model returned by existing APIs. */
public final class SkillCatalogViewMapper {

    private static final String GLOBAL = "GLOBAL";
    private static final String STATUS_ENABLED = "ENABLED";
    private static final String ORIGIN_MANUAL = "MANUAL";
    private static final String UPDATE_MODE_AUTO = "AUTO";
    private static final Set<String> UPDATE_MODES = Set.of("AUTO", "MANUAL_ONLY", "LOCKED", "SEALED", "FROZEN");

    public Map<String, Object> toView(SkillCatalogEntry entry, boolean includeContent) {
        if (entry == null) throw new IllegalArgumentException("SKILL_CATALOG_ENTRY_REQUIRED");
        SkillPackageManifest.Descriptor skillPackage = SkillPackageManifest.markdown(
                entry.scope(), entry.projectId(), entry.skillId(), entry.name(), entry.description(),
                entry.version(), entry.content());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", entry.id());
        data.put("skillId", entry.skillId());
        data.put("name", entry.name());
        data.put("skillName", entry.name());
        data.put("scope", entry.scope());
        data.put("projectId", entry.projectId());
        data.put("sourceGlobalSkillId", entry.sourceGlobalSkillId());
        data.put("description", entry.description());
        data.put("version", entry.version());
        data.put("currentVersion", entry.currentVersion());
        data.put("status", fallback(entry.status(), STATUS_ENABLED));
        data.put("sourceType", "DB");
        data.put("origin", fallback(entry.origin(), ORIGIN_MANUAL));
        data.put("updateMode", normalizeUpdateMode(entry.updateMode()));
        data.put("lifecycleStatus", entry.governanceState().lifecycleStatus().name());
        data.put("mutationMode", entry.governanceState().mutationMode().name());
        data.put("executionMode", entry.governanceState().executionMode().name());
        data.put("bindingMode", entry.governanceState().bindingMode().name());
        data.put("lockType", entry.governanceState().lock().type().name());
        data.put("lockReason", entry.governanceState().lock().reason());
        data.put("lockActor", entry.governanceState().lock().actor());
        data.put("lockApprovalId", entry.governanceState().lock().approvalId());
        data.put("lockAt", string(entry.governanceState().lock().lockedAt()));
        data.put("legacyFrozenClassificationRequired",
                entry.governanceState().legacyFrozenClassificationRequired());
        data.put("autoUpdateEnabled", entry.autoUpdateEnabled());
        data.put("autoMergeEnabled", entry.autoMergeEnabled());
        data.put("lastEvolvedAt", string(entry.lastEvolvedAt()));
        data.put("frozenReason", text(entry.frozenReason()));
        data.put("frozenBy", text(entry.frozenBy()));
        data.put("frozenAt", string(entry.frozenAt()));
        data.put("skillHash", text(entry.skillHash()));
        data.put("currentSkillHash", fallback(entry.currentSkillHash(), text(entry.skillHash())));
        data.put("versionSeq", entry.versionSeq());
        data.put("currentPackageHash", fallback(entry.currentPackageHash(), skillPackage.packageHash()));
        data.put("packageHash", data.get("currentPackageHash"));
        String manifestJson = fallback(entry.packageManifestJson(), skillPackage.manifestJson());
        data.put("packageManifestJson", manifestJson);
        Map<String, Object> routingProfile =
                SkillPackageManifest.routingProfile(manifestJson);
        data.put("category", routingProfile.getOrDefault("category", ""));
        data.put("subcategory", routingProfile.getOrDefault("subcategory", ""));
        data.put("whenToUse", routingProfile.getOrDefault("whenToUse", java.util.List.of()));
        data.put("whenNotToUse", routingProfile.getOrDefault("whenNotToUse", java.util.List.of()));
        data.put("keywords", routingProfile.getOrDefault("keywords", java.util.List.of()));
        data.put("manifestHash", sha256(manifestJson));
        data.put("artifactHashes", artifactHashes(entry.artifactHashesJson(), skillPackage.artifactHashes()));
        data.put("entrypoint", skillPackage.entrypoint());
        data.put("artifactCount", skillPackage.artifactCount());
        data.put("packageSize", skillPackage.packageSize());
        data.put("createBy", entry.createBy());
        data.put("createTime", string(entry.createTime()));
        data.put("updateTime", string(entry.updateTime()));
        data.put("contentLength", entry.content() == null ? 0 : entry.content().length());
        data.put("frontMatter", Map.of(
                "name", fallback(entry.name(), ""),
                "description", fallback(entry.description(), ""),
                "scope", fallback(entry.scope(), GLOBAL),
                "projectId", fallback(entry.projectId(), "")));
        if (includeContent) {
            data.put("content", rawText(entry.content()));
            data.put("markdown", toMarkdown(entry));
        }
        return data;
    }

    private String toMarkdown(SkillCatalogEntry entry) {
        return """
                ---
                name: %s
                description: %s
                scope: %s
                projectId: %s
                ---

                %s
                """.formatted(
                fallback(entry.name(), ""),
                fallback(entry.description(), ""),
                fallback(entry.scope(), GLOBAL),
                fallback(entry.projectId(), ""),
                text(entry.content()).strip());
    }

    private String normalizeUpdateMode(String value) {
        String normalized = fallback(value, UPDATE_MODE_AUTO).toUpperCase();
        return UPDATE_MODES.contains(normalized) ? normalized : UPDATE_MODE_AUTO;
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
                return artifactHashes(CanonicalJson.parseObject(json), fallback);
            } catch (Exception ignored) {
                // Legacy rows use the descriptor reconstructed from immutable Markdown content.
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

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String fallback(Object value, String fallback) {
        String normalized = text(value);
        return normalized.isEmpty() ? fallback : normalized;
    }

    private String rawText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
