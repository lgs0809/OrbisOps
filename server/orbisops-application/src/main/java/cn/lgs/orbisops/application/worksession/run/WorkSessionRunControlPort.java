package cn.lgs.orbisops.application.worksession.run;

/** Outbound durable Run boundary used by the Chat-facing control process manager. */
public interface WorkSessionRunControlPort<R, Q> {

    R get(String runId, String projectId);

    void assertActorCanRead(String runId, String projectId, String actor);

    Q resumeRequest(String runId, String projectId, String actor);

    Q approvalResumeRequest(String runId, String projectId, String actor);

    boolean requestCancel(String runId, String projectId, String actor, String reason);

    boolean requestCancelForActor(String runId, String actor, String reason);
}
