package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeToolContributorOrderArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void builtInContributorOrderMustRemainDeterministic() throws IOException {
        String registry = read("OpsRuntimeToolContributorRegistry.java");
        String repair = read("OpsRepairRuntimeToolContributor.java");
        String change = read("OpsChangePackageRuntimeToolContributor.java");
        String inspection = read("OpsInspectionTaskRuntimeToolContributor.java");
        String channel = read("OpsChannelRuntimeToolContributor.java");

        assertAll(
                () -> assertTrue(registry.contains("comparingInt(OpsRuntimeToolContributor::order)")),
                () -> assertTrue(registry.contains("thenComparing(item -> normalizeId(item.id()))")),
                () -> assertTrue(repair.contains("return 100")),
                () -> assertTrue(change.contains("return 200")),
                () -> assertTrue(inspection.contains("return 300")),
                () -> assertTrue(channel.contains("return 400")),
                () -> assertTrue(repair.contains("return \"repair\"")),
                () -> assertTrue(change.contains("return \"change-package\"")),
                () -> assertTrue(inspection.contains("return \"inspection-task\"")),
                () -> assertTrue(channel.contains("return \"channel\"")));
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
