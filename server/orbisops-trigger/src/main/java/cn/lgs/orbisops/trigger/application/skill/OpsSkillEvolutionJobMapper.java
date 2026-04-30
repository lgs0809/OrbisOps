package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.SkillEvolutionEnqueueResult;
import cn.lgs.orbisops.application.skill.SkillEvolutionJobRunResult;
import cn.lgs.orbisops.application.skill.SkillEvolutionDiagnosticPort;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionJobSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionPatchSnapshot;
import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Legacy Map ACL for Skill Evolution job and patch APIs. */
@Component
public class OpsSkillEvolutionJobMapper {

    public Map<String, Object> enqueue(SkillEvolutionEnqueueResult result) {
        if (result == null || !result.queued()) {
            return Map.of(
                    "queued", false,
                    "reason", result == null ? "DB_UNAVAILABLE" : result.reason());
        }
        SkillEvolutionJobSnapshot job = result.job();
        return Map.of(
                "jobId", job.jobId(),
                "runId", job.runId(),
                "sessionId", job.sessionId(),
                "projectId", job.projectId(),
                "agentId", job.agentId(),
                "triggerReason", job.triggerReason());
    }

    public Map<String, Object> runResult(SkillEvolutionJobRunResult result) {
        if (result.patch() != null) return patchResult(result.patch());
        return Map.of(
                "jobId", result.jobId(),
                "status", result.status(),
                "error", result.error());
    }

    public Map<String, Object> job(SkillEvolutionJobSnapshot job) {
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
        data.put("ordinaryFailureCount", job.ordinaryFailures());
        data.put("acceptedSourceId", job.sourceId());
        data.put("executionEpoch", job.epoch());
        data.put("leaseUntilMillis", job.leaseUntilMillis());
        data.put("nextRunAt", time(job.nextRunAt()));
        data.put("lastError", job.lastError());
        data.put("createTime", time(job.createdAt()));
        data.put("updateTime", time(job.updatedAt()));
        return data;
    }

    public Map<String, Object> patch(SkillEvolutionPatchSnapshot snapshot) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", snapshot.databaseId());
        data.put("patchId", snapshot.patchId());
        data.put("jobId", snapshot.jobId());
        data.put("runId", snapshot.runId());
        data.put("projectId", snapshot.projectId());
        data.put("targetSkillId", snapshot.targetSkillId());
        data.put("decision", snapshot.decision());
        Map<String, Object> patch = parseObject(snapshot.patchJson());
        Map<String, Object> validation = parseObject(snapshot.validationJson());
        data.put("patch", patch);
        data.put("validation", validation);
        data.put("sourceSummary", patch.getOrDefault("summary", Map.of()));
        Map<String, Object> pipeline = parseObject(value(patch.get("content")));
        if (!pipeline.isEmpty()) {
            data.put("candidateId", pipeline.get("candidateId"));
            data.put("patchType", pipeline.get("patchType"));
            data.put("changes", pipeline.getOrDefault("changes", List.of()));
            data.put("evalCases", pipeline.getOrDefault("evalCases", List.of()));
            data.put("authoringReason", pipeline.get("authoringReason"));
            data.put("authoringSource", pipeline.get("authoringSource"));
            data.put("similarity", pipeline.get("similarity"));
            data.put("release", pipeline.getOrDefault("release", Map.of()));
        }
        data.put("status", snapshot.status());
        data.put("appliedVersion", snapshot.appliedVersion());
        data.put("skippedReason", snapshot.skippedReason());
        data.put("createTime", time(snapshot.createdAt()));
        data.put("updateTime", time(snapshot.updatedAt()));
        return data;
    }

    /** Add a current read projection without rewriting the immutable generated patch status. */
    public List<Map<String,Object>> withPublications(List<Map<String,Object>> patches,
            Map<SkillEvolutionDiagnosticPort.PublicationRef,SkillEvolutionDiagnosticPort.PublicationState> publications) {
        for(var patch:patches) {
            var current=publications.get(new SkillEvolutionDiagnosticPort.PublicationRef(
                    (String)patch.get("projectId"),(String)patch.get("candidateId")));
            if(current!=null) patch.put("publication",current);
        }
        return patches;
    }

    private Map<String, Object> patchResult(SkillEvolutionPatchSnapshot patch) {
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

    private Map<String, Object> parseObject(String json) {
        if (value(json).isBlank()) return Map.of();
        try {
            return new LinkedHashMap<>(JSON.parseObject(json));
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String time(Instant value) {
        return value == null ? "" : value.toString();
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
