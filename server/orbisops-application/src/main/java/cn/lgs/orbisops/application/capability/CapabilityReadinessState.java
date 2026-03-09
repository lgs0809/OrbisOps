package cn.lgs.orbisops.application.capability;

import java.util.List;

/** Derived readiness state for one capability tier. */
public record CapabilityReadinessState(
        boolean ready,
        List<String> reasonCodes) {

    public CapabilityReadinessState {
        reasonCodes = reasonCodes == null || reasonCodes.isEmpty()
                ? List.of()
                : List.copyOf(reasonCodes);
    }

    public String status() {
        return ready ? "UP" : "DOWN";
    }
}
