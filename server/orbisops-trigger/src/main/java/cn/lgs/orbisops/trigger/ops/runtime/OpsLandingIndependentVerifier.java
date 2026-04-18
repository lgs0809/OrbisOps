package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.changepackage.LandingOperationFact;
import cn.lgs.orbisops.application.changepackage.LandingVerificationRecordPort;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingOperation;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Reads frozen postconditions after the agent has stopped; model prose is never evidence. */
@Component
public final class OpsLandingIndependentVerifier {
    private final OpsToolExecutionService tools;
    private final LandingVerificationRecordPort records;

    public OpsLandingIndependentVerifier(@org.springframework.context.annotation.Lazy OpsToolExecutionService tools,
                                         LandingVerificationRecordPort records) {
        this.tools = java.util.Objects.requireNonNull(tools);
        this.records = java.util.Objects.requireNonNull(records);
    }

    public Map<String, Object> verify(ApprovedLandingAgentCommand command, List<LandingOperationFact> facts) {
        return verify(new ReadContext(command.landingRunId(), command.approvedPlan(), command.actor()), facts);
    }

    /** No write authorization is created, so an expired write deadline does not block observation. */
    public Map<String, Object> verifyCompleted(String runId,
            cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingPlan plan,
            String actor, List<LandingOperationFact> facts) {
        return verify(new ReadContext(runId, plan, actor), facts);
    }

    private Map<String, Object> verify(ReadContext command, List<LandingOperationFact> facts) {
        String id = "lv-" + UUID.randomUUID();
        List<Map<String, Object>> checks = new ArrayList<>();
        for (ChangePackageLandingOperation operation : command.approvedPlan().operations()) {
            checks.add(check(command, operation, facts, id));
        }
        boolean passed = !checks.isEmpty() && checks.stream().allMatch(c -> Boolean.TRUE.equals(c.get("passed")));
        Map<String, Object> proof = Map.of(
                "verificationId", id, "passed", passed, "checks", List.copyOf(checks),
                "criteriaHash", CanonicalObjectHasher.sha256(command.approvedPlan().operations().stream()
                        .map(op -> op.raw().getOrDefault("postCheck", Map.of())).toList()),
                "observedAt", Instant.now().toString(), "verifierVersion", "independent-mcp-postcheck-v1");
        // A database failure must prevent LANDED even when every remote observation passed.
        records.record(id, command.landingRunId(), command.approvedPlan().projectId(),
                command.approvedPlan().packageId(), command.approvedPlan().approvedVersion(),
                command.approvedPlan().approvedPackageHash(), passed, proof);
        return proof;
    }

    private Map<String, Object> check(ReadContext command, ChangePackageLandingOperation operation,
                                      List<LandingOperationFact> facts, String verificationId) {
        Map<String, Object> contract = map(operation.raw().get("postCheck"));
        if (!contract.containsKey("additionalChecks")) {
            return checkContract(command, operation, facts, verificationId, contract);
        }
        Object raw = contract.get("additionalChecks");
        if (!(raw instanceof List<?> additional) || additional.size() > 15
                || additional.stream().anyMatch(item -> !(item instanceof Map<?, ?>)
                    || map(item).isEmpty() || map(item).containsKey("additionalChecks"))) {
            return Map.of("operationId", operation.operationId(), "passed", false,
                    "reasonCode", "LANDING_POSTCHECK_CONTRACT_REQUIRED");
        }
        List<Map<String, Object>> results = new ArrayList<>();
        results.add(checkContract(command, operation, facts, verificationId + ":0", contract));
        for (int i = 0; i < additional.size(); i++) {
            results.add(checkContract(command, operation, facts, verificationId + ":" + (i + 1), map(additional.get(i))));
        }
        return Map.of("operationId", operation.operationId(), "passed",
                results.stream().allMatch(result -> Boolean.TRUE.equals(result.get("passed"))),
                "criteriaHash", CanonicalObjectHasher.sha256(contract), "checks", List.copyOf(results));
    }

    private Map<String, Object> checkContract(ReadContext command,
            ChangePackageLandingOperation operation, List<LandingOperationFact> facts,
            String verificationId, Map<String, Object> contract) {
        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("operationId", operation.operationId());
        proof.put("operationHash", operation.operationHash());
        proof.put("resourceKey", operation.resourceKey());
        proof.put("passed", false);
        LandingOperationFact execution = facts.stream().filter(f -> operation.operationId().equals(f.operationId())
                && f.succeeded() && !f.resultId().isBlank() && f.outputHash().matches("[a-f0-9]{64}"))
                .findFirst().orElse(null);
        if (execution == null) return failed(proof, "LANDING_EXECUTION_RECEIPT_MISSING");
        proof.put("executionResultId", execution.resultId());
        proof.put("executionOutputHash", execution.outputHash());
        proof.put("criteriaHash", CanonicalObjectHasher.sha256(contract));
        Map<String, Object> expected = map(contract.get("expectedValues"));
        String toolsetId = text(contract.get("toolsetId"));
        String toolName = text(contract.get("toolName"));
        String identityField = text(contract.getOrDefault("resourceIdentityField", "resourceKey"));
        if (!toolsetId.startsWith("mcp.") || toolName.isBlank() || expected.isEmpty()
                || !(contract.get("arguments") instanceof Map<?, ?>) || operation.resourceKey().isBlank()
                || !operation.resourceKey().equals(text(expected.get(identityField)))) {
            return failed(proof, "LANDING_POSTCHECK_CONTRACT_REQUIRED");
        }
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("projectId", command.approvedPlan().projectId());
            request.put("sessionId", command.landingRunId());
            request.put("runId", command.landingRunId());
            request.put("toolsetId", toolsetId);
            request.put("toolName", toolName);
            request.put("arguments", contract.get("arguments"));
            request.put("executionScope", "PRE_APPROVAL_WORKFLOW");
            request.put("idempotencyKey", verificationId + ":" + operation.operationId());
            proof.put("readStartedAt", Instant.now().toString());
            Map<String, Object> response = tools.executeReadOnly(request, command.actor());
            var observation = new OpsDirectMcpEvidenceProjection().project(response, toolsetId.substring(4), toolName);
            proof.put("readReceipt", observation.structured());
            proof.put("readFinishedAt", Instant.now().toString());
            Map<String, Object> actual = map(response.get("normalizedContent"));
            for (Map.Entry<String, Object> field : expected.entrySet()) {
                Object value = field(actual, field.getKey());
                if (field.getValue() == null || value == null
                        || !CanonicalObjectHasher.sha256(field.getValue()).equals(CanonicalObjectHasher.sha256(value))) {
                    proof.put("mismatchedField", field.getKey());
                    return failed(proof, "LANDING_POSTCHECK_MISMATCH");
                }
            }
            proof.put("passed", true);
            proof.put("reasonCode", "LANDING_POSTCHECK_PASSED");
            return Map.copyOf(proof);
        } catch (RuntimeException failure) {
            // Preserve durable tool receipts through their run linkage; avoid copying exception secrets.
            proof.put("failureType", failure.getClass().getSimpleName());
            return failed(proof, "LANDING_POSTCHECK_READ_FAILED");
        }
    }

    private Object field(Map<String, Object> actual, String path) {
        String[] parts = path.split("\\.", -1);
        if (parts.length > 8) return null;
        Object value = actual;
        for (String part : parts) {
            if (part.isBlank()) return null;
            if (value instanceof Map<?, ?> map) {
                if (!map.containsKey(part)) return null;
                value = map.get(part);
            } else if (value instanceof List<?> list && part.matches("0|[1-9][0-9]{0,8}")) {
                int index = Integer.parseInt(part);
                if (index >= list.size()) return null;
                value = list.get(index);
            } else {
                return null;
            }
        }
        return value;
    }

    private Map<String, Object> failed(Map<String, Object> proof, String reason) {
        proof.put("reasonCode", reason);
        return Map.copyOf(proof);
    }

    private Map<String, Object> map(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> source) source.forEach((k, v) -> result.put(String.valueOf(k), v));
        return result;
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }

    private record ReadContext(String landingRunId,
            cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingPlan approvedPlan, String actor) {
        ReadContext {
            if (landingRunId == null || landingRunId.isBlank() || approvedPlan == null
                    || actor == null || actor.isBlank()) {
                throw new IllegalArgumentException("LANDING_VERIFICATION_IDENTITY_REQUIRED");
            }
        }
    }
}
