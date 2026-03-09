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

    List<SkillEvolutionJobSnapshot> findJobs(SkillEvolutionJobStatus status, int limit);

    Optional<SkillEvolutionJobSnapshot> findJob(String jobId);

    SkillEvolutionPatchSnapshot savePatch(SkillEvolutionPatchSnapshot patch);

    List<SkillEvolutionPatchSnapshot> findPatches(String jobId, String decision, int limit);

    boolean complete(String jobId, SkillEvolutionJobStatus status);

    boolean rescheduleOrFail(String jobId, SkillEvolutionRetryTransition transition, String lastError);
}
