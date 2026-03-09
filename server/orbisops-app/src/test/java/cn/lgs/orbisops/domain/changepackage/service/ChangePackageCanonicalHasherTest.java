package cn.lgs.orbisops.domain.changepackage.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ChangePackageCanonicalHasherTest {

    @Test
    void canonicalHashIsStableAcrossMapInsertionOrder() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("projectId", "project-1");
        first.put("arguments", Map.of("limit", 100, "query", "up"));
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("arguments", new LinkedHashMap<>(Map.of("query", "up", "limit", 100)));
        second.put("projectId", "project-1");

        assertEquals(ChangePackageCanonicalHasher.canonicalHash(first),
                ChangePackageCanonicalHasher.canonicalHash(second));
    }

    @Test
    void packageHashIgnoresPersistenceLifecycleFields() {
        Map<String, Object> before = packageSnapshot();
        Map<String, Object> after = new LinkedHashMap<>(before);
        after.put("packageHash", "stored-hash");
        after.put("createTime", "2026-07-17T10:00:00Z");
        after.put("updateTime", "2026-07-17T11:00:00Z");
        after.put("approvedAt", "2026-07-17T12:00:00Z");

        assertEquals(ChangePackageCanonicalHasher.packageHash(before),
                ChangePackageCanonicalHasher.packageHash(after));
    }

    @Test
    void applyingAndRecalculatingOperationHashesUsesOneContract() {
        Map<String, Object> operation = operation();

        ChangePackageCanonicalHasher.OperationHashes applied = ChangePackageCanonicalHasher.applyOperationHashes(operation);
        ChangePackageCanonicalHasher.OperationHashes recalculated = ChangePackageCanonicalHasher.calculateOperationHashes(operation);

        assertEquals(applied, recalculated);
        assertEquals(applied.argumentsHash(), operation.get("argumentsHash"));
        assertEquals(applied.preconditionHash(), operation.get("preconditionHash"));
        assertEquals(applied.postCheckHash(), operation.get("postCheckHash"));
        assertEquals(applied.rollbackHash(), operation.get("rollbackHash"));
        assertEquals(applied.operationHash(), operation.get("operationHash"));
    }

    @Test
    void argumentTamperingChangesSegmentAndOperationHashes() {
        Map<String, Object> operation = operation();
        ChangePackageCanonicalHasher.OperationHashes approved = ChangePackageCanonicalHasher.applyOperationHashes(operation);

        operation.put("arguments", Map.of("service", "order-service", "replicas", 5));
        ChangePackageCanonicalHasher.OperationHashes tampered = ChangePackageCanonicalHasher.calculateOperationHashes(operation);

        assertNotEquals(approved.argumentsHash(), tampered.argumentsHash());
        assertNotEquals(approved.operationHash(), tampered.operationHash());
        assertEquals(approved.preconditionHash(), tampered.preconditionHash());
    }

    private Map<String, Object> packageSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("packageId", "cp-1");
        snapshot.put("version", 2);
        snapshot.put("projectId", "project-1");
        snapshot.put("operations", List.of(operation()));
        return snapshot;
    }

    private Map<String, Object> operation() {
        Map<String, Object> operation = new LinkedHashMap<>();
        operation.put("operationId", "op-1");
        operation.put("adapterType", "MCP");
        operation.put("toolsetId", "deployment-tools");
        operation.put("toolName", "scale_service");
        operation.put("arguments", Map.of("service", "order-service", "replicas", 3));
        operation.put("preconditions", Map.of("currentReplicas", 2));
        operation.put("postCheck", Map.of("expectedReplicas", 3));
        operation.put("rollbackPlan", Map.of("replicas", 2));
        operation.put("resourceScope", Map.of("service", "order-service"));
        operation.put("riskLevel", "HIGH");
        return operation;
    }
}
