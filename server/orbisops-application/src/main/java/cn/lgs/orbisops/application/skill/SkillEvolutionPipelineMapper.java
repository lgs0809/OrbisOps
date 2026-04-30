package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillEvolutionEvidenceReference;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionHintSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionInputSummary;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionSignalSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceEvidenceReference;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceInput;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceObservation;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceTaskTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Maps typed Skill Evolution facts into authoring, persistence and compatibility views. */
public final class SkillEvolutionPipelineMapper {

    private final SkillEvolutionPayloadCodec payloadCodecPort;

    public SkillEvolutionPipelineMapper(
            SkillEvolutionPayloadCodec payloadCodecPort) {
        if (payloadCodecPort == null) {
            throw new IllegalArgumentException(
                    "SKILL_EVOLUTION_PAYLOAD_CODEC_REQUIRED");
        }
        this.payloadCodecPort = payloadCodecPort;
    }

    Map<String, Object> inputView(SkillEvolutionPipelineRequest request) {
        SkillEvolutionInputSummary summary = request.summary();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("projectId", request.projectId());
        input.put("agentId", request.agentId());
        input.put("runId", request.runId());
        input.put("sessionId", request.sessionId());
        input.put("triggerReason", request.triggerReason());
        input.put("primaryIntent", summary.normalizedUserGoal());
        input.put("normalizedUserGoal", summary.normalizedUserGoal());
        input.put("finalOutput", summary.finalReport());
        input.put("completed", summary.hasCompleted());
        input.put("toolEvidence", summary.toolEvents());
        input.put("eventSummaries", summary.eventSummaries());
        input.put("evidenceRefs", evidenceViews(summary.evidenceReferences()));
        input.put("contextBundleHash", summary.contextBundleHash());
        input.put("acceptedTaskEpisode", summary.episodeJson());
        input.put("evolutionSourceHash", summary.sourceHash());
        return input;
    }

    SkillExperienceInput experienceInput(
            SkillEvolutionPipelineRequest request,
            String observationType) {
        SkillEvolutionInputSummary summary = request.summary();
        return new SkillExperienceInput(
                request.projectId(),
                request.agentId(),
                request.runId(),
                request.sessionId(),
                observationType,
                summary.normalizedUserGoal(),
                summary.normalizedUserGoal(),
                summary.toolEvents(),
                experienceEvidence(summary.evidenceReferences()),
                summary.hasCompleted(),
                summary.finalReport(),
                summary.eventSummaries().size());
    }

    List<Map<String, Object>> evidenceViews(
            List<SkillEvolutionEvidenceReference> references) {
        return references.stream()
                .map(reference -> Map.<String, Object>of(
                        "evidenceId", reference.evidenceId(),
                        "resultId", reference.resultId(),
                        "outputHash", reference.outputHash()))
                .toList();
    }

    Map<String, Object> signalView(SkillEvolutionSignalSnapshot signal) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("signalId", signal.signalId());
        view.put("signalType", signal.signalType());
        view.put("projectId", signal.projectId());
        view.put("agentId", signal.agentId());
        view.put("runId", signal.runId());
        view.put("status", signal.status());
        return view;
    }

    Map<String, Object> hintView(SkillEvolutionHintSnapshot hint) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("hintId", hint.hintId());
        view.put("signalId", hint.signalId());
        view.put("runId", hint.runId());
        view.put("hintType", hint.hintType());
        view.put("status", hint.status());
        return view;
    }

    Map<String, Object> experienceView(
            SkillExperienceRecordResult result) {
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

    Map<String, Object> skippedResult(
            String reasonCode,
            Map<String, Object> details) {
        Map<String, Object> result = new LinkedHashMap<>(
                details == null ? Map.of() : details);
        result.put("status", "SKIPPED");
        result.put("reasonCode", text(reasonCode));
        return result;
    }

    SkillEvolutionPipelineDecision decision(Map<String, Object> result) {
        String status = text(result.get("status"), "CANDIDATE");
        return new SkillEvolutionPipelineDecision(
                status,
                text(result.get("reasonCode")),
                text(result.get("targetSkillId")),
                text(result.get("matchedSkillId")),
                payloadCodecPort.encode(result),
                status);
    }

    String encode(Object value) {
        return payloadCodecPort.encode(value);
    }

    String text(Object value) {
        return text(value, "");
    }

    String text(Object value, String fallback) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }

    private List<SkillExperienceEvidenceReference> experienceEvidence(
            List<SkillEvolutionEvidenceReference> references) {
        return references.stream()
                .map(reference -> new SkillExperienceEvidenceReference(
                        reference.evidenceId(),
                        reference.resultId(),
                        reference.outputHash(),
                        "TOOL_RESULT"))
                .toList();
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
}
