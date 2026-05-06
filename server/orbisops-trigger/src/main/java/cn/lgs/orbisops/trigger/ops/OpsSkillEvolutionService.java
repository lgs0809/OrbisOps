package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.application.skill.SkillEvolutionJobApplicationService;
import cn.lgs.orbisops.application.skill.SkillEvolutionDiagnosticPort;
import cn.lgs.orbisops.trigger.application.skill.OpsSkillEvolutionJobMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Legacy Map ACL over typed Skill Evolution application services and coordinators. */
@Service
public class OpsSkillEvolutionService {

    private final SkillEvolutionJobApplicationService applicationService;
    private final OpsSkillEvolutionJobMapper mapper;
    private final OpsSkillEvolutionTriggerCoordinator triggerCoordinator;
    private final OpsSkillEvolutionWorkerCoordinator workerCoordinator;
    private final SkillEvolutionDiagnosticPort diagnostics;

    public OpsSkillEvolutionService(
            SkillEvolutionJobApplicationService applicationService,
            OpsSkillEvolutionJobMapper mapper) {
        this(
                applicationService,
                mapper,
                OpsSkillEvolutionSettings.legacyConstructorDefaults());
    }

    public OpsSkillEvolutionService(
            SkillEvolutionJobApplicationService applicationService,
            OpsSkillEvolutionJobMapper mapper,
            OpsSkillEvolutionSettings settings) {
        this(applicationService,mapper,settings,SkillEvolutionDiagnosticPort.NONE);
    }

    @Autowired
    public OpsSkillEvolutionService(SkillEvolutionJobApplicationService applicationService,
            OpsSkillEvolutionJobMapper mapper, OpsSkillEvolutionSettings settings,
            SkillEvolutionDiagnosticPort diagnostics) {
        this.applicationService = applicationService;
        this.mapper = mapper;
        this.diagnostics = diagnostics;
        OpsSkillEvolutionSettings effective = settings == null
                ? OpsSkillEvolutionSettings.defaults()
                : settings;
        this.triggerCoordinator = new OpsSkillEvolutionTriggerCoordinator(
                applicationService,
                mapper,
                effective);
        this.workerCoordinator = new OpsSkillEvolutionWorkerCoordinator(
                applicationService,
                mapper,
                effective);
    }

    public Map<String, Object> enqueue(
            String runId,
            String sessionId,
            String projectId,
            String agentId,
            String triggerReason) {
        return triggerCoordinator.enqueue(
                runId,
                sessionId,
                projectId,
                agentId,
                triggerReason);
    }

    public void scheduledRun() {
        workerCoordinator.runBatch();
    }

    public List<Map<String, Object>> runBatch() {
        return workerCoordinator.runBatch();
    }

    public List<Map<String, Object>> listJobs(String status, int limit) {
        return applicationService.listJobs(status, limit).stream()
                .map(mapper::job)
                .toList();
    }

    public Map<String, Object> getJob(String jobId) {
        var job=applicationService.getJob(jobId);
        var result=mapper.job(job);
        if(!job.lastError().isBlank()) result.put("lastFailureCode",
                diagnostics.currentFailure(job.projectId(),job.jobId(),job.attempts()));
        var experience=diagnostics.savedExperience(job.projectId(),job.jobId(),job.sourceId());
        if(!experience.isEmpty()) result.put("savedExperience",experience);
        var publication=diagnostics.authoredPublication(job.projectId(),job.jobId(),job.sourceId());
        if(publication!=null && !publication.isEmpty()) result.put("authoredPublication",publication);
        diagnostics.authoredDecision(job.projectId(),job.jobId(),job.sourceId())
                .ifPresent(decision->result.put("authoredDecision",decision));
        return result;
    }

    public List<Map<String, Object>> listPatches(
            String jobId,
            String decision,
            int limit) {
        var patches = applicationService.listPatches(jobId, decision, limit).stream().map(mapper::patch).toList();
        var refs = patches.stream().filter(p -> p.get("candidateId") instanceof String id && !id.isBlank())
                .map(p -> new SkillEvolutionDiagnosticPort.PublicationRef(
                        (String) p.get("projectId"), (String) p.get("candidateId"))).distinct().toList();
        return mapper.withPublications(patches, diagnostics.publications(refs));
    }
}
