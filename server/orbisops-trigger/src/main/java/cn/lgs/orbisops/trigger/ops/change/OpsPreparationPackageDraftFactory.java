package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePreparationDecision;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentRunExecutionContextCodec;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentSnapshotFactory;
import cn.lgs.orbisops.trigger.ops.runtime.OpsPlatformLandingRuntimeDefinitionFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Assembles the final PREPARE package request from typed decisions and bounded projections. */
final class OpsPreparationPackageDraftFactory {

    private static final Map<String, Object> DEFAULT_ROLLBACK_PLAN = Map.of(
            "required", true,
            "status", "MANUAL_REQUIRED");
    private static final List<String> DEFAULT_VERIFICATION_CRITERIA = List.of(
            "确认目标指标/日志恢复正常",
            "确认无新增高风险错误",
            "确认回滚材料仍可用");
    private static final OpsPreparationEvidenceProjectionFactory EVIDENCE_PROJECTION_FACTORY =
            new OpsPreparationEvidenceProjectionFactory();
    private static final OpsPreparationLandingPlanProjectionFactory LANDING_PLAN_PROJECTION_FACTORY =
            new OpsPreparationLandingPlanProjectionFactory();
    private static final OpsPreparationContextProjectionFactory CONTEXT_PROJECTION_FACTORY =
            new OpsPreparationContextProjectionFactory();
    private static final OpsAgentSnapshotFactory AGENT_SNAPSHOT_FACTORY =
            new OpsAgentSnapshotFactory();
    private static final OpsAgentRunExecutionContextCodec AUTHORITY_CODEC =
            new OpsAgentRunExecutionContextCodec();

    Map<String, Object> create(Input input) {
        if (input == null) throw new IllegalArgumentException("PREPARATION_DRAFT_INPUT_REQUIRED");
        ChangePackagePreparationDecision decision = input.decision();
        String assessment = decision.assessment().name();
        String packageType = decision.packageType().name();
        String riskLevel = decision.riskLevel();
        String reasonCode = decision.reasonCode();
        List<String> limitations = List.copyOf(decision.limitations());
        Map<String, Object> request = input.request();
        Map<String, Object> contextBundle = input.contextBundle();
        Map<String, Object> methodRef = input.preparationMethodRef();
        OpsAgentDefinition agent = input.agent();
        Object rollbackPlan = request.getOrDefault("rollbackPlan", DEFAULT_ROLLBACK_PLAN);
        Object verificationCriteria = request.getOrDefault(
                "verificationCriteria",
                DEFAULT_VERIFICATION_CRITERIA);

        Map<String, Object> evidence = EVIDENCE_PROJECTION_FACTORY.create(
                new OpsPreparationEvidenceProjectionFactory.Input(
                        input.objective(),
                        input.question(),
                        agent,
                        decision,
                        input.operationBundle(),
                        input.evidenceBundle(),
                        input.proofBundle(),
                        input.planEnvelope(),
                        contextBundle,
                        methodRef,
                        rollbackPlan,
                        verificationCriteria));
        Map<String, Object> landingPlan =
                LANDING_PLAN_PROJECTION_FACTORY.create(input.planEnvelope());

        Map<String, Object> packageRequest = new LinkedHashMap<>();
        packageRequest.put("projectId", input.projectId());
        packageRequest.put("sessionId", text(contextBundle.get("sessionId"), ""));
        packageRequest.put("runId", text(contextBundle.get("runId"), ""));
        packageRequest.put("incidentId", text(request.get("incidentId"), ""));
        packageRequest.put("preparationAgentId", text(agent.getAgentId(), ""));
        packageRequest.put("preparationAgentVersion", agent.getVersion() == null ? 0 : agent.getVersion());
        packageRequest.put(
                "preparationAgentSnapshot",
                AUTHORITY_CODEC.encodeAgent(AGENT_SNAPSHOT_FACTORY.fromDefinition(agent)));
        packageRequest.put("packageType", packageType);
        packageRequest.put("status", decision.status().name());
        packageRequest.put("objective", input.objective());
        packageRequest.put("summary", summaryFor(assessment, input.question()));
        packageRequest.put("evidence", evidence);
        packageRequest.put("evidenceRefs", input.evidenceBundle().evidenceRefs());
        packageRequest.put("toolBindings", input.operationBundle().toolBindings());
        packageRequest.put("preflightResult", input.proofBundle().preflight());
        packageRequest.put("dryRunResult", input.proofBundle().dryRun());
        packageRequest.put("__trustedPreparationProofs", Map.of(
                "preflightResult", input.proofBundle().preflight(),
                "dryRunResult", input.proofBundle().dryRun()));
        packageRequest.put("validationAssessment", assessment);
        packageRequest.put("reasonCode", reasonCode);
        packageRequest.put("approvalBoundary", input.planEnvelope().approvalBoundary());
        packageRequest.put("preferredPlan", input.planEnvelope().preferredPlan());
        packageRequest.put("adjustmentPolicy", input.planEnvelope().adjustmentPolicy());
        packageRequest.put("riskLevel", riskLevel);
        packageRequest.put("targetEnvironment", targetEnvironment(input.operationBundle().operations()));
        packageRequest.put(
                "allowedTools",
                request.getOrDefault("allowedTools", input.planEnvelope().allowedTools()));
        packageRequest.put(
                "forbiddenTools",
                request.getOrDefault("forbiddenTools", List.of("UNKNOWN", "DELETE_TARGET_RESOURCE")));
        packageRequest.put(
                "mcpSteps",
                input.operationBundle().operations());
        packageRequest.put("rollbackSteps", rollbackPlan);
        packageRequest.put("verificationCriteria", verificationCriteria);
        packageRequest.put(
                "ciResult",
                request.getOrDefault("ciResult", Map.of("status", "NOT_RUN")));
        packageRequest.put("landingPlan", landingPlan);
        packageRequest.put(
                "allowedLandingAdjustments",
                input.planEnvelope().allowedLandingAdjustments());
        packageRequest.put(
                "failureSummary",
                Map.of("reasonCode", reasonCode, "limitations", limitations));
        packageRequest.put(
                "cleanupPlan",
                request.getOrDefault("cleanupPlan", Map.of("status", "NO_TEMP_RESOURCE")));
        copyRepairIdentity(request, packageRequest);
        packageRequest.putAll(CONTEXT_PROJECTION_FACTORY.packageContext(contextBundle));
        packageRequest.put(
                "usedSkillRefs",
                CONTEXT_PROJECTION_FACTORY.skillRefValues(contextBundle, "skillId"));
        packageRequest.put(
                "usedSkillVersions",
                CONTEXT_PROJECTION_FACTORY.skillRefValues(contextBundle, "version"));
        packageRequest.put(
                "usedSkillHashes",
                CONTEXT_PROJECTION_FACTORY.skillRefValues(contextBundle, "skillHash"));
        packageRequest.put("preparationMethodRef", methodRef);
        packageRequest.put(
                "preparationMethodHash",
                text(methodRef.get("preparationMethodHash"), ""));
        packageRequest.put(
                "preparationMethodSummary",
                text(methodRef.get("preparationMethodSummary"), ""));
        packageRequest.put("createBy", input.actor());
        return packageRequest;
    }

    private void copyRepairIdentity(Map<String, Object> request, Map<String, Object> target) {
        for (String key : List.of(
                "repairWorkspaceId", "repositoryId", "serviceId", "baseCommit", "repairCommit",
                "diffHash", "changedFiles", "testCommand", "testProofHash", "artifactDigest")) {
            if (request.containsKey(key)) target.put(key, request.get(key));
        }
    }

    private String targetEnvironment(List<Map<String, Object>> operations) {
        List<Map<String, Object>> safe = operations == null ? List.of() : operations;
        List<Map<String, Object>> landing = safe.stream()
                .filter(operation -> !preparationValidation(operation))
                .toList();
        // A test validation is evidence for the change, not a second Landing target.
        // Keep its own environment on the operation and preserve rejection of multiple Landing targets.
        List<String> environments = (landing.isEmpty() ? safe : landing).stream()
                .map(operation -> text(operation == null ? null : operation.get("targetEnvironment"), ""))
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
        if (environments.size() > 1) {
            throw new IllegalArgumentException("CHANGE_PACKAGE_MULTIPLE_TARGET_ENVIRONMENTS_UNSUPPORTED");
        }
        return environments.isEmpty() ? "" : environments.get(0);
    }

    private boolean preparationValidation(Map<String, Object> operation) {
        return operation != null && Boolean.TRUE.equals(operation.get("prepareAllowed"))
                && !Boolean.TRUE.equals(operation.get("writesTargetResource"))
                && List.of("VALIDATE_ONLY", "DRY_RUN", "MUTATE_EPHEMERAL", "MUTATE_TEST_RESOURCE")
                        .contains(text(operation.get("effectType"), ""))
                && !List.of("PRODUCTION", "TARGET_RESOURCE_WRITE").contains(text(operation.get("effectScope"), ""))
                && !List.of("PROD_MUTATING", "DESTRUCTIVE").contains(text(operation.get("mutability"), ""));
    }

    private String summaryFor(String assessment, String question) {
        if ("ACCEPTABLE".equals(assessment)) {
            return "PREPARE 已形成可审核 ChangePackage，验证结果可接受：" + question;
        }
        if ("NEEDS_REFINEMENT".equals(assessment)) {
            return "PREPARE 发现预验证失败，已生成需重新设计或补充验证的 ChangePackage：" + question;
        }
        return "PREPARE 当前仍有未满足的结构或显式 validation 条件，已生成需人工确认的 ChangePackage：" + question;
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    record Input(String projectId,
                 String actor,
                 String question,
                 String objective,
                 Map<String, Object> request,
                 OpsAgentDefinition agent,
                 ChangePackagePreparationDecision decision,
                 OpsPreparationOperationBindingFactory.OperationBundle operationBundle,
                 OpsPreparationEvidenceService.EvidenceBundle evidenceBundle,
                 OpsPreparationProofBundleFactory.ProofBundle proofBundle,
                 OpsPreparationPlanEnvelopeFactory.Envelope planEnvelope,
                 Map<String, Object> contextBundle,
                 Map<String, Object> preparationMethodRef) {
        Input {
            projectId = required(projectId, "PREPARATION_DRAFT_PROJECT_REQUIRED");
            actor = actor == null ? "" : actor.trim();
            question = question == null ? "" : question.trim();
            objective = objective == null ? "" : objective.trim();
            request = request == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(request));
            if (agent == null) throw new IllegalArgumentException("PREPARATION_DRAFT_AGENT_REQUIRED");
            if (decision == null) throw new IllegalArgumentException("PREPARATION_DRAFT_DECISION_REQUIRED");
            if (operationBundle == null) throw new IllegalArgumentException("PREPARATION_DRAFT_OPERATIONS_REQUIRED");
            if (evidenceBundle == null) throw new IllegalArgumentException("PREPARATION_DRAFT_EVIDENCE_REQUIRED");
            if (proofBundle == null) throw new IllegalArgumentException("PREPARATION_DRAFT_PROOF_REQUIRED");
            if (planEnvelope == null) throw new IllegalArgumentException("PREPARATION_DRAFT_PLAN_REQUIRED");
            contextBundle = contextBundle == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(contextBundle));
            preparationMethodRef = preparationMethodRef == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(preparationMethodRef));
        }

        private static String required(String value, String message) {
            String normalized = value == null ? "" : value.trim();
            if (normalized.isBlank()) throw new IllegalArgumentException(message);
            return normalized;
        }
    }
}
