package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolProviderExecutionDependencyArchitectureTest {

    private static final String REPAIR = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/repair/OpsRepairToolProvider.java";
    private static final String INSPECTION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/OpsInspectionTaskToolProvider.java";

    @Test
    void repairAndInspectionExecutionServicesMustBeConstructorBound() throws IOException {
        String repair = read(REPAIR);
        String inspection = read(INSPECTION);

        assertAll(
                () -> assertTrue(repair.contains("private final OpsToolExecutionService toolExecutionService")),
                () -> assertTrue(repair.contains("ObjectProvider<OpsToolExecutionService> toolExecutionServiceProvider")),
                () -> assertTrue(repair.contains("this((OpsToolExecutionService) null)")),
                () -> assertTrue(repair.contains("toolExecutionServiceProvider.getIfAvailable()")),
                () -> assertFalse(repair.contains("@Autowired(required = false)")),
                () -> assertFalse(repair.contains("private OpsToolExecutionService toolExecutionService;")),
                () -> assertTrue(inspection.contains("private final OpsToolExecutionService toolExecutionService")),
                () -> assertTrue(inspection.contains("ObjectProvider<OpsToolExecutionService> toolExecutionServiceProvider")),
                () -> assertTrue(inspection.contains("this((OpsToolExecutionService) null)")),
                () -> assertTrue(inspection.contains("toolExecutionServiceProvider.getIfAvailable()")),
                () -> assertFalse(inspection.contains("@Autowired(required = false)")),
                () -> assertFalse(inspection.contains("private OpsToolExecutionService toolExecutionService;")));
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
