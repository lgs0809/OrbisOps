package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GovernedMemoryHashDomainArchitectureTest {

    private static final String POLICY = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/memory/service/GovernedMemoryHashPolicy.java";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/GovernedMemoryApplicationService.java";
    private static final String STORE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/memory/OpsMemoryStoreService.java";

    @Test
    void domainOwnsSha256IdempotencyOrderingAndConflictComparison() throws IOException {
        String policy = read(POLICY);

        assertAll(
                () -> assertTrue(policy.contains("CanonicalObjectHasher.sha256Text(")),
                () -> assertTrue(policy.contains("CanonicalObjectHasher.sha256(Map.of(")),
                () -> assertTrue(policy.contains("idempotencyKey(GovernedMemoryDraft draft")),
                () -> assertTrue(policy.contains("draft.scope().name()")),
                () -> assertTrue(policy.contains("draft.scopeId()")),
                () -> assertTrue(policy.contains("draft.type().name()")),
                () -> assertTrue(policy.contains("draft.normalizedContent()")),
                () -> assertTrue(policy.contains("memoryHash(GovernedMemoryDraft draft, int version)")),
                () -> assertTrue(policy.contains("memoryHash(String canonicalSnapshotJson)")),
                () -> assertTrue(policy.contains("conflicts(String existingHash, String incomingHash)")),
                () -> assertFalse(policy.contains("com.alibaba.fastjson")),
                () -> assertFalse(policy.contains("org.springframework")),
                () -> assertFalse(policy.contains("JdbcTemplate")),
                () -> assertFalse(policy.contains("cn.lgs.orbisops.trigger")));
    }

    @Test
    void applicationDelegatesDigestToDomainPolicyWithoutUtilityAdapters() throws IOException {
        String application = read(APPLICATION);
        String store = read(STORE);

        assertAll(
                () -> assertTrue(application.contains("GovernedMemoryHashPolicy")),
                () -> assertTrue(application.contains("hashPolicy.idempotencyKey(draft, command.sourceRunId())")),
                () -> assertTrue(application.contains("hashPolicy.memoryHash(draft, version)")),
                () -> assertTrue(application.contains("hashPolicy.conflicts(")),
                () -> assertFalse(application.contains("GovernedMemoryCanonicalSnapshotPort")),
                () -> assertFalse(application.contains("com.alibaba.fastjson")),
                () -> assertFalse(store.contains("GovernedMemoryHashPolicy")),
                () -> assertFalse(store.contains("MessageDigest")),
                () -> assertFalse(store.contains("StandardCharsets")),
                () -> assertFalse(store.contains("HexFormat")),
                () -> assertFalse(store.contains("private String sha256(")),
                () -> assertFalse(store.contains("JSON.toJSONString(Map.of(")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-trigger"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-trigger"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-trigger"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
