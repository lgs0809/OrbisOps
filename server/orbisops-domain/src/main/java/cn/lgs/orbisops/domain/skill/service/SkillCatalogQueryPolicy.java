package cn.lgs.orbisops.domain.skill.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Pure query policy for catalog source precedence, stable ordering and file scope matching. */
public final class SkillCatalogQueryPolicy {

    private static final String GLOBAL = "GLOBAL";
    private static final String PROJECT = "PROJECT";

    public <T> List<T> mergeDatabaseFirst(List<T> databaseEntries,
                                          List<T> fileEntries,
                                          Function<T, String> skillIdExtractor) {
        if (skillIdExtractor == null) throw new IllegalArgumentException("SKILL_ID_EXTRACTOR_REQUIRED");
        Map<String, T> merged = new LinkedHashMap<>();
        for (T item : safe(databaseEntries)) {
            String skillId = skillId(item, skillIdExtractor);
            if (!skillId.isEmpty()) merged.put(skillId, item);
        }
        for (T item : safe(fileEntries)) {
            String skillId = skillId(item, skillIdExtractor);
            if (!skillId.isEmpty()) merged.putIfAbsent(skillId, item);
        }
        return new ArrayList<>(merged.values());
    }

    public boolean isGlobalFileSkill(String scope, String projectId) {
        return GLOBAL.equalsIgnoreCase(text(scope)) && text(projectId).isEmpty();
    }

    public boolean isProjectFileSkill(String scope, String projectId, String expectedProjectId) {
        String expected = text(expectedProjectId);
        return PROJECT.equalsIgnoreCase(text(scope))
                && !expected.isEmpty()
                && expected.equals(text(projectId));
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private <T> String skillId(T item, Function<T, String> extractor) {
        if (item == null) return "";
        return text(extractor.apply(item));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
