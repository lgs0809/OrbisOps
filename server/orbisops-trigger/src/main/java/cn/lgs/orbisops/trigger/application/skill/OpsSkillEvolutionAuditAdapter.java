package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.SkillEvolutionAuditPort;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobStatus;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionPatchSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionRetryTransition;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Governance audit adapter for Skill Evolution job lifecycle events. */
@Component
public class OpsSkillEvolutionAuditAdapter implements SkillEvolutionAuditPort {

    private final OpsConfigAuditService auditService;

    public OpsSkillEvolutionAuditAdapter(OpsConfigAuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public void recordJobCreated(SkillEvolutionJobSnapshot job) {
        auditService.recordRuntimeEvent(
                job.projectId(),
                job.agentId(),
                "",
                "skill-evolver",
                "job-create",
                job.jobId(),
                "LOW",
                SkillEvolutionJobStatus.PENDING.name(),
                Map.of(
                        "jobId", job.jobId(),
                        "runId", job.runId(),
                        "sessionId", job.sessionId(),
                        "projectId", job.projectId(),
                        "agentId", job.agentId(),
                        "triggerReason", job.triggerReason()));
    }

    @Override
    public void recordJobCompleted(
            SkillEvolutionJobSnapshot job,
            String decision,
            SkillEvolutionPatchSnapshot patch,
            SkillEvolutionJobStatus terminalStatus) {
        auditService.recordRuntimeEvent(
                job.projectId(),
                job.agentId(),
                "",
                "skill-evolver",
                "job-run",
                job.jobId(),
                "LOW",
                terminalStatus.name(),
                Map.of(
                        "job", job(job),
                        "decision", value(decision),
                        "patch", patch(patch)));
    }

    @Override
    public void recordJobFailed(
            SkillEvolutionJobSnapshot job,
            SkillEvolutionRetryTransition transition,
            String error) {
        auditService.recordRuntimeEvent(
                job.projectId(),
                job.agentId(),
                "",
                "skill-evolver",
                "job-fail",
                job.jobId(),
                "LOW",
                transition.status().name(),
                Map.of(
                        "error", value(error),
                        "attempts", transition.attempts()));
    }

    private Map<String, Object> job(SkillEvolutionJobSnapshot job) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", job.databaseId());
        data.put("jobId", job.jobId());
        data.put("runId", job.runId());
        data.put("sessionId", job.sessionId());
        data.put("projectId", job.projectId());
        data.put("agentId", job.agentId());
        data.put("triggerReason", job.triggerReason());
        data.put("status", job.status().name());
        data.put("attempts", job.attempts());
        data.put("nextRunAt", time(job.nextRunAt()));
        data.put("lastError", job.lastError());
        data.put("createTime", time(job.createdAt()));
        data.put("updateTime", time(job.updatedAt()));
        return data;
    }

    private Map<String, Object> patch(SkillEvolutionPatchSnapshot patch) {
        return Map.of(
                "patchId", patch.patchId(),
                "jobId", patch.jobId(),
                "runId", patch.runId(),
                "projectId", patch.projectId(),
                "targetSkillId", patch.targetSkillId(),
                "decision", patch.decision(),
                "status", patch.status(),
                "skippedReason", patch.skippedReason());
    }

    private String time(Instant value) {
        return value == null ? "" : value.toString();
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
