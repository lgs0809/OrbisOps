package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemorySelectionReferenceApplicationArchitectureTest {

    private static final String HASH_POLICY = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/memory/service/MemoryContentHashPolicy.java";
    private static final String APPLICATION_SERVICE = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/MemorySelectionReferenceApplicationService.java";
    private static final String FACADE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/OpsMemoryFacade.java";
    private static final String QUERY_SERVICE = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/memory/MemoryQueryApplicationService.java";
    private static final String MAPPER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsMemorySelectionReferenceMapper.java";
    private static final String CONFIGURATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/memory/OpsMemoryApplicationConfiguration.java";

    @Test
    void domainPolicyOwnsLegacyCompatibleStableHash() throws IOException {
        String policy = read(HASH_POLICY);

        assertAll(
                () -> assertTrue(policy.contains("MessageDigest.getInstance(\"MD5\")")),
                () -> assertTrue(policy.contains("StandardCharsets.UTF_8")),
                () -> assertFalse(policy.contains("DigestUtils")),
                () -> assertFalse(policy.contains("org.springframework")),
                () -> assertFalse(policy.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(policy.contains("cn.lgs.orbisops.infrastructure")));
    }

    @Test
    void applicationServiceOwnsReferenceFieldsClockAndDefaults() throws IOException {
        String service = read(APPLICATION_SERVICE);

        assertAll(
                () -> assertTrue(service.contains("MemoryContentHashPolicy")),
                () -> assertTrue(service.contains("Clock clock")),
                () -> assertTrue(service.contains("clock.instant().toString()")),
                () -> assertTrue(service.contains("contentHash")),
                () -> assertTrue(service.contains("sourceMessageHash")),
                () -> assertTrue(service.contains("false")),
                () -> assertFalse(service.contains("org.springframework")),
                () -> assertFalse(service.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(service.contains("cn.lgs.orbisops.infrastructure")),
                () -> assertFalse(service.contains("Map<String, Object>")));
    }

    @Test
    void queryServiceDelegatesReferenceAssemblyAndFacadeOwnsNoDetails() throws IOException {
        String facade = read(FACADE);
        String queryService = read(QUERY_SERVICE);
        String mapper = read(MAPPER);
        String configuration = read(CONFIGURATION);

        assertAll(
                () -> assertTrue(facade.contains("MemoryQueryApplicationService")),
                () -> assertFalse(facade.contains("MemorySelectionReferenceApplicationService")),
                () -> assertFalse(facade.contains("OpsMemorySelectionReferenceMapper")),
                () -> assertFalse(facade.contains("referenceService.assemble(")),
                () -> assertFalse(facade.contains("referenceMapper.views(")),
                () -> assertFalse(facade.contains("private Map<String, Object> contextMemoryRef(")),
                () -> assertFalse(facade.contains("OpsMemoryTextUtils.stableHash(")),
                () -> assertFalse(facade.contains("Instant.now()")),
                () -> assertFalse(facade.contains("new LinkedHashMap")),
                () -> assertFalse(facade.contains("ref.put(")),
                () -> assertTrue(queryService.contains("MemorySelectionReferenceApplicationService")),
                () -> assertTrue(queryService.contains("referenceService.assemble(")),
                () -> assertTrue(mapper.contains("Map<String, Object> view")),
                () -> assertTrue(mapper.contains("view.put(\"memoryId\"")),
                () -> assertTrue(mapper.contains("view.put(\"verified\"")),
                () -> assertTrue(configuration.contains("memorySelectionReferenceApplicationService(")),
                () -> assertTrue(configuration.contains("Clock.systemUTC()")));
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
