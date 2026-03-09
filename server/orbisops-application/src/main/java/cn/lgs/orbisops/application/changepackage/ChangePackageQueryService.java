package cn.lgs.orbisops.application.changepackage;

import cn.lgs.orbisops.domain.changepackage.service.ChangePackageStatusPolicy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChangePackageQueryService {

    private static final ChangePackageStatusPolicy STATUS = new ChangePackageStatusPolicy();
    private final ChangePackageQueryPort port;

    public ChangePackageQueryService(ChangePackageQueryPort port) {
        if (port == null) throw new IllegalArgumentException("CHANGE_PACKAGE_QUERY_PORT_REQUIRED");
        this.port = port;
    }

    public Map<String, Object> capabilities() {
        return port.capabilities();
    }

    public List<Map<String, Object>> list(ChangePackageListQuery query) {
        if (query == null) throw new IllegalArgumentException("CHANGE_PACKAGE_LIST_QUERY_REQUIRED");
        return port.list(query).stream().map(this::withProductQueue).toList();
    }

    public Map<String, Object> detail(String packageId) {
        return withProductQueue(port.detail(required(packageId, "CHANGE_PACKAGE_ID_REQUIRED")));
    }

    public List<Map<String, Object>> versions(String packageId) {
        return port.versions(required(packageId, "CHANGE_PACKAGE_ID_REQUIRED"));
    }

    public List<Map<String, Object>> events(String packageId, int limit) {
        return port.events(required(packageId, "CHANGE_PACKAGE_ID_REQUIRED"), limit(limit));
    }

    public List<Map<String, Object>> landingOperationRuns(String packageId, int limit) {
        return port.landingOperationRuns(required(packageId, "CHANGE_PACKAGE_ID_REQUIRED"), limit(limit));
    }

    public Map<String, Object> landingPlan(String packageId) {
        return port.landingPlan(required(packageId, "CHANGE_PACKAGE_ID_REQUIRED"));
    }

    public ChangePackageProductMetricsProjection productMetrics() {
        return port.productMetrics();
    }

    private Map<String, Object> withProductQueue(Map<String, Object> source) {
        if (source == null || source.isEmpty()) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>(source);
        result.put("productQueue", STATUS.productQueue(String.valueOf(source.getOrDefault("status", ""))));
        // Query projections may legitimately carry null optional fields (for example
        // incidentId before an Incident exists). Map.copyOf rejects null values and
        // turned one historical row into a 500 for the whole queue. Keep the view
        // immutable without changing the nullable projection contract.
        return Collections.unmodifiableMap(result);
    }

    private int limit(int value) {
        if (value <= 0 || value > 1000) throw new IllegalArgumentException("CHANGE_PACKAGE_QUERY_LIMIT_INVALID");
        return value;
    }

    private String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
