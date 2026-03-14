package cn.lgs.orbisops.application.worksession.run;

/** Coordinates durable Run authorization/state with local cancellation and resume scheduling. */
public final class WorkSessionRunControlUseCase<R, Q> {

    private final WorkSessionRunControlPort<R, Q> runPort;
    private final WorkSessionLocalCancellationPort localCancellation;
    private final WorkSessionResumeSchedulerPort<Q> resumeScheduler;

    public WorkSessionRunControlUseCase(
            WorkSessionRunControlPort<R, Q> runPort,
            WorkSessionLocalCancellationPort localCancellation,
            WorkSessionResumeSchedulerPort<Q> resumeScheduler) {
        if (runPort == null || localCancellation == null || resumeScheduler == null) {
            throw new IllegalArgumentException("WORK_SESSION_RUN_CONTROL_DEPENDENCIES_REQUIRED");
        }
        this.runPort = runPort;
        this.localCancellation = localCancellation;
        this.resumeScheduler = resumeScheduler;
    }

    public R get(String runId, String projectId) {
        return runPort.get(requiredRunId(runId), required(projectId, "projectId"));
    }

    public R getForActor(
            String runId,
            String projectId,
            String actor) {
        String normalizedRun = requiredRunId(runId);
        String normalizedProject = required(projectId, "projectId");
        runPort.assertActorCanRead(
                normalizedRun,
                normalizedProject,
                required(actor, "actor"));
        return runPort.get(normalizedRun, normalizedProject);
    }

    public void assertReadable(
            String runId,
            String projectId,
            String actor) {
        runPort.assertActorCanRead(
                requiredRunId(runId),
                required(projectId, "projectId"),
                required(actor, "actor"));
    }

    public WorkSessionResumeAccepted resume(
            String runId,
            String projectId,
            String actor) {
        String normalizedRun = requiredRunId(runId);
        String normalizedProject = required(projectId, "projectId");
        Q request = runPort.resumeRequest(
                normalizedRun,
                normalizedProject,
                required(actor, "actor"));
        resumeScheduler.schedule(request);
        return new WorkSessionResumeAccepted(
                normalizedRun,
                normalizedProject,
                "RESUME_SCHEDULED",
                "已从安全检查点重新取得 Work Session；可通过事件接口继续查看。");
    }

    public void assertCanResumeApproval(
            String runId,
            String projectId,
            String actor) {
        runPort.approvalResumeRequest(
                requiredRunId(runId),
                required(projectId, "projectId"),
                required(actor, "actor"));
    }

    public WorkSessionResumeAccepted resumeApproval(
            String runId,
            String projectId,
            String actor) {
        String normalizedRun = requiredRunId(runId);
        String normalizedProject = required(projectId, "projectId");
        Q request = runPort.approvalResumeRequest(
                normalizedRun,
                normalizedProject,
                required(actor, "actor"));
        resumeScheduler.schedule(request);
        return new WorkSessionResumeAccepted(
                normalizedRun,
                normalizedProject,
                "APPROVAL_RESUME_SCHEDULED",
                "审批决定已记录，Work Session 已从审批等待点重新调度。");
    }

    public boolean cancelLocal(String runId) {
        String normalizedRun = requiredRunId(runId);
        localCancellation.markCanceled(normalizedRun);
        return true;
    }

    public boolean requestCancel(
            String runId,
            String projectId,
            String actor,
            String reason) {
        String normalizedRun = requiredRunId(runId);
        boolean persisted = text(projectId).isBlank()
                ? runPort.requestCancelForActor(
                        normalizedRun,
                        required(actor, "actor"),
                        text(reason))
                : runPort.requestCancel(
                        normalizedRun,
                        projectId.trim(),
                        text(actor),
                        text(reason));
        if (persisted) localCancellation.markCanceled(normalizedRun);
        return persisted;
    }

    public boolean requestCancelForActor(
            String runId,
            String actor,
            String reason) {
        String normalizedRun = requiredRunId(runId);
        boolean persisted = runPort.requestCancelForActor(
                normalizedRun,
                required(actor, "actor"),
                text(reason));
        if (persisted) localCancellation.markCanceled(normalizedRun);
        return persisted;
    }

    private String requiredRunId(String runId) {
        String normalized = text(runId);
        if (normalized.isBlank()) throw new IllegalArgumentException("runId 不能为空");
        return normalized;
    }

    private String required(String value, String field) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " 不能为空");
        return normalized;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
