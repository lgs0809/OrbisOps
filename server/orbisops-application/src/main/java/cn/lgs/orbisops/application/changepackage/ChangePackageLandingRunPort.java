package cn.lgs.orbisops.application.changepackage;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;

import java.util.List;
import java.util.Optional;

public interface ChangePackageLandingRunPort {

    Optional<ChangePackageLandingRun> findByIdempotencyKey(String idempotencyKey);

    Optional<ChangePackageLandingRun> find(String runId);

    /** Original authenticated execution owner; recovery must not invent a worker principal. */
    default String executionActor(String runId) { return ""; }

    List<ChangePackageLandingRun> findStrandedTerminalRuns(int limit);

    List<ChangePackageLandingRun> findLateCompletedFailedRuns(int limit);

    void start(String runId,
               String idempotencyKey,
               ChangePackageCurrent current,
               int approvedVersion,
               String approvedPackageHash,
               String leaseToken,
               String actor);

    void complete(String runId, ChangePackageLandingRunStatus status, Object result);
}
