package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.SkillEvolutionAuthoredCandidate;
import cn.lgs.orbisops.application.skill.SkillEvolutionAuthoringPort;
import cn.lgs.orbisops.application.skill.SkillEvolutionPipelineAuditPort;
import cn.lgs.orbisops.application.skill.SkillEvolutionSimilarityMatch;
import cn.lgs.orbisops.application.skill.SkillEvolutionSimilarityPort;
import cn.lgs.orbisops.trigger.ops.OpsConfigAuditService;
import cn.lgs.orbisops.trigger.ops.skill.OpsSkillAuthoringAgent;
import cn.lgs.orbisops.trigger.ops.skill.OpsSkillSimilarityService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

/** Outer adapter for authoring, similarity, JSON and runtime audit concerns used by Skill Evolution. */
@Component
public class OpsSkillEvolutionPipelineAdapter implements
        SkillEvolutionAuthoringPort,
        SkillEvolutionSimilarityPort,
        SkillEvolutionPipelineAuditPort {

    private final OpsSkillAuthoringAgent authoringAgent;
    private final OpsSkillSimilarityService similarityService;
    private final OpsConfigAuditService auditService;
    private final cn.lgs.orbisops.application.skill.SkillEvolutionRelatedSkillService relatedSkillService;

    public OpsSkillEvolutionPipelineAdapter(
            OpsSkillAuthoringAgent authoringAgent,
            OpsSkillSimilarityService similarityService,
            OpsConfigAuditService auditService) {
        this(authoringAgent,similarityService,auditService,null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public OpsSkillEvolutionPipelineAdapter(OpsSkillAuthoringAgent authoringAgent,OpsSkillSimilarityService similarityService,
            OpsConfigAuditService auditService,cn.lgs.orbisops.application.skill.SkillEvolutionRelatedSkillService relatedSkillService) {
        this.relatedSkillService=relatedSkillService;
        this.authoringAgent = authoringAgent;
        this.similarityService = similarityService;
        this.auditService = auditService;
    }

    @Override
    public boolean available() { return authoringAgent.available(); }

    @Override
    public SkillEvolutionAuthoredCandidate author(Map<String, Object> input) {
        Map<String, Object> result = authoringAgent.author(
                input == null ? Map.of() : input);
        return SkillEvolutionAuthoredCandidate.from(result == null ? Map.of() : result);
    }
    @Override public SkillEvolutionAuthoredCandidate author(Map<String,Object> input,cn.lgs.orbisops.application.skill.SkillAuthoringProgressPort progress) {
        return SkillEvolutionAuthoredCandidate.from(authoringAgent.author(input,progress));
    }

    @Override
    public SkillEvolutionSimilarityMatch bestMatch(
            String projectId,
            Map<String, Object> candidate) {
        Map<String, Object> result = similarityService.bestMatch(
                text(projectId),
                candidate == null ? Map.of() : candidate);
        return similarityMatch(result);
    }

    @Override
    public cn.lgs.orbisops.application.skill.SkillEvolutionRelatedSkills relatedSkills(String projectId,Map<String,Object> input) {
        requireRelatedStore();
        return new cn.lgs.orbisops.application.skill.SkillEvolutionRelatedSkills(projectId,relatedSkillService.select(projectId,input));
    }

    @Override
    public void validateRelatedSkills(String projectId,List<Map<String,Object>> skills) {
        requireRelatedStore();relatedSkillService.requireCurrent(projectId,skills);
    }

    @Override
    public SkillEvolutionSimilarityMatch bestFrozenMatch(String projectId,Map<String,Object> candidate,List<Map<String,Object>> skills) {
        validateRelatedSkills(projectId,skills);
        return similarityMatch(similarityService.bestFrozenMatch(projectId,candidate,skills));
    }

    private void requireRelatedStore() {
        if(relatedSkillService==null) throw new IllegalStateException("SKILL_EVOLUTION_RELATED_SKILL_STORE_REQUIRED");
    }

    private SkillEvolutionSimilarityMatch similarityMatch(Map<String,Object> result) {
        Map<String, Object> safe = result == null ? Map.of() : result;
        if (safe.isEmpty()) return SkillEvolutionSimilarityMatch.none();
        return new SkillEvolutionSimilarityMatch(
                text(safe.get("skillId")),
                integer(safe.get("version")),
                text(safe.get("skillHash")),
                decimal(safe.get("similarity")),
                frozen(safe),
                text(safe.get("similarityReason")));
    }

    @Override
    public void recordSkipped(
            String projectId,
            String agentId,
            String runId,
            String reasonCode,
            Map<String, Object> details) {
        Map<String, Object> metadata = new LinkedHashMap<>(
                details == null ? Map.of() : details);
        metadata.put("reasonCode", text(reasonCode));
        auditService.recordRuntimeEvent(
                text(projectId),
                text(agentId),
                "",
                "skill-evolution",
                "SKILL_EVOLUTION_SKIPPED",
                text(runId),
                "LOW",
                "SKIPPED",
                metadata);
    }

    @Override
    public void recordCandidateCreated(
            String projectId,
            String agentId,
            String candidateId,
            String releaseStatus,
            Map<String, Object> details) {
        auditService.recordRuntimeEvent(
                text(projectId),
                text(agentId),
                "",
                "skill-evolution",
                "SKILL_EVOLUTION_CANDIDATE_CREATED",
                text(candidateId),
                "LOW",
                text(releaseStatus),
                details == null ? Map.of() : details);
    }

    @Override
    public void recordCandidateSelectionCompared(
            String projectId,
            String agentId,
            String runId,
            String rolloutMode,
            Map<String, Object> details) {
        Map<String, Object> metadata = new LinkedHashMap<>(
                details == null ? Map.of() : details);
        metadata.put("rolloutMode", text(rolloutMode));
        auditService.recordRuntimeEvent(
                text(projectId),
                text(agentId),
                "",
                "skill-evolution",
                "SKILL_EVOLUTION_CANDIDATE_SELECTION_COMPARED",
                text(runId),
                "MEDIUM",
                "EVALUATED",
                Map.copyOf(metadata));
    }


    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int integer(Object value) {
        if (value instanceof Number number) return Math.max(0, number.intValue());
        try {
            return Math.max(0, Integer.parseInt(text(value)));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private double decimal(Object value) {
        if (value instanceof Number number) return bounded(number.doubleValue());
        try {
            return bounded(Double.parseDouble(text(value)));
        } catch (NumberFormatException ignored) {
            return 0D;
        }
    }

    private double bounded(double value) {
        if (!Double.isFinite(value)) return 0D;
        return Math.max(0D, Math.min(1D, value));
    }

    private boolean frozen(Map<String, Object> value) {
        return Boolean.TRUE.equals(value.get("frozen"))
                || "FROZEN".equalsIgnoreCase(text(value.get("status")))
                || "FROZEN".equalsIgnoreCase(text(value.get("updateMode")));
    }
}
