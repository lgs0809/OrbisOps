package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillPatchCandidate;
import cn.lgs.orbisops.domain.skill.model.SkillPatchCandidateStatus;
import cn.lgs.orbisops.domain.skill.model.SkillPatchValidationDecision;
import cn.lgs.orbisops.domain.skill.service.SkillPatchValidationPolicy;

import java.util.List;
import java.util.Map;

/** Application use case for candidate schema, policy and regression validation. */
public class SkillPatchValidationApplicationService {

    private static final List<String> VALIDATION_TYPES = List.of(
            "SCHEMA",
            "POLICY",
            "DANGEROUS_CONTENT",
            "EVIDENCE_ATTRIBUTION",
            "SKILL_BOUNDARY",
            "REGRESSION_EVAL");

    private final SkillPatchCandidateApplicationService candidateService;
    private final SkillPatchRegressionEvaluationPort regressionPort;
    private final SkillPatchValidationResultPort validationResultPort;
    private final SkillPatchValidationPolicy validationPolicy;

    public SkillPatchValidationApplicationService(
            SkillPatchCandidateApplicationService candidateService,
            SkillPatchRegressionEvaluationPort regressionPort,
            SkillPatchValidationResultPort validationResultPort,
            SkillPatchValidationPolicy validationPolicy) {
        if (candidateService == null) {
            throw new IllegalArgumentException("SKILL_PATCH_CANDIDATE_SERVICE_REQUIRED");
        }
        if (regressionPort == null) {
            throw new IllegalArgumentException("SKILL_PATCH_REGRESSION_PORT_REQUIRED");
        }
        if (validationResultPort == null) {
            throw new IllegalArgumentException("SKILL_PATCH_VALIDATION_RESULT_PORT_REQUIRED");
        }
        this.candidateService = candidateService;
        this.regressionPort = regressionPort;
        this.validationResultPort = validationResultPort;
        this.validationPolicy = validationPolicy == null
                ? new SkillPatchValidationPolicy()
                : validationPolicy;
    }

    public Map<String, Object> validate(String candidateId) {
        SkillPatchValidationDecision decision = validateDecision(candidateId);
        return Map.of(
                "candidateId", candidateId,
                "status", decision.status(),
                "valid", decision.valid(),
                "failures", decision.failures());
    }

    public SkillPatchValidationDecision validateDecision(String candidateId) {
        SkillPatchCandidate candidate = candidateService.getCandidate(candidateId);
        SkillPatchRegressionResult regression = regressionPort.evaluate(
                SkillPatchCandidateView.of(candidate));
        SkillPatchValidationDecision decision = validationPolicy.evaluate(
                candidate,
                regression.failures());
        for (String type : VALIDATION_TYPES) {
            validationResultPort.upsert(
                    candidateId,
                    type,
                    decision.status(),
                    decision.valid() ? 1D : 0D,
                    "REGRESSION_EVAL".equals(type)
                            ? regression.toMap()
                            : Map.of("failures", decision.failures()));
        }
        candidateService.updateStatus(
                candidateId,
                SkillPatchCandidateStatus.require(decision.status()),
                decision.valid()
                        ? "VALIDATION_PASSED"
                        : String.join(",", decision.failures()));
        return decision;
    }

    /** Release admission is separate from optional platform regression evaluation. */
    public SkillPatchValidationDecision validateMinimumDecision(String candidateId, List<String> contentFailures) {
        SkillPatchValidationDecision decision = validationPolicy.evaluate(
                candidateService.getCandidate(candidateId), contentFailures);
        validationResultPort.upsert(candidateId, "MINIMUM_CHECKS", decision.status(),
                decision.valid() ? 1D : 0D, Map.of("policy", SkillAutomaticPublicationService.POLICY,
                        "failures", decision.failures(), "regressionEvaluated", false));
        candidateService.updateStatus(candidateId, SkillPatchCandidateStatus.require(decision.status()),
                decision.valid() ? "MINIMUM_CHECKS_PASSED" : String.join(",", decision.failures()));
        return decision;
    }
}
