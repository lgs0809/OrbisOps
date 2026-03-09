package cn.lgs.orbisops.application.toolset;

/** Target and execution bounds for one local or project-scoped MySQL operation. */
public record LocalMySqlExecutionTarget(
        String projectId,
        String resourceId,
        int maxRows,
        int timeoutSeconds) {

    public LocalMySqlExecutionTarget {
        projectId = text(projectId);
        resourceId = text(resourceId);
        maxRows = Math.max(1, Math.min(maxRows, 1000));
        timeoutSeconds = Math.max(1, Math.min(timeoutSeconds, 300));
    }

    public boolean projectScoped() {
        return !resourceId.isBlank();
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
