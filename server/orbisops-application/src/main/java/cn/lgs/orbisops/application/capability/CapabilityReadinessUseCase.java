package cn.lgs.orbisops.application.capability;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Application service deriving readiness tiers from normalized environment facts. */
public final class CapabilityReadinessUseCase {

    private final CapabilityReadinessEnvironmentPort environmentPort;
    private final Clock clock;

    public CapabilityReadinessUseCase(CapabilityReadinessEnvironmentPort environmentPort) {
        this(environmentPort, Clock.systemDefaultZone());
    }

    public CapabilityReadinessUseCase(
            CapabilityReadinessEnvironmentPort environmentPort,
            Clock clock) {
        if (environmentPort == null) {
            throw new IllegalArgumentException("CAPABILITY_READINESS_ENVIRONMENT_PORT_REQUIRED");
        }
        if (clock == null) {
            throw new IllegalArgumentException("CAPABILITY_READINESS_CLOCK_REQUIRED");
        }
        this.environmentPort = environmentPort;
        this.clock = clock;
    }

    public CapabilityReadinessSnapshot snapshot() {
        CapabilityReadinessEnvironment environment = environmentPort.inspect();
        List<String> analysisReasons = downReasons(
                environment,
                List.of("toolResultStore", "auditStore"));
        List<String> packageReasons = new ArrayList<>(analysisReasons);
        packageReasons.addAll(downReasons(
                environment,
                List.of("trustedProofStore", "changePackageStore")));

        List<String> landingReasons = new ArrayList<>(packageReasons);
        if (!environment.approvedLandingEnabled()) {
            landingReasons.add("APPROVED_LANDING_DISABLED");
        }
        if (!environment.operationJournalReady()) {
            landingReasons.add("LANDING_OPERATION_JOURNAL_UNAVAILABLE");
        }
        if (!environment.operationRecoveryReady()) {
            landingReasons.add("LANDING_OPERATION_RECOVERY_UNAVAILABLE");
        }
        if (!environment.productionToolRuntimeAvailable()) {
            landingReasons.add("LANDING_TOOL_RUNTIME_UNAVAILABLE");
        }

        return new CapabilityReadinessSnapshot(
                LocalDateTime.now(clock),
                state(analysisReasons),
                state(packageReasons),
                state(landingReasons),
                environment.dependencies());
    }

    private List<String> downReasons(
            CapabilityReadinessEnvironment environment,
            List<String> names) {
        List<String> reasons = new ArrayList<>();
        for (String name : names) {
            if (!environment.dependency(name).up()) {
                reasons.add(name.toUpperCase() + "_UNAVAILABLE");
            }
        }
        return reasons;
    }

    private CapabilityReadinessState state(List<String> reasons) {
        return new CapabilityReadinessState(reasons.isEmpty(), reasons);
    }
}
