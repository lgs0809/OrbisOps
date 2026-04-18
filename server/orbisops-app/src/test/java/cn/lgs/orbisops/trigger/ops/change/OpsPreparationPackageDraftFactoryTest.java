package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePreparationAssessment;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePreparationContext;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePreparationDecision;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePreparationProof;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageType;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpsPreparationPackageDraftFactoryTest {

    private final OpsPreparationPackageDraftFactory factory =
            new OpsPreparationPackageDraftFactory();

    @Test
    void testValidationDoesNotTurnProductionChangeIntoMultipleLandingEnvironments() {
        var validation = Map.<String, Object>of("operationId", "validate", "targetEnvironment", "test",
                "effectType", "VALIDATE_ONLY", "effectScope", "SANDBOX", "mutability", "READ_ONLY",
                "prepareAllowed", true, "writesTargetResource", false);
        var production = Map.<String, Object>of("operationId", "land", "targetEnvironment", "prod",
                "effectType", "MUTATE_TARGET_RESOURCE", "writesTargetResource", true);
        var decision = decision(ChangePackagePreparationAssessment.ACCEPTABLE,
                ChangePackageType.MCP_OPERATION_PACKAGE, ChangePackageStatus.READY_FOR_REVIEW,
                "HIGH", List.of(), "");
        var operations = new OpsPreparationOperationBindingFactory.OperationBundle(List.of(validation, production), List.of());
        var draft = factory.create(input(decision, Map.of(), operations));
        assertEquals("prod", draft.get("targetEnvironment"));
        assertEquals(operations.operations(), draft.get("mcpSteps"));
        var anotherTarget = Map.<String, Object>of("operationId", "other", "targetEnvironment", "staging",
                "writesTargetResource", true);
        assertThrows(IllegalArgumentException.class, () -> factory.create(input(decision, Map.of(),
                new OpsPreparationOperationBindingFactory.OperationBundle(List.of(validation, production, anotherTarget), List.of()))));
        var disguisedWrite = new java.util.LinkedHashMap<>(validation);
        disguisedWrite.put("effectScope", "TARGET_RESOURCE_WRITE");
        assertThrows(IllegalArgumentException.class, () -> factory.create(input(decision, Map.of(),
                new OpsPreparationOperationBindingFactory.OperationBundle(List.of(disguisedWrite, production), List.of()))));
    }

    @Test
    void acceptableDecisionProducesCompleteReviewableDraft() {
        Map<String, Object> draft = factory.create(input(
                decision(ChangePackagePreparationAssessment.ACCEPTABLE,
                        ChangePackageType.MCP_OPERATION_PACKAGE,
                        ChangePackageStatus.READY_FOR_REVIEW,
                        "HIGH",
                        List.of(),
                        ""),
                Map.of(
                        "incidentId", "incident-1",
                        "allowedTools", List.of("explicit-tool"),
                        "forbiddenTools", List.of("delete_resource"),
                        "ciResult", Map.of("status", "PASSED"),
                        "cleanupPlan", Map.of("status", "PLANNED"))));

        assertEquals("project-1", draft.get("projectId"));
        assertEquals("session-1", draft.get("sessionId"));
        assertEquals("run-1", draft.get("runId"));
        assertEquals("incident-1", draft.get("incidentId"));
        assertEquals("prep-agent", draft.get("preparationAgentId"));
        assertEquals(3, draft.get("preparationAgentVersion"));
        assertEquals("MCP_OPERATION_PACKAGE", draft.get("packageType"));
        assertEquals("READY_FOR_REVIEW", draft.get("status"));
        assertEquals("ACCEPTABLE", draft.get("validationAssessment"));
        assertEquals("HIGH", draft.get("riskLevel"));
        assertEquals("prod", draft.get("targetEnvironment"));
        assertTrue(String.valueOf(draft.get("summary")).contains("验证结果可接受"));
        assertEquals(List.of("explicit-tool"), draft.get("allowedTools"));
        assertEquals(List.of("delete_resource"), draft.get("forbiddenTools"));
        assertEquals(Map.of("status", "PASSED"), draft.get("ciResult"));
        assertEquals(Map.of("status", "PLANNED"), draft.get("cleanupPlan"));
        assertEquals(List.of("skill-a"), draft.get("usedSkillRefs"));
        assertEquals(List.of(2), draft.get("usedSkillVersions"));
        assertEquals(List.of("skill-hash"), draft.get("usedSkillHashes"));
        assertEquals("method-hash", draft.get("preparationMethodHash"));
        assertEquals("prepare using controlled tools", draft.get("preparationMethodSummary"));
        assertEquals("alice", draft.get("createBy"));
        assertEquals(operationBundle().operations(), draft.get("mcpSteps"));

        @SuppressWarnings("unchecked")
        Map<String, Object> evidence = (Map<String, Object>) draft.get("evidence");
        assertEquals("repair service", evidence.get("userObjective"));
        assertEquals(List.of(Map.of("evidenceId", "evidence-1")),
                evidence.get("trustedEvidence"));
        assertEquals(List.of(), evidence.get("notVerifiedItems"));
        assertEquals(
                List.of("验证结果可接受，但 Landing 仍需按审批边界和 MCP Policy 执行。"),
                evidence.get("riskDisclosure"));
        assertEquals(1, evidence.get("preparationIterations"));

        @SuppressWarnings("unchecked")
        Map<String, Object> landingPlan =
                (Map<String, Object>) draft.get("landingPlan");
        assertEquals(planEnvelope().approvalBoundary(), landingPlan.get("approvalBoundary"));
        assertEquals(planEnvelope().preferredPlan(), landingPlan.get("preferredPlan"));
        assertEquals(planEnvelope().adjustmentPolicy(), landingPlan.get("adjustmentPolicy"));
        assertEquals(planEnvelope().replanTriggers(), landingPlan.get("replanTriggers"));
    }

    @Test
    void refinementDecisionUsesSafeDefaultsAndFailureSummary() {
        ChangePackagePreparationDecision decision = decision(
                ChangePackagePreparationAssessment.NEEDS_REFINEMENT,
                ChangePackageType.MANUAL_REQUIRED,
                ChangePackageStatus.DRAFT,
                "MEDIUM",
                List.of("dry run missing", "sandbox unavailable"),
                "PREPARATION_PROOF_INCOMPLETE");

        Map<String, Object> draft = factory.create(input(decision, Map.of()));

        assertEquals("MANUAL_REQUIRED", draft.get("packageType"));
        assertEquals("DRAFT", draft.get("status"));
        assertEquals("PREPARATION_PROOF_INCOMPLETE", draft.get("reasonCode"));
        assertTrue(String.valueOf(draft.get("summary")).contains("需重新设计或补充验证"));
        assertEquals(
                Map.of("required", true, "status", "MANUAL_REQUIRED"),
                draft.get("rollbackSteps"));
        assertEquals(
                List.of("确认目标指标/日志恢复正常", "确认无新增高风险错误", "确认回滚材料仍可用"),
                draft.get("verificationCriteria"));
        assertEquals(Map.of("status", "NOT_RUN"), draft.get("ciResult"));
        assertEquals(Map.of("status", "NO_TEMP_RESOURCE"), draft.get("cleanupPlan"));
        assertEquals(
                Map.of(
                        "reasonCode", "PREPARATION_PROOF_INCOMPLETE",
                        "limitations", List.of("dry run missing", "sandbox unavailable")),
                draft.get("failureSummary"));

        @SuppressWarnings("unchecked")
        Map<String, Object> evidence = (Map<String, Object>) draft.get("evidence");
        assertEquals(
                List.of("当前变更包不代表自动修复已验证成功，审批前需要人工确认未验证项。"),
                evidence.get("riskDisclosure"));
        assertEquals(List.of("dry run missing", "sandbox unavailable"),
                evidence.get("missingEvidence"));
    }

    private OpsPreparationPackageDraftFactory.Input input(
            ChangePackagePreparationDecision decision,
            Map<String, Object> request) {
        return input(decision, request, operationBundle());
    }

    private OpsPreparationPackageDraftFactory.Input input(ChangePackagePreparationDecision decision,
            Map<String, Object> request, OpsPreparationOperationBindingFactory.OperationBundle operations) {
        return new OpsPreparationPackageDraftFactory.Input(
                "project-1",
                "alice",
                "service is unhealthy",
                "repair service",
                request,
                agent(),
                decision,
                operations,
                evidenceBundle(),
                proofBundle(),
                planEnvelope(),
                contextBundle(),
                Map.of(
                        "preparationMethodHash", "method-hash",
                        "preparationMethodSummary", "prepare using controlled tools"));
    }

    private ChangePackagePreparationDecision decision(
            ChangePackagePreparationAssessment assessment,
            ChangePackageType type,
            ChangePackageStatus status,
            String risk,
            List<String> limitations,
            String reasonCode) {
        return new ChangePackagePreparationDecision(
                assessment,
                type,
                status,
                risk,
                limitations,
                reasonCode,
                false);
    }

    private OpsPreparationOperationBindingFactory.OperationBundle operationBundle() {
        Map<String, Object> operation = Map.of(
                "operationId", "op-1",
                "mcpId", "mcp-1",
                "toolName", "query_health",
                "targetEnvironment", "prod",
                "effectType", "READ_EXTERNAL_STATE",
                "effectScope", "PLATFORM_INTERNAL",
                "mutability", "READ_ONLY");
        return new OpsPreparationOperationBindingFactory.OperationBundle(
                List.of(operation),
                List.of(Map.of(
                        "operationId", "op-1",
                        "mcpId", "mcp-1",
                        "toolName", "query_health",
                        "schemaBound", true)));
    }

    private OpsPreparationEvidenceService.EvidenceBundle evidenceBundle() {
        return new OpsPreparationEvidenceService.EvidenceBundle(
                List.of(Map.of("evidenceId", "evidence-1")),
                List.of(Map.of(
                        "evidenceId", "evidence-1",
                        "toolResultId", "tool-result-1",
                        "outputHash", "output-hash",
                        "fullOutputRef", "minio://proof/1",
                        "runId", "run-1")));
    }

    private OpsPreparationProofBundleFactory.ProofBundle proofBundle() {
        Map<String, Object> passed = Map.of(
                "status", "PASSED",
                "source", "MCP_RUNTIME");
        ChangePackagePreparationContext context = new ChangePackagePreparationContext(
                new ChangePackagePreparationProof("PASSED", "MCP_RUNTIME", false),
                new ChangePackagePreparationProof("PASSED", "MCP_RUNTIME", false),
                List.of(),
                true,
                true,
                false,
                "MCP_OPERATION_PACKAGE",
                "HIGH",
                List.of());
        return new OpsPreparationProofBundleFactory.ProofBundle(
                passed,
                passed,
                context,
                List.of());
    }

    private OpsPreparationPlanEnvelopeFactory.Envelope planEnvelope() {
        Map<String, Object> operation = operationBundle().operations().get(0);
        return new OpsPreparationPlanEnvelopeFactory.Envelope(
                List.of(Map.of(
                        "candidateId", "candidate-1",
                        "status", "DRAFTED",
                        "steps", List.of(operation))),
                Map.of(
                        "maxRiskLevel", "HIGH",
                        "allowedMcpTools", List.of("query_health", "mcp-1")),
                Map.of(
                        "selectedCandidateId", "candidate-1",
                        "steps", List.of("op-1")),
                Map.of("allowRetry", true, "maxRiskLevelAfterAdjustment", "HIGH"),
                List.of(Map.of("type", "ARGUMENT_CHANGE")),
                List.of("query_health", "mcp-1"),
                List.of("POLICY_STALE_OR_UNKNOWN"));
    }

    private Map<String, Object> contextBundle() {
        return Map.ofEntries(
                Map.entry("sessionId", "session-1"),
                Map.entry("runId", "run-1"),
                Map.entry("contextBundleId", "ctx-1"),
                Map.entry("contextBundleHash", "ctx-hash"),
                Map.entry("memoryContextRefs", List.of("project:project-1")),
                Map.entry("memoryContextHash", "memory-hash"),
                Map.entry("compressedMemorySummary", "memory summary"),
                Map.entry("memoryInjectionVersion", "memory-v1"),
                Map.entry("usedSkillVersionRefs", List.of(Map.of(
                        "skillId", "skill-a",
                        "version", 2,
                        "skillHash", "skill-hash"))),
                Map.entry("usedSkillRefsHash", "skill-refs-hash"),
                Map.entry("toolsetRefs", List.of("toolset-a")),
                Map.entry("policyRefs", List.of("policy-a")),
                Map.entry("policyHash", "policy-hash"),
                Map.entry("toolsetBoundaryHash", "toolset-hash"),
                Map.entry("runtimeBoundaryHash", "runtime-hash"),
                Map.entry("approvalBoundaryHash", "approval-hash"));
    }

    private OpsAgentDefinition agent() {
        return OpsAgentDefinition.builder()
                .agentId("prep-agent")
                .version(3)
                .name("Preparation Agent")
                .projectId("project-1")
                .build();
    }
}
