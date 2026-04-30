package cn.lgs.orbisops.domain.skill.adapter.repository;

import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobStatus;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionPatchSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionRetryTransition;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionRunCandidate;

import java.util.List;
import java.util.Optional;

/** Persistence port for Skill Evolution jobs and generated patches. */
public interface ISkillEvolutionJobRepository {

    boolean available();

    SkillEvolutionJobSnapshot enqueue(SkillEvolutionJobSnapshot job);

    List<SkillEvolutionRunCandidate> findUnqueuedRunCandidates(int limit);

    /**
     * Claims one eligible pending job using CAS and returns the pre-claim persisted snapshot.
     * The caller uses the selected attempts value to derive the claimed attempt transition.
     */
    Optional<SkillEvolutionJobSnapshot> claimPending(int maxAttempts);

    /** Renew only the current live attempt; an expired or replaced owner cannot revive itself. */
    boolean renewLease(SkillEvolutionJobSnapshot claim);

    List<SkillEvolutionJobSnapshot> findJobs(SkillEvolutionJobStatus status, int limit);

    Optional<SkillEvolutionJobSnapshot> findJob(String jobId);

    List<SkillEvolutionPatchSnapshot> findPatches(String jobId, String decision, int limit);

    /** Atomically append the patch and complete only the owned, unexpired attempt. */
    Optional<SkillEvolutionPatchSnapshot> complete(SkillEvolutionJobSnapshot claim,
            SkillEvolutionPatchSnapshot patch, SkillEvolutionJobStatus status);

    boolean rescheduleOrFail(SkillEvolutionJobSnapshot claim, SkillEvolutionRetryTransition transition, String lastError);
}
