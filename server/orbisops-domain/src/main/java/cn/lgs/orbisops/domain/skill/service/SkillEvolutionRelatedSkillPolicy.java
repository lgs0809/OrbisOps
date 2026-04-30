package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.domain.skill.model.SkillCatalogEntry;
import java.util.*;

/** Identity of the catalog facts used to prepare a bounded authoring proposal. */
public final class SkillEvolutionRelatedSkillPolicy {
    public static final int MAX_RELATED_SKILLS = 5;
    public static final String VERSION = "related-skills-v1";

    public static String databaseFence(SkillCatalogEntry entry) {
        var facts = new LinkedHashMap<String,Object>();
        facts.put("skillId",entry.skillId()); facts.put("scope",entry.scope()); facts.put("projectId",entry.projectId());
        facts.put("name",entry.name()); facts.put("description",entry.description());
        facts.put("version",entry.currentVersion()); facts.put("versionSeq",entry.versionSeq());
        facts.put("skillHash",entry.currentSkillHash()); facts.put("packageHash",entry.currentPackageHash());
        facts.put("manifest",entry.packageManifestJson()); facts.put("artifacts",entry.artifactHashesJson());
        facts.put("status",entry.status()); facts.put("updateMode",entry.updateMode());
        facts.put("autoUpdate",entry.autoUpdateEnabled()); facts.put("autoMerge",entry.autoMergeEnabled());
        facts.put("governance",entry.governanceState().toString());
        return CanonicalObjectHasher.sha256(facts);
    }

    public static List<Map<String,Object>> references(Object value, String projectId) {
        if (!(value instanceof List<?> list) || list.size()>MAX_RELATED_SKILLS)
            throw new IllegalStateException("SKILL_EVOLUTION_RELATED_SKILL_SET_INVALID");
        var result = new ArrayList<Map<String,Object>>(); var identities = new HashSet<String>();
        for (Object item : list) {
            if (!(item instanceof Map<?,?> raw)) throw new IllegalStateException("SKILL_EVOLUTION_RELATED_SKILL_SET_INVALID");
            var skill = new LinkedHashMap<String,Object>(); raw.forEach((key,v)->skill.put(String.valueOf(key),v));
            String scope=text(skill.get("scope")), project=text(skill.get("projectId")), id=text(skill.get("skillId"));
            if (id.isBlank() || !("GLOBAL".equals(scope)&&project.isBlank() || "PROJECT".equals(scope)&&projectId.equals(project))
                    || !identities.add(scope+":"+id) || !Set.of("DB","FILE").contains(text(skill.get("sourceType")))
                    || !(skill.get("currentVersion") instanceof Number version) || version.intValue()<1
                    || !(skill.get("content") instanceof String) || !(skill.get("relatedArtifacts") instanceof List<?>)
                    || !text(skill.get("catalogFence")).matches("[0-9a-f]{64}")
                    || !text(skill.get("currentSkillHash")).matches("[0-9a-f]{64}")
                    || !text(skill.get("currentPackageHash")).matches("[0-9a-f]{64}"))
                throw new IllegalStateException("SKILL_EVOLUTION_RELATED_SKILL_SET_INVALID");
            result.add(Collections.unmodifiableMap(skill));
        }
        return List.copyOf(result);
    }

    private static String text(Object value) { return value==null?"":String.valueOf(value).trim(); }
}
