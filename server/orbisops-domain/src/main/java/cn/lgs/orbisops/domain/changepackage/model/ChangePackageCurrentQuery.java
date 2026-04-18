package cn.lgs.orbisops.domain.changepackage.model;

public record ChangePackageCurrentQuery(String projectId,
                                        String sessionId,
                                        String incidentId,
                                        ChangePackageStatus status,
                                        int limit) {
    public ChangePackageCurrentQuery {
        projectId = text(projectId);
        sessionId = text(sessionId);
        incidentId = text(incidentId);
        if (limit <= 0 || limit > 500) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_CURRENT_QUERY_LIMIT_INVALID");
        }
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
