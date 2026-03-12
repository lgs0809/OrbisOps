package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextMemoryProjectionArchitectureTest {

    private static final String POLICY = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/memory/service/ContextMemoryProjectionPolicy.java";
    private static final String SERVICE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/OpsContextMemoryService.java";
    private static final String STORE_SERVICE = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/ContextMemoryStoreApplicationService.java";
    private static final String ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsMemoryPostProcessingAdapter.java";

    @Test
    void domainPolicyOwnsExtractedItemProjectionAndStableIdentity() throws IOException {
        String policy = read(POLICY);

        assertAll(
                () -> assertTrue(policy.contains("ColdMemoryItemSnapshot")),
                () -> assertTrue(policy.contains("ContextMemorySnapshot")),
                () -> assertTrue(policy.contains("ContextMemoryDefinitionPolicy")),
                () -> assertTrue(policy.contains("MemoryContentHashPolicy")),
                () -> assertTrue(policy.contains("scopeType = memoryType.startsWith")),
                () -> assertTrue(policy.contains("metadata.get(\"projectId\")")),
                () -> assertTrue(policy.contains("metadata.get(\"title\")")),
                () -> assertTrue(policy.contains("metadata.get(\"summary\")")),
                () -> assertTrue(policy.contains("metadata.get(\"memory_status\")")),
                () -> assertTrue(policy.contains("memory_extractor")),
                () -> assertTrue(policy.contains("ctx-mem-")),
                () -> assertTrue(policy.contains("abbreviate(content, 240)")),
                () -> assertFalse(policy.contains("org.springframework")),
                () -> assertFalse(policy.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(policy.contains("cn.lgs.orbisops.infrastructure")),
                () -> assertFalse(policy.contains("lombok")));
    }

    @Test
    void applicationStorePersistsTypedProjectionAndFacadeOnlyDelegates() throws IOException {
        String service = read(SERVICE);
        String storeService = read(STORE_SERVICE);

        assertAll(
                () -> assertTrue(service.contains("ContextMemoryApplicationFacade")),
                () -> assertFalse(service.contains("ContextMemoryStoreApplicationService")),
                () -> assertTrue(service.contains("List<ColdMemoryItemSnapshot>")),
                () -> assertTrue(service.contains("applicationFacade.saveExtractedItems(items)")),
                () -> assertFalse(service.contains("ContextMemoryProjectionPolicy")),
                () -> assertFalse(service.contains("projectionPolicy.project(item)")),
                () -> assertFalse(service.contains("repository.upsert(snapshot)")),
                () -> assertFalse(service.contains("List<OpsMemoryItem>")),
                () -> assertFalse(service.contains("OpsMemoryTextUtils.stableHash(")),
                () -> assertFalse(service.contains("OpsMemoryTextUtils.abbreviate(")),
                () -> assertFalse(service.contains("scopeType = memoryType.startsWith")),
                () -> assertFalse(service.contains("metadata.get(\"projectId\")")),
                () -> assertTrue(storeService.contains("ContextMemoryProjectionPolicy")),
                () -> assertTrue(storeService.contains("projectionPolicy.project(item)")),
                () -> assertTrue(storeService.contains("repository.upsert(snapshot)")));
    }

    @Test
    void postProcessingAdapterPassesTypedSnapshotsWithoutReverseMapping() throws IOException {
        String adapter = read(ADAPTER);

        assertAll(
                () -> assertTrue(adapter.contains("List<ColdMemoryItemSnapshot> items")),
                () -> assertTrue(adapter.contains("contextMemoryService.saveExtractedItems(items)")),
                () -> assertFalse(adapter.contains("contextMemoryService.saveExtractedItems(coldMemoryMapper.views(items))")));
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
