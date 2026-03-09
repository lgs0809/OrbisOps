package cn.lgs.orbisops.domain.memory.model;

/** Latest active/conflict pointer used for governed-memory version and conflict decisions. */
public record GovernedMemoryHead(
        String memoryId,
        int version,
        String memoryHash) {

    public GovernedMemoryHead {
        memoryId = memoryId == null ? "" : memoryId.trim();
        memoryHash = memoryHash == null ? "" : memoryHash.trim();
    }
}
