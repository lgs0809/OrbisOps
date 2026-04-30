package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillEvolutionJobRepository;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionInput;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionInputSummary;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobStatus;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionPatchSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionRetryTransition;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionRunCandidate;
import cn.lgs.orbisops.domain.skill.service.SkillEvolutionInputPolicy;
import cn.lgs.orbisops.domain.skill.service.SkillEvolutionJobPolicy;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Application process manager for the Skill Evolution job and patch lifecycle. */
public class SkillEvolutionJobApplicationService {

    private final ISkillEvolutionJobRepository repository;
    private final SkillEvolutionJobPolicy jobPolicy;
    private final SkillEvolutionInputPolicy inputPolicy;
    private final SkillEvolutionSourcePort sourcePort;
    private final SkillEvolutionPipelinePort pipelinePort;
    private final SkillEvolutionPatchJsonEncoder jsonCodecPort;
    private final Supplier<String> patchIdSupplier;
    private final SkillEvolutionAuditPort auditPort;
    private final Clock clock;
    private final SkillEvolutionLeasePort leases;

    public SkillEvolutionJobApplicationService(
            ISkillEvolutionJobRepository repository,
            SkillEvolutionJobPolicy jobPolicy,
            SkillEvolutionInputPolicy inputPolicy,
            SkillEvolutionTraceInputPort traceInputPort,
            SkillEvolutionChatInputPort chatInputPort,
            SkillEvolutionPipelinePort pipelinePort,
            SkillEvolutionPatchJsonEncoder jsonCodecPort,
            Supplier<String> patchIdSupplier,
            SkillEvolutionAuditPort auditPort,
            Clock clock) {
        this(repository,jobPolicy,inputPolicy,
                job -> new SkillEvolutionInput(traceInputPort.trace(job.runId()), chatInputPort.messages(job.sessionId(),200)),
                pipelinePort,jsonCodecPort,patchIdSupplier,auditPort,clock);
    }

    public SkillEvolutionJobApplicationService(ISkillEvolutionJobRepository repository,
            SkillEvolutionJobPolicy jobPolicy, SkillEvolutionInputPolicy inputPolicy, SkillEvolutionSourcePort sourcePort,
            SkillEvolutionPipelinePort pipelinePort, SkillEvolutionPatchJsonEncoder jsonCodecPort,
            Supplier<String> patchIdSupplier, SkillEvolutionAuditPort auditPort, Clock clock) {
        this(repository,jobPolicy,inputPolicy,sourcePort,pipelinePort,jsonCodecPort,patchIdSupplier,auditPort,clock,
                SkillEvolutionLeasePort.unmanaged());
    }

    public SkillEvolutionJobApplicationService(ISkillEvolutionJobRepository repository,
            SkillEvolutionJobPolicy jobPolicy, SkillEvolutionInputPolicy inputPolicy, SkillEvolutionSourcePort sourcePort,
            SkillEvolutionPipelinePort pipelinePort, SkillEvolutionPatchJsonEncoder jsonCodecPort,
            Supplier<String> patchIdSupplier, SkillEvolutionAuditPort auditPort, Clock clock, SkillEvolutionLeasePort leases) {
        if (repository == null) throw new IllegalArgumentException("SKILL_EVOLUTION_JOB_REPOSITORY_REQUIRED");
        if (sourcePort == null) throw new IllegalArgumentException("SKILL_EVOLUTION_SOURCE_PORT_REQUIRED");
        if (pipelinePort == null) throw new IllegalArgumentException("SKILL_EVOLUTION_PIPELINE_PORT_REQUIRED");
        if (jsonCodecPort == null) throw new IllegalArgumentException("SKILL_EVOLUTION_JSON_CODEC_PORT_REQUIRED");
        if (patchIdSupplier == null) throw new IllegalArgumentException("SKILL_EVOLUTION_PATCH_ID_SUPPLIER_REQUIRED");
        this.repository = repository;
        this.leases = java.util.Objects.requireNonNull(leases);
        this.jobPolicy = jobPolicy == null ? new SkillEvolutionJobPolicy() : jobPolicy;
        this.inputPolicy = inputPolicy == null ? new SkillEvolutionInputPolicy() : inputPolicy;
        this.sourcePort = sourcePort;
        this.pipelinePort = pipelinePort;
        this.jsonCodecPort = jsonCodecPort;
        this.patchIdSupplier = patchIdSupplier;
        this.auditPort = auditPort;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

    public SkillEvolutionEnqueueResult enqueue(
            String runId,
            String sessionId,
            String projectId,
            String agentId,
            String triggerReason) {
        String run = value(runId);
        if (run.isBlank()) {
            return SkillEvolutionEnqueueResult.skipped("SKIP_MISSING_CANONICAL_RUN_ID");
        }
        if (!repository.available()) {
            return SkillEvolutionEnqueueResult.skipped("DB_UNAVAILABLE");
        }
        Instant now = clock.instant();
        SkillEvolutionJobSnapshot stored = repository.enqueue(new SkillEvolutionJobSnapshot(
                0L,
                jobPolicy.jobId(run),
                run,
                value(sessionId),
                value(projectId),
                value(agentId),
                jobPolicy.triggerReason(triggerReason),
                SkillEvolutionJobStatus.PENDING,
                0,
                now,
                "",
                null,
                null));
        if (auditPort != null) auditPort.recordJobCreated(stored);
        return SkillEvolutionEnqueueResult.queued(stored);
    }

    public List<SkillEvolutionEnqueueResult> enqueueUnobservedCompletedRuns(int configuredLimit) {
        if (!repository.available()) return List.of();
        int limit = jobPolicy.listLimit(configuredLimit);
        List<SkillEvolutionEnqueueResult> results = new ArrayList<>();
        for (SkillEvolutionRunCandidate candidate : repository.findUnqueuedRunCandidates(limit)) {
            try { results.add(enqueue(
                    candidate.runId(),
                    candidate.sessionId(),
                    candidate.projectId(),
                    candidate.agentId(),
                    "RUN_COMPLETED_BACKGROUND")); }
            catch (IllegalStateException invalidSource) {
                results.add(SkillEvolutionEnqueueResult.skipped("SKIP_ACCEPTED_SOURCE_UNAVAILABLE"));
            }
        }
        return List.copyOf(results);
    }

    public List<SkillEvolutionJobRunResult> runBatch(int configuredBatchSize, int configuredMaxAttempts) {
        if (!repository.available()) return List.of();
        int batchSize = jobPolicy.batchSize(configuredBatchSize);
        int maxAttempts = jobPolicy.maxAttempts(configuredMaxAttempts);
        List<SkillEvolutionJobRunResult> results = new ArrayList<>();
        for (int index = 0; index < batchSize; index++) {
            SkillEvolutionJobSnapshot job = repository.claimPending(maxAttempts).orElse(null);
            if (job == null) break;
            try (var lease = leases.maintain(job)) {
                lease.requireOwned();
                results.add(process(job, maxAttempts, lease));
            } catch (IllegalStateException lost) {
                if (!"SKILL_EVOLUTION_CLAIM_LOST".equals(lost.getMessage())) throw lost;
                results.add(SkillEvolutionJobRunResult.failed(job.jobId(), "CLAIM_LOST", lost.getMessage()));
            }
        }
        return List.copyOf(results);
    }

    public List<SkillEvolutionJobSnapshot> listJobs(String status, int limit) {
        if (!repository.available()) return List.of();
        SkillEvolutionJobStatus filter = null;
        String value = value(status);
        if (!value.isBlank()) {
            try {
                filter = SkillEvolutionJobStatus.require(value);
            } catch (IllegalArgumentException ignored) {
                return List.of();
            }
        }
        return repository.findJobs(filter, jobPolicy.listLimit(limit));
    }

    public SkillEvolutionJobSnapshot getJob(String jobId) {
        if (!repository.available()) {
            throw new IllegalArgumentException("Skill Evolver job 不存在：" + jobId);
        }
        return repository.findJob(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Skill Evolver job 不存在：" + jobId));
    }

    public List<SkillEvolutionPatchSnapshot> listPatches(String jobId, String decision, int limit) {
        if (!repository.available()) return List.of();
        return repository.findPatches(jobId, decision, jobPolicy.listLimit(limit));
    }

    private SkillEvolutionJobRunResult process(SkillEvolutionJobSnapshot job, int maxAttempts, SkillEvolutionLeasePort.Scope lease) {
        try {
            SkillEvolutionInput input = sourcePort.load(job);
            SkillEvolutionInputSummary summary = inputPolicy.summarize(input);
            String skipReason = inputPolicy.skipReason(summary);
            if (input.trace().isEmpty() && input.messages().isEmpty()) skipReason="SKIP_TASK_OUTCOME_UNVERIFIED";
            SkillEvolutionPipelineDecision pipeline = skipReason.isBlank()
                    ? pipelinePort.decide(new SkillEvolutionPipelineRequest(
                            job.projectId(),
                            job.agentId(),
                            job.runId(),
                            job.sessionId(),
                            job.triggerReason(),
                            summary, null, job))
                    : new SkillEvolutionPipelineDecision(
                            "SKIPPED", skipReason, "", "", "{}", "SKIPPED");

            PatchDecision decision = patchDecision(pipeline, summary);
            SkillEvolutionPatchSnapshot patch = new SkillEvolutionPatchSnapshot(
                    0L,
                    requirePatchId(patchIdSupplier.get()),
                    job.jobId(),
                    job.runId(),
                    job.projectId(),
                    decision.targetSkillId(),
                    decision.decision(),
                    decision.patchJson(),
                    decision.validationJson(),
                    jobPolicy.patchStatus(decision.decision(), pipeline.validationStatus()),
                    null,
                    decision.skippedReason(),
                    null,
                    null);
            SkillEvolutionJobStatus terminalStatus = jobPolicy.terminalStatus(decision.decision());
            lease.requireOwned();
            SkillEvolutionPatchSnapshot stored = repository.complete(job, patch, terminalStatus).orElse(null);
            if (stored==null) return SkillEvolutionJobRunResult.failed(job.jobId(),"CLAIM_LOST","SKILL_EVOLUTION_CLAIM_LOST");
            if (auditPort != null) auditPort.recordJobCompleted(job, decision.decision(), stored, terminalStatus);
            return SkillEvolutionJobRunResult.completed(stored);
        } catch (Exception error) {
            lease.requireOwned();
            SkillEvolutionRetryTransition transition = jobPolicy.failureTransition(
                    job, maxAttempts, clock.instant(), error.getMessage());
            if (!repository.rescheduleOrFail(job, transition, error.getMessage()))
                return SkillEvolutionJobRunResult.failed(job.jobId(),"CLAIM_LOST","SKILL_EVOLUTION_CLAIM_LOST");
            if (auditPort != null) auditPort.recordJobFailed(job, transition,
                    error instanceof SkillGroupingDeferredException deferred ? deferred.diagnostic() : error.getMessage());
            return SkillEvolutionJobRunResult.failed(
                    job.jobId(),
                    transition.status().name(),
                    error.getMessage());
        }
    }

    private PatchDecision patchDecision(
            SkillEvolutionPipelineDecision pipeline,
            SkillEvolutionInputSummary summary) {
        if (pipeline.skipped()) {
            String decision = pipeline.reasonCode();
            return new PatchDecision(
                    decision,
                    pipeline.reasonCode(),
                    pipeline.matchedSkillId(),
                    jsonCodecPort.encodePatch("", summary),
                    jsonCodecPort.encodeSkipValidation(decision, pipeline.reasonCode()));
        }
        String decision = jobPolicy.candidateDecision(!pipeline.targetSkillId().isBlank());
        return new PatchDecision(
                decision,
                "",
                pipeline.targetSkillId(),
                jsonCodecPort.encodePatch(pipeline.payloadJson(), summary),
                jsonCodecPort.encodeCandidateValidation(pipeline.payloadJson()));
    }

    private String requirePatchId(String patchId) {
        String id = value(patchId);
        if (id.isBlank()) throw new IllegalStateException("SKILL_EVOLUTION_PATCH_ID_EMPTY");
        return id;
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }

    private record PatchDecision(
            String decision,
            String skippedReason,
            String targetSkillId,
            String patchJson,
            String validationJson) {
    }
}
