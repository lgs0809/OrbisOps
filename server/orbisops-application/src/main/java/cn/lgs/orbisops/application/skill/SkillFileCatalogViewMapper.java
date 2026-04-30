package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.service.SkillCatalogFingerprint;
import cn.lgs.orbisops.domain.skill.service.SkillPackageManifest;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Maps file-backed Skill snapshots to the existing catalog read model. */
public final class SkillFileCatalogViewMapper {

    private static final String GLOBAL = "GLOBAL";
    private static final String ENABLED = "ENABLED";
    private static final String MANUAL_ONLY = "MANUAL_ONLY";

    public Map<String, Object> toView(SkillFileDefinition skill, boolean includeContent) {
        if (skill == null) throw new IllegalArgumentException("SKILL_FILE_DEFINITION_REQUIRED");
        Map<String, Object> frontMatter = skill.frontMatter();
        String scope = fallback(frontMatter.get("scope"), GLOBAL).toUpperCase(Locale.ROOT);
        String projectId = text(firstNonNull(frontMatter.get("projectId"), frontMatter.get("project_id")));
        String description = text(frontMatter.get("description"));
        String skillHash = SkillCatalogFingerprint.sha256(
                scope, projectId, skill.name(), skill.name(), description, skill.content(),
                1, ENABLED, MANUAL_ONLY, false, false);
        SkillPackageManifest.Descriptor skillPackage = SkillPackageManifest.markdown(
                scope, projectId, skill.name(), skill.name(), description, 1, text(skill.content()));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("skillId", skill.name());
        data.put("name", skill.name());
        data.put("skillName", skill.name());
        data.put("scope", scope);
        data.put("projectId", projectId);
        data.put("description", description);
        data.put("version", 1);
        data.put("currentVersion", 1);
        data.put("status", ENABLED);
        data.put("sourceType", "FILE");
        data.put("origin", "IMPORTED");
        data.put("updateMode", MANUAL_ONLY);
        data.put("autoUpdateEnabled", false);
        data.put("autoMergeEnabled", false);
        data.put("lastEvolvedAt", "");
        data.put("frozenReason", "");
        data.put("frozenBy", "");
        data.put("frozenAt", "");
        data.put("skillHash", skillHash);
        data.put("currentSkillHash", skillHash);
        data.put("versionSeq", 1);
        data.put("currentPackageHash", skillPackage.packageHash());
        data.put("packageHash", skillPackage.packageHash());
        data.put("packageManifestJson", skillPackage.manifestJson());
        data.put("manifestHash", skillPackage.manifestHash());
        data.put("artifactHashes", skillPackage.artifactHashes());
        data.put("entrypoint", skillPackage.entrypoint());
        data.put("artifactCount", skillPackage.artifactCount());
        data.put("packageSize", skillPackage.packageSize());
        data.put("basePath", skill.basePath());
        data.put("frontMatter", frontMatter);
        data.put("contentLength", skill.content().length());
        if (includeContent) {
            data.put("content", skill.content());
            data.put("markdown", skill.markdown().isEmpty() ? skill.content() : skill.markdown());
            data.put("xml", skill.xml());
        }
        return data;
    }

    private Object firstNonNull(Object left, Object right) {
        return left == null ? right : left;
    }

    private String fallback(Object value, String fallback) {
        String normalized = text(value);
        return normalized.isEmpty() ? fallback : normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
