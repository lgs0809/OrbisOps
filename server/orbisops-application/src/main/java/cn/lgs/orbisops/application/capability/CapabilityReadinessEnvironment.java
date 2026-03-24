package cn.lgs.orbisops.application.capability;

import java.util.List;

/** Typed environment facts used to derive analysis, package and landing readiness. */
public record CapabilityReadinessEnvironment(
        List<CapabilityDependencyReadiness> dependencies,
        boolean approvedLandingEnabled,
        boolean operationJournalReady,
        boolean operationRecoveryReady,
        boolean productionToolRuntimeAvailable) {

    public CapabilityReadinessEnvironment {
        dependencies = dependencies == null || dependencies.isEmpty()
                ? List.of()
                : List.copyOf(dependencies);
    }

    public CapabilityDependencyReadiness dependency(String name) {
        return dependencies.stream()
                .filter(value -> value.name().equals(name))
                .findFirst()
                .orElse(new CapabilityDependencyReadiness(
                        name, false, null, false, false, java.util.Map.of()));
    }
}
