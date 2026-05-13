package cn.lgs.orbisops.domain.memory.model;

/** Runtime-tunable parameters consumed by the domain memory selection policy. */
public record MemorySelectionConfiguration(
        boolean recencyAware,
        double recencyHalfLifeTurns) {

    public MemorySelectionConfiguration {
        recencyHalfLifeTurns = Math.max(1D, recencyHalfLifeTurns);
    }
}
