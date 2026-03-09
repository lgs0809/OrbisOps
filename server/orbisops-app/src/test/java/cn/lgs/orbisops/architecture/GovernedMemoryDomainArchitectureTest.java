package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GovernedMemoryDomainArchitectureTest {

    private static final String POLICY = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/memory/service/GovernedMemoryPolicy.java";
    private static final String DRAFT = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/memory/model/GovernedMemoryDraft.java";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/GovernedMemoryApplicationService.java";
    private static final String STORE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/memory/OpsMemoryStoreService.java";

    @Test
    void domainOwnsGovernedMemoryNormalizationDefaultsVersionLifecycleAndLimits() throws IOException {
        String policy = read(POLICY);
        String draft = read(DRAFT);

        assertAll(
                () -> assertTrue(draft.contains("record GovernedMemoryDraft")),
                () -> assertTrue(draft.contains("MemoryScope scope")),
                () -> assertTrue(draft.contains("MemoryType type")),
                () -> assertTrue(draft.contains("MEMORY_CONFIDENCE_INVALID")),
                () -> assertTrue(policy.contains("MemoryScope.require(scopeType)")),
                () -> assertTrue(policy.contains("MemoryType.require(memoryType)")),
                () -> assertTrue(policy.contains("normalizeContent(")),
                () -> assertTrue(policy.contains("MAX_LOGICAL_KEY_LENGTH = 96")),
                () -> assertTrue(policy.contains("MAX_RUNTIME_LIMIT = 50")),
                () -> assertTrue(policy.contains("nextVersion(")),
                () -> assertTrue(policy.contains("status(boolean conflict)")),
                () -> assertTrue(policy.contains("expiresAt(")),
                () -> assertTrue(policy.contains("requireProjectFact(")),
                () -> assertFalse(policy.contains("org.springframework")),
                () -> assertFalse(policy.contains("JdbcTemplate")),
                () -> assertFalse(policy.contains("com.alibaba.fastjson")),
                () -> assertFalse(policy.contains("cn.lgs.orbisops.trigger")));
    }

    @Test
    void applicationUsesDomainRulesWhileLegacyStoreOnlyDelegates() throws IOException {
        String application = read(APPLICATION);
        String store = read(STORE);

        assertAll(
                () -> assertTrue(application.contains("GovernedMemoryPolicy")),
                () -> assertTrue(application.contains("memoryPolicy.draft(")),
                () -> assertTrue(application.contains("memoryPolicy.nextVersion(")),
                () -> assertTrue(application.contains("memoryPolicy.status(conflict)")),
                () -> assertTrue(application.contains("memoryPolicy.expiresAt(")),
                () -> assertTrue(application.contains("memoryPolicy.requireProjectFact(")),
                () -> assertTrue(application.contains("IGovernedMemoryRepository")),
                () -> assertTrue(application.contains("repository.insert(pending)")),
                () -> assertTrue(application.contains("repository.recordAudit(")),
                () -> assertFalse(store.contains("GovernedMemoryPolicy")),
                () -> assertFalse(store.contains("IGovernedMemoryRepository")),
                () -> assertFalse(store.contains("ALLOWED_TYPES")),
                () -> assertFalse(store.contains("ACTIVE_STATUSES")),
                () -> assertFalse(store.contains("normalized.length() <= 96")),
                () -> assertFalse(store.contains("Math.max(1, Math.min(limit, 50))")),
                () -> assertFalse(store.contains("JdbcTemplate")),
                () -> assertFalse(store.contains("CREATE TABLE IF NOT EXISTS ai_ops_memory")),
                () -> assertFalse(store.contains("hashPolicy.memoryHash(")),
                () -> assertFalse(store.contains("private String sha256(")));
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
