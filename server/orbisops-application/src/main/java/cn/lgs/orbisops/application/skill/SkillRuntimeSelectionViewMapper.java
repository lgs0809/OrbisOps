package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillRuntimeSelection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Maps authoritative Skill selection results into runtime catalog and bound-reference views. */
public final class SkillRuntimeSelectionViewMapper {

    private final SkillBoundReferenceMapper boundReferences;

    public SkillRuntimeSelectionViewMapper(
            SkillBoundReferenceMapper boundReferences) {
        if (boundReferences == null) {
            throw new IllegalArgumentException("SKILL_BOUND_REFERENCE_MAPPER_REQUIRED");
        }
        this.boundReferences = boundReferences;
    }

    public SelectRuntimeSkillsQuery.Result result(
            SkillRuntimeSelection selection) {
        if (selection == null) {
            throw new IllegalStateException("SKILL_RUNTIME_SELECTION_REQUIRED");
        }
        List<Map<String, Object>> catalogRefs = selection.catalog().stream()
                .map(this::runtimeCatalogRef)
                .toList();
        List<Map<String, Object>> selectedRefs = selection.selected().stream()
                .map(boundReferences::runtimeVersionRef)
                .toList();
        List<Map<String, Object>> suppressedRefs = selection.suppressed().stream()
                .map(item -> suppressedRef(
                        item.rankedSkill(),
                        item.suppressedBySkillId(),
                        item.reasonCode()))
                .toList();
        return new SelectRuntimeSkillsQuery.Result(
                catalogRefs,
                selectedRefs,
                suppressedRefs,
                selection.activeCount(),
                catalogRefs.size(),
                selectedRefs.size());
    }

    public SelectRuntimeSkillsQuery.Result frozenResult(
            SkillRuntimeSelection selection) {
        var mapped=result(selection);
        return new SelectRuntimeSkillsQuery.Result(mapped.catalogRefs(),selection.selected().stream()
                .map(boundReferences::frozenCatalogRef).toList(),mapped.suppressedRefs(),
                mapped.activeCount(),mapped.catalogCount(),mapped.selectedCount());
    }

    public SelectRuntimeSkillsQuery.Result frozenResult(
            List<Map<String, Object>> catalogRefs,
            List<SkillRuntimeSelection.RankedSkill> ranked) {
        List<Map<String, Object>> selected = ranked == null
                ? List.of()
                : ranked.stream().map(boundReferences::frozenCatalogRef).toList();
        List<Map<String, Object>> safeCatalog = catalogRefs == null ? List.of() : catalogRefs;
        return new SelectRuntimeSkillsQuery.Result(
                safeCatalog,
                selected,
                List.of(),
                safeCatalog.size(),
                safeCatalog.size(),
                selected.size());
    }

    private Map<String, Object> runtimeCatalogRef(
            SkillRuntimeSelection.RankedSkill ranked) {
        Map<String, Object> ref = new LinkedHashMap<>(
                boundReferences.runtimeVersionRef(ranked));
        ref.put("name", ranked.candidate().name());
        ref.put("description", abbreviate(ranked.candidate().description(), 300));
        ref.put("category", ranked.candidate().routingProfile().category());
        ref.put("subcategory", ranked.candidate().routingProfile().subcategory());
        ref.put("whenToUse", ranked.candidate().routingProfile().useCases());
        ref.put("whenNotToUse", ranked.candidate().routingProfile().exclusions());
        ref.put("keywords", ranked.candidate().routingProfile().keywords());
        ref.put("contentLength", ranked.candidate().contentLength());
        return Map.copyOf(ref);
    }

    private Map<String, Object> suppressedRef(
            SkillRuntimeSelection.RankedSkill ranked,
            String suppressedBySkillId,
            String reasonCode) {
        Map<String, Object> ref = new LinkedHashMap<>(
                boundReferences.runtimeVersionRef(ranked));
        ref.put("suppressedBySkillId", suppressedBySkillId);
        ref.put("reasonCode", reasonCode);
        return Map.copyOf(ref);
    }

    private String abbreviate(String value, int max) {
        String normalized = value == null ? "" : value;
        return normalized.length() <= max
                ? normalized
                : normalized.substring(0, max) + "...";
    }
}
