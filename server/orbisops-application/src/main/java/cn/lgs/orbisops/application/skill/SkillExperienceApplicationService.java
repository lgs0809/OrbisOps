package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceClusterSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceClusterEvidence;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceConsolidationSample;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceEvidenceReference;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceInput;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceObservation;
import cn.lgs.orbisops.domain.skill.model.SkillExperiencePolicyResult;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceTaskTemplate;
import cn.lgs.orbisops.domain.skill.service.SkillExperienceClusteringPolicy;
import cn.lgs.orbisops.domain.skill.service.SkillExperienceObservationPolicy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Application process manager for Skill experience observation and promotion. */
public class SkillExperienceApplicationService {

    private final SkillExperiencePort experiencePort;
    private final SkillExperienceAuditPort auditPort;
    private final SkillTransactionPort transactionPort;
    private final SkillExperienceObservationPolicy observationPolicy;
    private final SkillTaskOutcomePort outcomes;
    private final SkillExperienceClusteringPolicy clusteringPolicy =
            new SkillExperienceClusteringPolicy();

    public SkillExperienceApplicationService(
            SkillExperiencePort experiencePort,
            SkillExperienceAuditPort auditPort,
            SkillTransactionPort transactionPort,
            SkillExperienceObservationPolicy observationPolicy) {
        this(experiencePort, auditPort, transactionPort, observationPolicy,
                (project, run) -> cn.lgs.orbisops.domain.skill.model.VerifiedTaskOutcome.unknown());
    }

    public SkillExperienceApplicationService(SkillExperiencePort experiencePort, SkillExperienceAuditPort auditPort,
            SkillTransactionPort transactionPort, SkillExperienceObservationPolicy observationPolicy, SkillTaskOutcomePort outcomes) {
        if (experiencePort == null) {
            throw new IllegalArgumentException("SKILL_EXPERIENCE_PORT_REQUIRED");
        }
        if (transactionPort == null) {
            throw new IllegalArgumentException("SKILL_TRANSACTION_PORT_REQUIRED");
        }
        this.experiencePort = experiencePort;
        this.auditPort = auditPort;
        this.transactionPort = transactionPort;
        this.outcomes = java.util.Objects.requireNonNull(outcomes, "SKILL_TASK_OUTCOME_PORT_REQUIRED");
        this.observationPolicy = observationPolicy == null
                ? new SkillExperienceObservationPolicy()
                : observationPolicy;
    }

    public SkillExperienceRecordResult recordObservation(
            SkillExperienceInput input) {
        require(input == null ? null : input.projectId(), "projectId");
        require(input == null ? null : input.runId(), "runId");
        return transactionPort.required(() -> recordRequired(input));
    }

    private SkillExperienceRecordResult recordRequired(SkillExperienceInput input) {
        var verified = outcomes.verifiedSuccess(input.projectId(), input.runId());
        SkillExperiencePolicyResult policy = observationPolicy.sanitize(input, verified);
        String taskTemplateHash = CanonicalObjectHasher.sha256(
                taskTemplateView(policy.taskTemplate()));
        String trajectoryHash = CanonicalObjectHasher.sha256(
                policy.abstractTrajectory());
        String clusterKey = CanonicalObjectHasher.sha256(Map.of(
                "projectId",
                value(input.projectId()),
                "agentId",
                value(input.agentId()),
                "taskFamily",
                clusteringPolicy.clusterIdentity(
                        policy.taskTemplate(),
                        policy.abstractTrajectory())));
        String episodeId = "skill-episode-" + shortHash(
                value(input.projectId()) + ":" + value(input.runId()));
        String observationId = "skill-observation-" + shortHash(
                value(input.projectId())
                        + ":"
                        + value(input.runId())
                        + ":"
                        + value(input.observationType()));
        clusterKey = experiencePort.recordedClusterKey(value(input.projectId()),value(input.agentId()),observationId)
                .orElse(clusterKey);
        SkillExperienceObservation observation = new SkillExperienceObservation(
                episodeId,
                observationId,
                clusterKey,
                value(input.projectId()),
                value(input.agentId()),
                value(input.runId()),
                value(input.sessionId()),
                value(input.observationType()),
                policy.taskTemplate(),
                taskTemplateHash,
                policy.abstractTrajectory(),
                trajectoryHash,
                policy.outcome(),
                policy.evidenceReferences(),
                policy.finalSummary(),
                input.eventCount(),
                policy.qualityScore(), verified);
        experiencePort.upsertEpisode(observation);
        boolean inserted = experiencePort.insertObservation(observation);
        boolean contributed = experiencePort.recordVerifiedContribution(observation);
        if (inserted || contributed) {
            experiencePort.upsertCluster(observation);
            if (auditPort != null) auditPort.recordObservation(observation);
        }
        SkillExperienceClusterSnapshot cluster = experiencePort.cluster(
                observation.projectId(),
                observation.agentId(),
                observation.clusterKey());
        return new SkillExperienceRecordResult(
                observation,
                cluster,
                inserted);
    }

    public void markPromoted(
            String projectId,
            String agentId,
            String clusterKey,
            String candidateId) {
        transactionPort.required(() -> {
            if (experiencePort.markClusterPromoted(
                    value(projectId),
                    value(agentId),
                    value(clusterKey),
                    value(candidateId))) {
                experiencePort.markObservationsPromoted(
                        value(projectId),
                        value(agentId),
                        value(clusterKey));
            }
            return Boolean.TRUE;
        });
    }

    public SkillExperienceClusterEvidence clusterEvidence(
            String projectId,
            String agentId,
            String clusterKey) {
        SkillExperienceClusterEvidence evidence = experiencePort.clusterEvidence(
                value(projectId),
                value(agentId),
                value(clusterKey));
        return evidence == null
                ? SkillExperienceClusterEvidence.empty()
                : evidence;
    }

    public List<SkillExperienceConsolidationSample> consolidationSamples(
            String projectId,
            String agentId,
            String clusterKey,
            int limit) {
        List<SkillExperienceConsolidationSample> samples =
                experiencePort.consolidationSamples(
                        value(projectId),
                        value(agentId),
                        value(clusterKey),
                        Math.max(1, Math.min(limit, 32)));
        return samples == null ? List.of() : List.copyOf(samples);
    }

    public List<SkillExperienceConsolidationSample> consolidationSamples(String projectId,String agentId,String clusterKey,int limit,String requiredRun) {
        var sources=experiencePort.consolidationSamples(value(projectId),value(agentId),value(clusterKey),Math.max(3,Math.min(limit,20)),value(requiredRun));
        return sources==null?List.of():List.copyOf(sources);
    }

    private Map<String, Object> taskTemplateView(
            SkillExperienceTaskTemplate template) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("intent", template.intent());
        view.put("problemPattern", template.problemPattern());
        view.put("triggerType", template.triggerType());
        view.put("evidenceTypes", template.evidenceTypes());
        view.put("outcome", template.outcome());
        return view;
    }

    public static List<Map<String, Object>> evidenceViews(
            List<SkillExperienceEvidenceReference> references) {
        return references.stream()
                .map(reference -> {
                    Map<String, Object> view = new LinkedHashMap<>();
                    putIfPresent(view, "evidenceId", reference.evidenceId());
                    putIfPresent(view, "resultId", reference.resultId());
                    putIfPresent(view, "outputHash", reference.outputHash());
                    putIfPresent(view, "sourceType", reference.sourceType());
                    return view;
                })
                .toList();
    }

    private static void putIfPresent(
            Map<String, Object> target,
            String key,
            String value) {
        if (value != null && !value.isBlank()) target.put(key, value);
    }

    private String shortHash(String value) {
        String hash = CanonicalObjectHasher.sha256Text(value);
        if (hash == null || hash.length() < 32) {
            throw new IllegalStateException("SKILL_EXPERIENCE_HASH_INVALID");
        }
        return hash.substring(0, 32);
    }

    private String require(String value, String field) {
        String result = value(value);
        if (result.isBlank()) {
            throw new IllegalArgumentException(
                    "Skill Observation 缺少 " + field);
        }
        return result;
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
