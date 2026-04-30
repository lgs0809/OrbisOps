package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillPatchCandidate;
import cn.lgs.orbisops.domain.skill.model.SkillPatchCandidateStatus;
import cn.lgs.orbisops.domain.skill.model.SkillShadowDecision;
import cn.lgs.orbisops.domain.skill.service.SkillShadowDecisionPolicy;

import java.util.List;
import java.util.Map;

/** Application use case for deterministic plus model-assisted Skill shadow evaluation. */
public class SkillShadowApplicationService {

    private final SkillPatchCandidateApplicationService candidateService;
    private final SkillPatchRegressionEvaluationPort regressionPort;
    private final SkillShadowModelPort modelPort;
    private final SkillShadowEvalCasePort evalCasePort;
    private final SkillShadowDecisionPolicy decisionPolicy;
    private final SkillShadowSettings settings;

    public SkillShadowApplicationService(
            SkillPatchCandidateApplicationService candidateService,
            SkillPatchRegressionEvaluationPort regressionPort,
            SkillShadowModelPort modelPort,
            SkillShadowEvalCasePort evalCasePort,
            SkillShadowDecisionPolicy decisionPolicy,
            SkillShadowSettings settings) {
        if (candidateService == null) {
            throw new IllegalArgumentException("SKILL_PATCH_CANDIDATE_SERVICE_REQUIRED");
        }
        if (regressionPort == null) {
            throw new IllegalArgumentException("SKILL_PATCH_REGRESSION_PORT_REQUIRED");
        }
        if (modelPort == null) {
            throw new IllegalArgumentException("SKILL_SHADOW_MODEL_PORT_REQUIRED");
        }
        if (evalCasePort == null) {
            throw new IllegalArgumentException("SKILL_SHADOW_EVAL_CASE_PORT_REQUIRED");
        }
        if (settings == null) {
            throw new IllegalArgumentException("SKILL_SHADOW_SETTINGS_REQUIRED");
        }
        this.candidateService = candidateService;
        this.regressionPort = regressionPort;
        this.modelPort = modelPort;
        this.evalCasePort = evalCasePort;
        this.decisionPolicy = decisionPolicy == null
                ? new SkillShadowDecisionPolicy()
                : decisionPolicy;
        this.settings = settings;
    }

    public Map<String, Object> evaluate(String candidateId) {
        return evaluateOutcome(candidateId).view();
    }

    public SkillShadowEvaluationOutcome evaluateOutcome(String candidateId) {
        if (!settings.enabled()) {
            return fail(candidateId, "SHADOW_DISABLED",
                    List.of("Shadow evaluation is disabled"), 0D);
        }
        SkillPatchCandidate candidate = candidateService.getCandidate(candidateId);
        Map<String, Object> candidatePayload = SkillPatchCandidateView.of(candidate);
        SkillPatchRegressionResult deterministic = regressionPort.evaluate(candidatePayload);
        if (!deterministic.passed()) {
            return fail(candidateId, "SHADOW_DETERMINISTIC_REGRESSION_FAILED",
                    deterministic.failures(), 0D);
        }
        List<?> cases = candidate.evalCases();
        List<?> evidence = candidate.evidenceRefs();
        if (cases.isEmpty() || evidence.isEmpty()) {
            return fail(candidateId, "SHADOW_EVIDENCE_OR_EVAL_MISSING",
                    List.of("Shadow requires historical eval cases and traceable evidence refs"), 0D);
        }
        if (!modelPort.available()) {
            return fail(candidateId, "SHADOW_MODEL_NOT_AVAILABLE",
                    List.of("No model is available for regression comparison; candidate was not promoted"), 0D);
        }
        SkillShadowModelResult model = modelPort.evaluate(
                candidateId, candidatePayload, deterministic);
        if (!model.valid()) {
            return fail(candidateId, "SHADOW_EVALUATION_INVALID",
                    List.of("Shadow evaluator returned no valid result"), 0D);
        }
        SkillShadowDecision decision = decisionPolicy.decide(
                model.valid(),
                model.passed(),
                model.score(),
                model.routingRegression(),
                model.evidenceRegression(),
                model.toolCallRegression(),
                model.unsafe(),
                model.reasons(),
                settings.minimumScore());
        evalCasePort.persist(candidateId, candidatePayload, cases, evidence, model.raw());
        SkillPatchCandidateStatus status = decision.passed()
                ? SkillPatchCandidateStatus.SHADOW
                : SkillPatchCandidateStatus.VALIDATION_FAILED;
        String reasonCode = decision.passed()
                ? "SHADOW_PASSED"
                : "SHADOW_REGRESSION_FAILED";
        candidateService.updateStatus(candidateId, status, reasonCode);
        return new SkillShadowEvaluationOutcome(
                candidateId,
                status.name(),
                reasonCode,
                decision,
                deterministic.toMap(),
                cases.size());
    }

    private SkillShadowEvaluationOutcome fail(
            String candidateId,
            String reasonCode,
            List<String> reasons,
            double score) {
        if (candidateId != null && !candidateId.isBlank()) {
            candidateService.updateStatus(
                    candidateId,
                    SkillPatchCandidateStatus.VALIDATION_FAILED,
                    reasonCode);
        }
        return new SkillShadowEvaluationOutcome(
                candidateId,
                "VALIDATION_FAILED",
                reasonCode,
                new SkillShadowDecision(false, score, reasons),
                Map.of(),
                0);
    }

    private List<?> list(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }
}
