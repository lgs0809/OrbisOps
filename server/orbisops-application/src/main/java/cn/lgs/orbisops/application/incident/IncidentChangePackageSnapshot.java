package cn.lgs.orbisops.application.incident;

/** Narrow published read model used by Incident without importing ChangePackage domain internals. */
public record IncidentChangePackageSnapshot(
        String packageId,
        String status,
        int version,
        String packageHash,
        String landingRunId,
        String updateTime) {

    public IncidentChangePackageSnapshot {
        packageId = text(packageId);
        status = text(status);
        packageHash = text(packageHash);
        landingRunId = text(landingRunId);
        updateTime = text(updateTime);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
