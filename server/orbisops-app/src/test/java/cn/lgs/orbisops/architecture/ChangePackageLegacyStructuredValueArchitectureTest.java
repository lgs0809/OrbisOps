package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackageLegacyStructuredValueArchitectureTest {

    private static final String SERVICE = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/changepackage/service/";

    @Test
    void onePackagePrivateHelperOwnsLegacyStructuredDecoding() throws IOException {
        String helper = read(SERVICE + "ChangePackageLegacyStructuredValue.java");

        assertAll(
                () -> assertTrue(helper.contains("final class ChangePackageLegacyStructuredValue")),
                () -> assertFalse(helper.contains("public final class")),
                () -> assertTrue(helper.contains("static Object decode(Object raw)")),
                () -> assertTrue(helper.contains("CanonicalJson.parseObject(trimmed)")),
                () -> assertTrue(helper.contains("CanonicalJson.parseArray(trimmed)")),
                () -> assertFalse(helper.contains("org.springframework")));
    }

    @Test
    void factoriesDelegateLegacyDecodingWithoutDuplicateParsers() throws IOException {
        String approval = read(SERVICE + "ChangePackageApprovalAssessmentFactory.java");
        String landing = read(SERVICE + "ChangePackageLandingPlanFactory.java");
        String snapshot = read(SERVICE + "ChangePackageSnapshotFactory.java");
        String validation = read(SERVICE + "ChangePackageValidationWritebackFactory.java");
        String cleanup = read(SERVICE + "ChangePackageCleanupPolicy.java");

        assertAll(
                () -> assertTrue(approval.contains("ChangePackageLegacyStructuredValue.decode(raw)")),
                () -> assertTrue(landing.contains("ChangePackageLegacyStructuredValue.decode(raw)")),
                () -> assertTrue(snapshot.contains("ChangePackageLegacyStructuredValue.decode(raw)")),
                () -> assertTrue(validation.contains("ChangePackageLegacyStructuredValue.decode(raw)")),
                () -> assertTrue(cleanup.contains("ChangePackageLegacyStructuredValue.decode(raw)")),
                () -> assertFalse(approval.contains("parseMaybeJson")),
                () -> assertFalse(landing.contains("parseMaybeJson")),
                () -> assertFalse(snapshot.contains("parseMaybeJson")),
                () -> assertFalse(validation.contains("parseMaybeJson")),
                () -> assertFalse(approval.contains("CanonicalJson.parse")),
                () -> assertFalse(landing.contains("CanonicalJson.parse")),
                () -> assertFalse(snapshot.contains("CanonicalJson.parse")),
                () -> assertFalse(validation.contains("CanonicalJson.parse")),
                () -> assertFalse(cleanup.contains("CanonicalJson.parse")));
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
