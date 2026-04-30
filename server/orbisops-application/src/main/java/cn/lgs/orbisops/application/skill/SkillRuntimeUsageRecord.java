package cn.lgs.orbisops.application.skill;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Locked runtime usage row used for outcome reconciliation. */
public record SkillRuntimeUsageRecord(
        long id,
        String skillId,
        int skillVersion,
        Map<String, Object> outcome,
        String persistenceToken) {

    public SkillRuntimeUsageRecord {
        Map<String, Object> safeOutcome = new LinkedHashMap<>();
        if (outcome != null) {
            outcome.forEach((key, value) -> {
                if (key != null && value != null) safeOutcome.put(key, value);
            });
        }
        outcome = Collections.unmodifiableMap(safeOutcome);
        persistenceToken = persistenceToken == null ? "" : persistenceToken;
    }
}
