package cn.lgs.orbisops.domain.repair.model;

public final class RepairWorkspaceAggregate {

    private final String workspaceId;
    private final String projectId;
    private final String serviceId;
    private final RepairWorkspaceStatus status;

    private RepairWorkspaceAggregate(String workspaceId,
                                     String projectId,
                                     String serviceId,
                                     RepairWorkspaceStatus status) {
        this.workspaceId = required(workspaceId, "REPAIR_WORKSPACE_ID_REQUIRED");
        this.projectId = required(projectId, "REPAIR_PROJECT_ID_REQUIRED");
        this.serviceId = required(serviceId, "REPAIR_SERVICE_ID_REQUIRED");
        if (status == null) throw new IllegalArgumentException("REPAIR_WORKSPACE_STATUS_REQUIRED");
        this.status = status;
    }

    public static RepairWorkspaceAggregate rehydrate(String workspaceId,
                                                      String projectId,
                                                      String serviceId,
                                                      RepairWorkspaceStatus status) {
        return new RepairWorkspaceAggregate(workspaceId, projectId, serviceId, status);
    }

    public void requireDeliverable() {
        if (status != RepairWorkspaceStatus.VERIFIED) {
            throw new IllegalStateException("REPAIR_WORKSPACE_NOT_VERIFIED:" + status.name());
        }
    }

    public void requireCleanupAllowed() {
        if (status == RepairWorkspaceStatus.TESTING || status == RepairWorkspaceStatus.PREPARING) {
            throw new IllegalStateException("REPAIR_WORKSPACE_CLEANUP_FORBIDDEN:" + status.name());
        }
    }

    public String workspaceId() { return workspaceId; }
    public String projectId() { return projectId; }
    public String serviceId() { return serviceId; }
    public RepairWorkspaceStatus status() { return status; }

    private static String required(String input, String error) {
        String value = input == null ? "" : input.trim();
        if (value.isBlank()) throw new IllegalArgumentException(error);
        return value;
    }
}
