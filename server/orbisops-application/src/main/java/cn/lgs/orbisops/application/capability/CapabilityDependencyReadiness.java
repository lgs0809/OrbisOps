package cn.lgs.orbisops.application.capability;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Normalized readiness result for one external capability dependency. */
public record CapabilityDependencyReadiness(
        String name,
        boolean up,
        String reason,
        boolean usableForValidation,
        boolean productionLandingEligible,
        Map<String, Object> attributes) {

    public CapabilityDependencyReadiness {
        attributes = attributes == null || attributes.isEmpty()
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }
}
