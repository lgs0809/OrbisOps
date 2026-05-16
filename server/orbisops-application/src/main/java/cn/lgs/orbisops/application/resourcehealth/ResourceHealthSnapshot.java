package cn.lgs.orbisops.application.resourcehealth;

import java.util.List;

/** Aggregated health snapshot for all resources used by operational agents. */
public record ResourceHealthSnapshot(
        String generatedAt,
        List<ResourceHealthCheck> checks,
        long healthyCount,
        long degradedCount) {

    public ResourceHealthSnapshot {
        generatedAt = generatedAt == null ? "" : generatedAt.trim();
        checks = checks == null ? List.of() : List.copyOf(checks);
    }
}
