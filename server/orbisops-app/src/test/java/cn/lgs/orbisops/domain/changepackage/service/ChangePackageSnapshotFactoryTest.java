package cn.lgs.orbisops.domain.changepackage.service;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageSnapshot;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackageSnapshotFactoryTest {

    private final ChangePackageSnapshotFactory factory = new ChangePackageSnapshotFactory();

    @Test
    void buildsSealedSnapshotAndNormalizesOperationContract() {
        Map<String, Object> source = baseSource();
        source.put("riskLevel", "LOW");
        source.put("mcpSteps", List.of(Map.of(
                "operationId", "op-1", "effectType", "MUTATE_TEMP_RESOURCE",
                "effectScope", "SANDBOX", "mutability", "TEST_MUTATING", "riskLevel", "MEDIUM",
                "arguments", Map.of("namespace", "test"), "preconditions", Map.of("exists", true),
                "postCheck", Map.of("status", "READY"), "rollbackPlan", Map.of("delete", true))));

        ChangePackageSnapshot snapshot = factory.create("cp-1", 1, source, "alice");
        Map<String, Object> values = snapshot.toMap();
        List<Map<String, Object>> operations = JSON.parseObject(String.valueOf(values.get("mcpStepsJson")), List.class);
        Map<String, Object> operation = operations.get(0);

        assertEquals(snapshot.packageHash(), values.get("packageHash"));
        assertEquals("MEDIUM", values.get("riskLevel"));
        assertEquals("MUTATE_EPHEMERAL", operation.get("effectType"));
        assertNotNull(operation.get("operationHash"));
        assertNotNull(operation.get("argumentsHash"));
        assertNotNull(operation.get("preconditionHash"));
        assertNotNull(operation.get("postCheckHash"));
        assertNotNull(operation.get("rollbackHash"));
    }

    @Test
    void preparationAgentSnapshotIsFrozenIntoCanonicalPackageHash() {
        Map<String, Object> source = baseSource();
        source.put("preparationAgentId", "agent-1");
        source.put("preparationAgentVersion", 7);
        source.put("preparationAgentSnapshot", Map.of(
                "agentId", "agent-1",
                "agentVersion", 7,
                "definitionHash", "definition-hash-1"));

        ChangePackageSnapshot first = factory.create("cp-1", 1, source, "alice");
        @SuppressWarnings("unchecked")
        Map<String, Object> frozen = (Map<String, Object>) first.toMap().get("preparationAgentSnapshot");
        assertEquals("agent-1", frozen.get("agentId"));
        assertEquals(7, frozen.get("agentVersion"));
        assertEquals("definition-hash-1", frozen.get("definitionHash"));

        source.put("preparationAgentSnapshot", Map.of(
                "agentId", "agent-1",
                "agentVersion", 7,
                "definitionHash", "definition-hash-2"));
        ChangePackageSnapshot changed = factory.create("cp-1", 1, source, "alice");
        assertFalse(first.packageHash().equals(changed.packageHash()));
    }

    @Test
    void artifactDigestIsFrozenIntoCanonicalPackageHash() {
        Map<String, Object> source = baseSource();
        source.put("artifactDigest", "sha256:" + "a".repeat(64));

        ChangePackageSnapshot first = factory.create("cp-1", 1, source, "alice");
        assertEquals("sha256:" + "a".repeat(64), first.toMap().get("artifactDigest"));

        source.put("artifactDigest", "sha256:" + "b".repeat(64));
        ChangePackageSnapshot changed = factory.create("cp-1", 1, source, "alice");
        assertFalse(first.packageHash().equals(changed.packageHash()));
    }

    @Test
    void requestSuppliedProofCannotBecomeTrusted() {
        Map<String, Object> source = baseSource();
        source.put("dryRunResult", Map.of("status", "PASSED", "trusted", true, "source", "USER_INPUT"));

        Map<String, Object> snapshot = factory.create("cp-1", 1, source, "alice").toMap();
        Map<String, Object> proof = JSON.parseObject(String.valueOf(snapshot.get("dryRunResultJson")));

        assertEquals("REQUEST_SUPPLIED_UNTRUSTED", proof.get("source"));
        assertEquals("REQUEST_SUPPLIED_UNTRUSTED", proof.get("trustLevel"));
        assertFalse(Boolean.TRUE.equals(proof.get("trusted")));
        assertTrue(Boolean.TRUE.equals(proof.get("untrusted")));
    }

    @Test
    void planPortTrustedProofUsesSeparateAuthorityChannel() {
        Map<String, Object> source = baseSource();
        source.put("dryRunResult", Map.of(
                "status", "PASSED",
                "trusted", true,
                "source", "USER_INPUT"));
        Map<String, Object> trustedProofs = Map.of(
                "dryRunResult", Map.of(
                        "status", "PASSED",
                        "source", "OWNING_PREPARE_TOOL_EXECUTION",
                        "verified", true,
                        "executed", true,
                        "toolResultId", "tool-result-1",
                        "outputHash", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));

        Map<String, Object> snapshot = factory.create(
                "cp-1", 1, source, "alice", trustedProofs).toMap();
        Map<String, Object> proof = JSON.parseObject(String.valueOf(snapshot.get("dryRunResultJson")));

        assertEquals("OWNING_PREPARE_TOOL_EXECUTION", proof.get("source"));
        assertEquals("PLAN_PORT_TRUSTED_PROVENANCE", proof.get("trustLevel"));
        assertTrue(Boolean.TRUE.equals(proof.get("trusted")));
        assertFalse(Boolean.TRUE.equals(proof.get("untrusted")));
    }

    @Test
    void rejectsMissingAuthoritativeContextBoundary() {
        Map<String, Object> source = baseSource();
        source.remove("contextBundleHash");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> factory.create("cp-1", 1, source, "alice"));

        assertEquals("CHANGE_PACKAGE_CONTEXT_BUNDLE_HASH_REQUIRED", error.getMessage());
    }

    @Test
    void legacySkillSnapshotDoesNotEnterNewSnapshot() {
        Map<String, Object> source = baseSource();
        source.put("skillSnapshot", Map.of("unsafe", true));
        source.put("skillSnapshotHash", "forged");

        Map<String, Object> snapshot = factory.create("cp-1", 1, source, "alice").toMap();

        assertFalse(snapshot.containsKey("skillSnapshot"));
        assertFalse(snapshot.containsKey("skillSnapshotHash"));
        assertTrue(snapshot.containsKey("usedSkillVersionRefsJson"));
        assertEquals("skill-refs-hash", snapshot.get("usedSkillRefsHash"));
    }

    private Map<String, Object> baseSource() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("projectId", "project-1");
        source.put("packageType", "MCP_OPERATION_PACKAGE");
        source.put("status", "DRAFT");
        source.put("riskLevel", "MEDIUM");
        source.put("contextBundleId", "bundle-1");
        source.put("contextBundleHash", "bundle-hash");
        source.put("usedSkillVersionRefs", List.of());
        source.put("usedSkillRefsHash", "skill-refs-hash");
        source.put("toolsetBoundaryHash", "toolset-boundary-hash");
        source.put("runtimeBoundaryHash", "runtime-boundary-hash");
        source.put("approvalBoundary", Map.of("projectId", "project-1"));
        return source;
    }
}
