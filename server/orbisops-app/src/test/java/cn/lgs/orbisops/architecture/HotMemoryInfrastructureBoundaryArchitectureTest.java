package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotMemoryInfrastructureBoundaryArchitectureTest {

    private static final String INFRASTRUCTURE =
            "orbisops-infrastructure/src/main/java/"
                    + "cn/lgs/orbisops/infrastructure/adapter/memory/";
    private static final String TRIGGER =
            "orbisops-trigger/src/main/java/";

    @Test
    void infrastructureMustOwnMemoryAndRedisStorageMechanics()
            throws IOException {
        String memory = read(INFRASTRUCTURE
                + "InMemoryHotMemoryAdapter.java");
        String redis = read(INFRASTRUCTURE
                + "RedisHotMemoryAdapter.java");

        assertAll(
                () -> assertTrue(memory.contains("implements\n        HotMemoryWritePort")),
                () -> assertTrue(memory.contains("HotMemoryQueryPort")),
                () -> assertTrue(memory.contains("HotMemoryReplacePort")),
                () -> assertTrue(memory.contains("HotMemoryClearPort")),
                () -> assertTrue(memory.contains("ConcurrentHashMap")),
                () -> assertTrue(memory.contains("@ConditionalOnProperty")),
                () -> assertTrue(redis.contains("StringRedisTemplate")),
                () -> assertTrue(redis.contains("opsForList()")),
                () -> assertTrue(redis.contains("JSON.toJSONString")),
                () -> assertTrue(redis.contains("@ConditionalOnClass")),
                () -> assertTrue(redis.contains("havingValue = \"redis\"")),
                () -> assertFalse(memory.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(redis.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(memory.contains("OpsMemoryMessage")),
                () -> assertFalse(redis.contains("OpsMemoryMessage")));
    }

    @Test
    void triggerLegacyHotMemoryCompatibilityLayerMustBeRemoved() {
        Path root = projectRoot();
        assertAll(
                () -> assertFalse(Files.exists(root.resolve(TRIGGER
                        + "cn/lgs/orbisops/trigger/ops/runtime/OpsHotMemoryStore.java"))),
                () -> assertFalse(Files.exists(root.resolve(TRIGGER
                        + "cn/lgs/orbisops/trigger/ops/runtime/OpsInMemoryHotMemoryStore.java"))),
                () -> assertFalse(Files.exists(root.resolve(TRIGGER
                        + "cn/lgs/orbisops/trigger/ops/runtime/OpsRedisHotMemoryStore.java"))),
                () -> assertFalse(Files.exists(root.resolve(TRIGGER
                        + "cn/lgs/orbisops/trigger/application/memory/OpsMemoryCaptureAdapter.java"))),
                () -> assertFalse(Files.exists(root.resolve(TRIGGER
                        + "cn/lgs/orbisops/trigger/application/memory/OpsHotMemoryReplaceAdapter.java"))),
                () -> assertFalse(Files.exists(root.resolve(TRIGGER
                        + "cn/lgs/orbisops/trigger/application/memory/OpsHotMemoryClearAdapter.java"))));
    }

    @Test
    void productionCompositionMustInjectTypedHotMemoryPorts()
            throws IOException {
        String configuration = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/memory/"
                + "OpsMemoryApplicationConfiguration.java");
        String retrieval = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/memory/"
                + "OpsMemoryRetrievalSourceAdapter.java");
        String postProcessing = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/memory/"
                + "OpsMemoryPostProcessingAdapter.java");

        assertAll(
                () -> assertTrue(configuration.contains(
                        "HotMemoryWritePort hotMemoryWritePort")),
                () -> assertTrue(configuration.contains(
                        "HotMemoryQueryPort hotMemoryQueryPort")),
                () -> assertTrue(configuration.contains(
                        "HotMemoryReplacePort hotMemoryReplacePort")),
                () -> assertTrue(configuration.contains(
                        "HotMemoryClearPort hotMemoryClearPort")),
                () -> assertFalse(configuration.contains(
                        "OpsHotMemoryReplaceAdapter hotMemoryReplaceAdapter")),
                () -> assertFalse(retrieval.contains("HotMemoryQueryPort")),
                () -> assertFalse(retrieval.contains("OpsHotMemoryStore")),
                () -> assertTrue(postProcessing.contains(
                        "HotMemoryQueryPort hotMemoryQueryPort")),
                () -> assertTrue(postProcessing.contains(
                        "hotMemoryQueryPort.recent(")),
                () -> assertFalse(postProcessing.contains("OpsHotMemoryStore")));
    }

    @Test
    void infrastructureModuleMustDeclareRedisCompileDependency()
            throws IOException {
        String pom = read("orbisops-infrastructure/pom.xml");

        assertAll(
                () -> assertTrue(pom.contains("org.springframework.data")),
                () -> assertTrue(pom.contains("spring-data-redis")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(
                current.resolve("orbisops-application"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null
                && Files.isDirectory(
                parent.resolve("orbisops-application"))) {
            return parent;
        }
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(
                nested.resolve("orbisops-application"))) {
            return nested;
        }
        throw new IllegalStateException(
                "Cannot locate orbisops reactor root from " + current);
    }
}
