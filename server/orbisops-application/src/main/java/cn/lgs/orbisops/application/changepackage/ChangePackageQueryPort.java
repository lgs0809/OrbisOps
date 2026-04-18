package cn.lgs.orbisops.application.changepackage;

import java.util.List;
import java.util.Map;

public interface ChangePackageQueryPort {
    Map<String, Object> capabilities();

    List<Map<String, Object>> list(ChangePackageListQuery query);

    Map<String, Object> detail(String packageId);

    List<Map<String, Object>> versions(String packageId);

    List<Map<String, Object>> events(String packageId, int limit);

    List<Map<String, Object>> landingOperationRuns(String packageId, int limit);

    Map<String, Object> landingPlan(String packageId);

    ChangePackageProductMetricsProjection productMetrics();
}
