package cn.lgs.orbisops.trigger.ops.skill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Typed Skill catalog locations and editable workspace policy. */
public record OpsSkillToolSettings(
        boolean enabled,
        List<String> configuredLocations,
        String editableLocation,
        boolean editableAutoInit) {

    public OpsSkillToolSettings {
        configuredLocations = immutableLocations(configuredLocations);
        editableLocation = editableLocation == null ? "" : editableLocation.trim();
    }

    public static OpsSkillToolSettings fromRaw(
            boolean enabled,
            String locations,
            String editableLocation,
            boolean editableAutoInit) {
        List<String> parsed = new ArrayList<>();
        if (locations != null && !locations.isBlank()) {
            for (String value : locations.split(",")) {
                String normalized = value.trim();
                if (!normalized.isEmpty()) {
                    parsed.add(normalized);
                }
            }
        }
        return new OpsSkillToolSettings(
                enabled,
                parsed,
                editableLocation,
                editableAutoInit);
    }

    public static OpsSkillToolSettings defaults() {
        return fromRaw(true, "classpath:/skills", "./data/skills", true);
    }

    static OpsSkillToolSettings legacyConstructorDefaults() {
        return new OpsSkillToolSettings(false, List.of(), "", false);
    }

    private static List<String> immutableLocations(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                String normalized = value.trim();
                if (!result.contains(normalized)) {
                    result.add(normalized);
                }
            }
        }
        return Collections.unmodifiableList(result);
    }
}
