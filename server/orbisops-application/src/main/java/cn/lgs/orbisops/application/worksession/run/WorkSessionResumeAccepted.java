package cn.lgs.orbisops.application.worksession.run;

/** Protocol-neutral acknowledgement after a recoverable Work Session is scheduled. */
public record WorkSessionResumeAccepted(
        String runId,
        String projectId,
        String status,
        String message) {

    public WorkSessionResumeAccepted {
        runId = required(runId, "runId");
        projectId = required(projectId, "projectId");
        status = required(status, "status");
        message = message == null ? "" : message.trim();
    }

    private static String required(String value, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " 不能为空");
        return normalized;
    }
}
