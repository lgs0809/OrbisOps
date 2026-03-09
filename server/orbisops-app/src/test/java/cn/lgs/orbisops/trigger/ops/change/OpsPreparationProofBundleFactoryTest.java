package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackagePreparationContext;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsPreparationProofBundleFactoryTest {

    private final OpsPreparationProofBundleFactory factory =
            new OpsPreparationProofBundleFactory();

    @Test
    void activePrepareResultWinsOverUserSuppliedFallback() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("preflightResult", Map.of("status", "FAILED"));
        Map<String, Object> active = Map.of(
                "preflightResult", Map.of(
                        "status", "PASSED",
                        "source", "MCP_RUNTIME"));

        OpsPreparationProofBundleFactory.ProofBundle bundle = factory.create(
                input(request, active, List.of(completeOperation()), List.of(binding(true)), true));

        assertEquals("PASSED", bundle.preflight().get("status"));
        assertEquals("MCP_RUNTIME", bundle.preflight().get("source"));
        assertFalse(bundle.context().preflight().untrusted());
        assertTrue(bundle.context().trustedEvidencePresent());
    }

    @Test
    void userSuppliedPassedProofIsMarkedUntrustedAndCannotSatisfyVerification() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("dryRunResult", Map.of(
                "status", "PASSED",
                "resultId", "user-result"));

        OpsPreparationProofBundleFactory.ProofBundle bundle = factory.create(
                input(request, Map.of(), List.of(completeOperation()), List.of(binding(true)), false));

        assertEquals("UNTRUSTED_USER_INPUT", bundle.dryRun().get("status"));
        assertEquals("PASSED", bundle.dryRun().get("originalStatus"));
        assertEquals(true, bundle.dryRun().get("untrusted"));
        assertTrue(bundle.context().dryRun().untrusted());
        assertEquals(List.of("显式 validation operation 未执行或未通过"), bundle.notVerifiedItems());
    }

    @Test
    void completeOperationAndSchemaBindingProduceOnlyFallbackPreflightPass() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("packageType", "MCP_OPERATION_PACKAGE");
        request.put("riskLevel", "MEDIUM");
        request.put("changedFiles", List.of("src/main/java/App.java"));
        request.put("repairWorkspaceId", "workspace-1");

        OpsPreparationProofBundleFactory.ProofBundle bundle = factory.create(
                input(request, Map.of(), List.of(completeOperation()), List.of(binding(true)), true));
        ChangePackagePreparationContext context = bundle.context();

        assertEquals("PASSED", bundle.preflight().get("status"));
        assertEquals("NOT_SUPPORTED", bundle.dryRun().get("status"));
        assertFalse(context.preflight().untrusted());
        assertTrue(context.toolBindingsComplete());
        assertTrue(context.trustedEvidencePresent());
        assertTrue(context.repairIntent());
        assertEquals("MCP_OPERATION_PACKAGE", context.requestedPackageType());
        assertEquals("MEDIUM", context.requestedRiskLevel());
        assertEquals(List.of("src/main/java/App.java"), context.changedFiles());
        assertEquals("MUTATE_EPHEMERAL", context.operations().get(0).effectType());
        assertEquals(
                List.of("显式 validation operation 未执行或未通过"),
                bundle.notVerifiedItems());
    }

    @Test
    void incompleteBindingKeepsFallbackPreflightFailed() {
        OpsPreparationProofBundleFactory.ProofBundle bundle = factory.create(
                input(Map.of(), Map.of(), List.of(completeOperation()), List.of(binding(false)), false));

        assertEquals("FAILED", bundle.preflight().get("status"));
        assertEquals("PENDING", bundle.preflight().get("toolPolicyCheck"));
        assertFalse(bundle.context().toolBindingsComplete());
        assertTrue(bundle.notVerifiedItems().contains("preflight 未完整通过"));
    }

    private OpsPreparationProofBundleFactory.Input input(
            Map<String, Object> request,
            Map<String, Object> active,
            List<Map<String, Object>> operations,
            List<Map<String, Object>> bindings,
            boolean trustedEvidencePresent) {
        return new OpsPreparationProofBundleFactory.Input(
                request,
                active,
                operations,
                bindings,
                trustedEvidencePresent);
    }

    private Map<String, Object> completeOperation() {
        return Map.of(
                "operationId", "op-1",
                "toolName", "validate_patch",
                "mcpId", "mcp-1",
                "effectType", "MUTATE_TEMP_RESOURCE",
                "effectScope", "VALIDATION_ENVIRONMENT",
                "mutability", "TEST_MUTATING",
                "riskLevel", "MEDIUM",
                "resourceScope", "validation:workspace-1");
    }

    private Map<String, Object> binding(boolean schemaBound) {
        return Map.of(
                "operationId", "op-1",
                "schemaBound", schemaBound);
    }
}
