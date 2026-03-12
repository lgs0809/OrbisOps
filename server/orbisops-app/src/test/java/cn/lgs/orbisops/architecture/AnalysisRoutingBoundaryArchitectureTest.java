package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisRoutingBoundaryArchitectureTest {

    @Test
    void investigationConstraintsMustNarrowSourcesWithoutRestoringBusinessIntentRouting() throws IOException {
        String policy = read("orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/ops/runtime/OpsAnalysisRouterSelectionPolicy.java");
        String preparation = read("orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/application/chat/OpsChatRequestPreparationFacade.java");

        assertAll(
                () -> assertTrue(policy.contains("allowedInvestigationSources")),
                () -> assertTrue(policy.contains("excludedCapabilities")),
                () -> assertTrue(policy.contains("explicit request-scoped analysis constraints")),
                () -> assertFalse(policy.contains("intentDecision")),
                () -> assertFalse(policy.contains("_intentAllowedInvestigationSources")),
                () -> assertFalse(policy.contains("import cn.lgs.orbisops.trigger.ops.intent.OpsIntentRouter")),
                () -> assertFalse(policy.contains("import cn.lgs.orbisops.trigger.ops.intent.OpsIntentDecision")),
                () -> assertFalse(preparation.contains("intentRouter.route")),
                () -> assertFalse(preparation.contains("_authoritativeIntentDecision")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-domain"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-domain"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-domain"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
