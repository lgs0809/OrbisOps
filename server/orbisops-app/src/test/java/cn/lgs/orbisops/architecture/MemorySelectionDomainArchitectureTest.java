package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemorySelectionDomainArchitectureTest {

    private static final String POLICY = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/memory/service/MemorySelectionPolicy.java";
    private static final String ITEM = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/memory/model/MemoryItemCandidate.java";
    private static final String MESSAGE = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/memory/model/MemoryMessageCandidate.java";
    private static final String FACADE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/OpsMemoryFacade.java";
    private static final String QUERY_SERVICE = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/MemoryQueryApplicationService.java";

    @Test
    void domainPolicyOwnsFilteringDedupAndScoring() throws IOException {
        String policy = read(POLICY);
        String item = read(ITEM);
        String message = read(MESSAGE);

        assertAll(
                () -> assertTrue(policy.contains("superseded_by")),
                () -> assertTrue(policy.contains("memory_status")),
                () -> assertTrue(policy.contains("turn_index")),
                () -> assertTrue(policy.contains("recencyWeight(")),
                () -> assertTrue(policy.contains("boundedImportance")),
                () -> assertTrue(policy.contains("roleWeight")),
                () -> assertTrue(policy.contains("distinctItems.merge")),
                () -> assertTrue(policy.contains("distinctMessages.merge")),
                () -> assertTrue(item.contains("String dedupKey()")),
                () -> assertTrue(message.contains("String dedupKey()")),
                () -> assertFalse(policy.contains("org.springframework")),
                () -> assertFalse(policy.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(policy.contains("cn.lgs.orbisops.infrastructure")),
                () -> assertFalse(item.contains("lombok")),
                () -> assertFalse(message.contains("lombok")));
    }

    @Test
    void queryServiceOwnsSelectionAndFacadeOnlyDelegatesUnifiedQuery() throws IOException {
        String facade = read(FACADE);
        String queryService = read(QUERY_SERVICE);

        assertAll(
                () -> assertTrue(facade.contains("MemoryQueryApplicationService")),
                () -> assertFalse(facade.contains("MemorySelectionPolicy")),
                () -> assertFalse(facade.contains("MemorySelectionConfiguration")),
                () -> assertFalse(facade.contains("OpsMemorySelectionMapper")),
                () -> assertFalse(facade.contains("private boolean isActive(")),
                () -> assertFalse(facade.contains("private OpsMemoryItem preferNewerItem(")),
                () -> assertFalse(facade.contains("private OpsMemoryMessage preferNewerMessage(")),
                () -> assertFalse(facade.contains("private double memoryScore(")),
                () -> assertFalse(facade.contains("private double recencyWeight(")),
                () -> assertFalse(facade.contains("private long turnIndex(")),
                () -> assertFalse(facade.contains("Collectors.toMap(OpsMemoryItem::dedupKey")),
                () -> assertTrue(queryService.contains("MemorySelectionPolicy")),
                () -> assertTrue(queryService.contains("MemorySelectionConfiguration")),
                () -> assertTrue(queryService.contains("MemoryItemCandidate itemCandidate")),
                () -> assertTrue(queryService.contains("MemoryMessageCandidate messageCandidate")));
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
