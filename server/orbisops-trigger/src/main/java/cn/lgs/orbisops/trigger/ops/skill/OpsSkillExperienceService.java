package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.application.skill.SkillExperienceApplicationService;
import cn.lgs.orbisops.application.skill.SkillExperienceRecordResult;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceEvidenceReference;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceInput;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceObservation;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceTaskTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Trigger facade mapping legacy pipeline maps into typed Skill experience input. */
@Service
public class OpsSkillExperienceService {

    private final SkillExperienceApplicationService applicationService;

    public OpsSkillExperienceService(
            SkillExperienceApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    public Map<String, Object> recordObservation(
            Map<String, Object> input,
            String observationType) {
        Map<String, Object> source = input == null ? Map.of() : input;
        SkillExperienceRecordResult result = applicationService.recordObservation(
                new SkillExperienceInput(
                        text(source.get("projectId")),
                        text(source.get("agentId")),
                        text(source.get("runId")),
                        text(source.get("sessionId")),
                        text(observationType),
                        text(source.get("primaryIntent")),
                        text(source.get("normalizedUserGoal")),
                        stringList(source.get("toolEvidence")),
                        evidenceReferences(source.get("evidenceRefs")),
                        bool(source.get("completed")),
                        text(source.get("finalOutput")),
                        list(source.get("eventSummaries")).size()));
        return view(result);
    }

    public void markPromoted(
            String projectId,
            String agentId,
            String clusterKey,
            String candidateId) {
        applicationService.markPromoted(
                projectId,
                agentId,
                clusterKey,
                candidateId);
    }

    private Map<String, Object> view(SkillExperienceRecordResult result) {
        SkillExperienceObservation observation = result.observation();
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("episodeId", observation.episodeId());
        view.put("observationId", observation.observationId());
        view.put("clusterKey", observation.clusterKey());
        view.put("taskTemplate", taskTemplateView(observation.taskTemplate()));
        view.put("taskTemplateHash", observation.taskTemplateHash());
        view.put("abstractTrajectory", observation.abstractTrajectory());
        view.put("trajectoryHash", observation.trajectoryHash());
        view.put("observationCount", result.cluster().observationCount());
        view.put("successfulCount", result.cluster().successfulCount());
        view.put("evidenceCount", result.cluster().evidenceCount());
        view.put("newObservation", result.newObservation());
        return view;
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

    private List<SkillExperienceEvidenceReference> evidenceReferences(Object raw) {
        List<SkillExperienceEvidenceReference> references = new ArrayList<>();
        for (Object item : list(raw)) {
            if (!(item instanceof Map<?, ?> map)) continue;
            SkillExperienceEvidenceReference reference =
                    new SkillExperienceEvidenceReference(
                            text(map.get("evidenceId")),
                            text(map.get("resultId")),
                            text(map.get("outputHash")),
                            text(map.get("sourceType")));
            references.add(reference);
        }
        return references;
    }

    private List<String> stringList(Object raw) {
        return list(raw).stream().map(this::text).toList();
    }

    private List<?> list(Object value) {
        return value instanceof List<?> values ? values : List.of();
    }

    private boolean bool(Object value) {
        return Boolean.TRUE.equals(value)
                || "true".equalsIgnoreCase(text(value));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
