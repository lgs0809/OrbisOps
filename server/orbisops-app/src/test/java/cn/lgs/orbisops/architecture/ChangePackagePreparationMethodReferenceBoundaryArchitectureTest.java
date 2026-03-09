package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackagePreparationMethodReferenceBoundaryArchitectureTest {

    private static final String TRIGGER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/change/";

    @Test
    void plainFactoryOwnsMethodSnapshotAndSharedCanonicalHash() throws IOException {
        String factory = read(TRIGGER + "OpsPreparationMethodReferenceFactory.java");

        assertAll(
                () -> assertTrue(factory.contains("final class OpsPreparationMethodReferenceFactory")),
                () -> assertTrue(factory.contains("Map<String, Object> create(")),
                () -> assertTrue(factory.contains("CanonicalObjectHasher.sha256(")),
                () -> assertTrue(factory.contains("HASH_EXCLUSIONS")),
                () -> assertTrue(factory.contains("preparationMethodHash")),
                () -> assertTrue(factory.contains("changePackageTemplate")),
                () -> assertTrue(factory.contains("riskGuidance")),
                () -> assertFalse(factory.contains("MessageDigest")),
                () -> assertFalse(factory.contains("TreeMap")),
                () -> assertFalse(factory.contains("HexFormat")),
                () -> assertFalse(factory.contains("com.alibaba.fastjson")),
                () -> assertFalse(factory.contains("org.springframework")),
                () -> assertFalse(factory.contains("@Service")));
    }

    @Test
    void preparationServiceDelegatesWithoutOwningCryptoOrSnapshotFields() throws IOException {
        String preparation = read(TRIGGER + "OpsChangePackagePreparationService.java");

        assertAll(
                () -> assertTrue(preparation.contains("OpsPreparationMethodReferenceFactory PREPARATION_METHOD_REFERENCE_FACTORY")),
                () -> assertTrue(preparation.contains("PREPARATION_METHOD_REFERENCE_FACTORY.create(")),
                () -> assertFalse(preparation.contains("private Map<String, Object> preparationMethodRef(")),
                () -> assertFalse(preparation.contains("canonicalSha256(")),
                () -> assertFalse(preparation.contains("MessageDigest")),
                () -> assertFalse(preparation.contains("StandardCharsets")),
                () -> assertFalse(preparation.contains("HexFormat")),
                () -> assertFalse(preparation.contains("TreeMap")),
                () -> assertFalse(preparation.contains("snapshot.put(\"changePackageTemplate\"")));
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
