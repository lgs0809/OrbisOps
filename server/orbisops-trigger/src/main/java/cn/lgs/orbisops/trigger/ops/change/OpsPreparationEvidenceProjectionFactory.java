package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePreparationDecision;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Projects PREPARE facts into the review evidence contract without performing IO. */
final class OpsPreparationEvidenceProjectionFactory {

    private static final OpsPreparationContextProjectionFactory CONTEXT_PROJECTION_FACTORY =
            new OpsPreparationContextProjectionFactory();

    Map<String, Object> create(Input input) {
        if (input == null) throw new IllegalArgumentException("PREPARATION_EVIDENCE_INPUT_REQUIRED");
        ChangePackagePreparationDecision decision = input.decision();
        String assessment = decision.assessment().name();
        String riskLevel = decision.riskLevel();
        String reasonCode = decision.reasonCode();
        List<String> limitations = List.copyOf(decision.limitations());
        OpsAgentDefinition agent = input.agent();

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("userObjective", input.objective());
        evidence.put("diagnosis", Map.of(
                "summary", "已进入 PREPARE 阶段；当前版本未伪造实时根因，需以已授权 MCP/RAG/Skill 证据继续收敛。",
                "confidence", 0.35,
                "source", text(agent.getAgentId(), "owning-work-session-agent")));
        evidence.put("evidence", List.of(Map.of(
                "type", "USER_INPUT",
                "summary", input.question(),
                "verified", false)));
        evidence.put("trustedEvidence", input.evidenceBundle().trustedEvidence());
        evidence.put("candidatePlans", input.planEnvelope().candidatePlans());
        evidence.put("selectedPlan", input.planEnvelope().preferredPlan());
        evidence.put("toolBindings", input.operationBundle().toolBindings());
        evidence.put("mcpSteps", input.operationBundle().operations());
        evidence.put("preflightResult", input.proofBundle().preflight());
        evidence.put("validationResult", input.proofBundle().dryRun());
        evidence.put("validationAssessment", assessment);
        evidence.put("reasonCode", reasonCode);
        evidence.put("limitations", limitations);
        evidence.put("missingEvidence", limitations);
        evidence.put("rollbackPlan", input.rollbackPlan());
        evidence.put("verificationRequirements", input.verificationCriteria());
        evidence.put("riskAssessment", Map.of(
                "riskLevel", riskLevel,
                "assessment", assessment,
                "limitations", limitations));
        if (!input.contextBundle().isEmpty()) {
            evidence.put("contextBundle",
                    CONTEXT_PROJECTION_FACTORY.evidenceContext(input.contextBundle()));
        }
        if (!input.preparationMethodRef().isEmpty()) {
            evidence.put("preparationMethodRef", input.preparationMethodRef());
        }
        evidence.put("notVerifiedItems", input.proofBundle().notVerifiedItems());
        evidence.put("riskDisclosure", riskDisclosure(assessment));
        evidence.put("preparationIterations", input.planEnvelope().candidatePlans().size());
        evidence.put("preparationAgent", Map.of(
                "agentId", text(agent.getAgentId(), ""),
                "version", agent.getVersion() == null ? 0 : agent.getVersion(),
                "name", text(agent.getName(), text(agent.getAgentId(), ""))));
        return evidence;
    }

    private List<String> riskDisclosure(String assessment) {
        return "ACCEPTABLE".equals(assessment)
                ? List.of("验证结果可接受，但 Landing 仍需按审批边界和 MCP Policy 执行。")
                : List.of("当前变更包不代表自动修复已验证成功，审批前需要人工确认未验证项。");
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    record Input(String objective,
                 String question,
                 OpsAgentDefinition agent,
                 ChangePackagePreparationDecision decision,
                 OpsPreparationOperationBindingFactory.OperationBundle operationBundle,
                 OpsPreparationEvidenceService.EvidenceBundle evidenceBundle,
                 OpsPreparationProofBundleFactory.ProofBundle proofBundle,
                 OpsPreparationPlanEnvelopeFactory.Envelope planEnvelope,
                 Map<String, Object> contextBundle,
                 Map<String, Object> preparationMethodRef,
                 Object rollbackPlan,
                 Object verificationCriteria) {
        Input {
            objective = objective == null ? "" : objective.trim();
            question = question == null ? "" : question.trim();
            if (agent == null) throw new IllegalArgumentException("PREPARATION_EVIDENCE_AGENT_REQUIRED");
            if (decision == null) throw new IllegalArgumentException("PREPARATION_EVIDENCE_DECISION_REQUIRED");
            if (operationBundle == null) throw new IllegalArgumentException("PREPARATION_EVIDENCE_OPERATIONS_REQUIRED");
            if (evidenceBundle == null) throw new IllegalArgumentException("PREPARATION_EVIDENCE_FACTS_REQUIRED");
            if (proofBundle == null) throw new IllegalArgumentException("PREPARATION_EVIDENCE_PROOF_REQUIRED");
            if (planEnvelope == null) throw new IllegalArgumentException("PREPARATION_EVIDENCE_PLAN_REQUIRED");
            contextBundle = contextBundle == null ? Map.of() : contextBundle;
            preparationMethodRef = preparationMethodRef == null ? Map.of() : preparationMethodRef;
            rollbackPlan = rollbackPlan == null ? Map.of() : rollbackPlan;
            verificationCriteria = verificationCriteria == null ? List.of() : verificationCriteria;
        }
    }
}
