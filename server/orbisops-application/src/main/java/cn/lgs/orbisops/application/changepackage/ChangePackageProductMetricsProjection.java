package cn.lgs.orbisops.application.changepackage;

public record ChangePackageProductMetricsProjection(
        long packageCount,
        long incidentLinkedPackageCount,
        long approvedCount,
        long landedCount,
        long landingFailedCount,
        long needsReplanCount,
        long rejectedCount) {

    public static ChangePackageProductMetricsProjection empty() {
        return new ChangePackageProductMetricsProjection(0, 0, 0, 0, 0, 0, 0);
    }
}
