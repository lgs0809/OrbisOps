package cn.lgs.orbisops.application.capability;

import java.time.LocalDateTime;
import java.util.List;

/** Derived readiness snapshot across analysis, package preparation and production landing. */
public record CapabilityReadinessSnapshot(
        LocalDateTime generatedAt,
        CapabilityReadinessState analysisReady,
        CapabilityReadinessState changePackageReady,
        CapabilityReadinessState approvedLandingReady,
        List<CapabilityDependencyReadiness> dependencies) {

    public CapabilityReadinessSnapshot {
        dependencies = dependencies == null || dependencies.isEmpty()
                ? List.of()
                : List.copyOf(dependencies);
    }
}
