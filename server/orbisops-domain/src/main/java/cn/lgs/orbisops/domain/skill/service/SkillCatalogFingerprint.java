package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.shared.json.CanonicalJson;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.TreeMap;

/** Canonical fingerprint for the mutable Skill catalog current pointer. */
public final class SkillCatalogFingerprint {

    private static final Set<String> STATUSES = Set.of(
            "DRAFT", "ACTIVE", "PAUSED", "FROZEN", "DEPRECATED", "REJECTED", "ENABLED", "DISABLED");
    private static final Set<String> UPDATE_MODES = Set.of("AUTO", "MANUAL_ONLY", "FROZEN");

    private SkillCatalogFingerprint() {
    }

    public static String sha256(String scope,
                                String projectId,
                                String skillId,
                                String name,
                                String description,
                                String content,
                                int version,
                                String status,
                                String updateMode,
                                boolean autoUpdateEnabled,
                                boolean autoMergeEnabled) {
        try {
            TreeMap<String, Object> canonical = new TreeMap<>();
            canonical.put("scope", text(scope, "GLOBAL"));
            canonical.put("projectId", text(projectId, ""));
            canonical.put("skillId", normalizeId(skillId));
            canonical.put("name", text(name, ""));
            canonical.put("description", text(description, ""));
            canonical.put("content", text(content, ""));
            canonical.put("version", Math.max(1, version));
            canonical.put("status", normalizeStatus(status));
            canonical.put("updateMode", normalizeUpdateMode(updateMode));
            canonical.put("autoUpdateEnabled", autoUpdateEnabled);
            canonical.put("autoMergeEnabled", autoMergeEnabled);
            byte[] bytes = CanonicalJson.stringify(canonical).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException("生成 Skill hash 失败：" + e.getMessage(), e);
        }
    }

    public static String normalizeId(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        String normalized = value.trim().toLowerCase().replaceAll("[^a-z0-9_\\-]+", "-");
        normalized = normalized.replaceAll("-+", "-").replaceAll("(^-|-$)", "");
        return normalized;
    }

    private static String normalizeStatus(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        return STATUSES.contains(normalized) ? normalized : "ENABLED";
    }

    private static String normalizeUpdateMode(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        return UPDATE_MODES.contains(normalized) ? normalized : "AUTO";
    }

    private static String text(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? fallback : normalized;
    }
}
