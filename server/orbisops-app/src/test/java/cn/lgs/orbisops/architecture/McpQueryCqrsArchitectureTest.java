package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpQueryCqrsArchitectureTest {

    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/mcp/";
    private static final String TRIGGER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/";

    @Test
    void runtimeSelectionQueryMustNotOwnGovernanceHistoryOrSnapshotQueries() throws IOException {
        String selection = read(APPLICATION + "SelectRuntimeMcpToolsQuery.java");
        String catalog = read(APPLICATION + "McpRuntimeCatalogQueryService.java");
        String policy = read(APPLICATION + "McpPolicyQueryService.java");
        String history = read(APPLICATION + "McpRuntimeHistoryQueryService.java");
        String snapshots = read(APPLICATION + "McpToolSnapshotQueryService.java");
        String views = read(APPLICATION + "McpRuntimeViewMapper.java");

        assertAll(
                () -> assertTrue(selection.contains("McpRuntimeCatalogQueryService catalog")),
                () -> assertTrue(selection.contains("runtimeCatalog(")),
                () -> assertTrue(selection.contains("runtimeExecutableTools(")),
                () -> assertFalse(selection.contains("IMcpRuntimeCatalogRepository")),
                () -> assertFalse(selection.contains("IMcpToolPolicyRepository")),
                () -> assertFalse(selection.contains("IMcpToolSnapshotRepository")),
                () -> assertFalse(selection.contains("runtimeActivations(")),
                () -> assertFalse(selection.contains("policies(")),
                () -> assertFalse(selection.contains("snapshots(")),
                () -> assertFalse(selection.contains("decisions(")),
                () -> assertFalse(selection.contains("calls(")),
                () -> assertTrue(selection.lines().count() < 70),
                () -> assertTrue(catalog.contains("IMcpToolPolicyRepository policies")),
                () -> assertTrue(catalog.contains("McpToolPolicyGovernance governance")),
                () -> assertTrue(policy.contains("reviewedPolicySnapshots(")),
                () -> assertTrue(policy.contains("policyModels(")),
                () -> assertTrue(history.contains("IMcpRuntimeCatalogRepository runtimeCatalog")),
                () -> assertTrue(history.contains("findRoutingDecisions")),
                () -> assertTrue(history.contains("findToolCalls")),
                () -> assertTrue(snapshots.contains("IMcpToolSnapshotRepository snapshots")),
                () -> assertTrue(views.contains("policyView(")),
                () -> assertTrue(views.contains("activationView(")),
                () -> assertTrue(views.contains("snapshotView(")),
                () -> assertTrue(views.contains("decisionView(")),
                () -> assertTrue(views.contains("toolCallView(")));
    }

    @Test
    void governanceAndAdminConsumersMustUseNarrowQueryServices() throws IOException {
        String governance = read(APPLICATION + "McpGovernanceApplicationService.java");
        String controller = read(TRIGGER + "http/admin/OpsProgressiveMcpAdminController.java");
        String reviewed = read(TRIGGER + "application/project/OpsProjectMcpReviewedPolicyAdapter.java");

        assertAll(
                () -> assertFalse(governance.contains("SelectRuntimeMcpToolsQuery")),
                () -> assertTrue(governance.contains("McpRuntimeHistoryQueryService historyQueries")),
                () -> assertTrue(governance.contains("McpToolSnapshotQueryService snapshotQueries")),
                () -> assertTrue(governance.contains("McpPolicyQueryService policyQueries")),
                () -> assertFalse(controller.contains("SelectRuntimeMcpToolsQuery")),
                () -> assertTrue(controller.contains("McpRuntimeHistoryQueryService historyQueries")),
                () -> assertTrue(controller.contains("McpToolSnapshotQueryService snapshotQueries")),
                () -> assertTrue(controller.contains("McpPolicyQueryService policyQueries")),
                () -> assertTrue(reviewed.contains("McpPolicyQueryService policies")),
                () -> assertFalse(reviewed.contains("SelectRuntimeMcpToolsQuery")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-application"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-application"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-application"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
