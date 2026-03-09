package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeCriticalContributorBoundaryArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void criticalContributorsMustBeRequiredAndMissingOnesFailClosed() throws IOException {
        String registry = read("OpsRuntimeToolContributorRegistry.java");
        String requirement = read("OpsRuntimeToolContributorRequirement.java");
        String repair = read("OpsRepairRuntimeToolContributor.java");
        String change = read("OpsChangePackageRuntimeToolContributor.java");
        String inspection = read("OpsInspectionTaskRuntimeToolContributor.java");
        String channel = read("OpsChannelRuntimeToolContributor.java");

        assertAll(
                () -> assertTrue(requirement.contains("REQUIRED")),
                () -> assertTrue(requirement.contains("OPTIONAL")),
                () -> assertTrue(registry.contains("CRITICAL_CONTRIBUTOR_IDS")),
                () -> assertTrue(registry.contains("RUNTIME_CRITICAL_TOOL_CONTRIBUTOR_MISSING")),
                () -> assertTrue(registry.contains("RUNTIME_CRITICAL_TOOL_CONTRIBUTOR_NOT_REQUIRED")),
                () -> assertTrue(repair.contains("OpsRuntimeToolContributorRequirement.REQUIRED")),
                () -> assertTrue(change.contains("OpsRuntimeToolContributorRequirement.REQUIRED")),
                () -> assertTrue(inspection.contains("OpsRuntimeToolContributorRequirement.REQUIRED")),
                () -> assertTrue(channel.contains("OpsRuntimeToolContributorRequirement.REQUIRED")),
                () -> assertTrue(change.contains("throw new IllegalStateException")));
    }

    private String read(String file) throws IOException {
        return Files.readString(projectRoot().resolve(RUNTIME + file));
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
