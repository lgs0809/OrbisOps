package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeBuiltInToolBoundaryArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void facadeMustDelegateAllBuiltInContributionToOrderedRegistry() throws IOException {
        String facade = read(RUNTIME + "OpsRuntimeBuiltInToolContributor.java");
        String registry = read(RUNTIME + "OpsRuntimeToolContributorRegistry.java");
        String configuration = read(RUNTIME + "OpsRuntimeBuiltInToolContributorConfiguration.java");

        assertAll(
                () -> assertTrue(facade.contains("OpsRuntimeToolContributorRegistry contributorRegistry")),
                () -> assertTrue(facade.contains("contributorRegistry.contribute(context)")),
                () -> assertFalse(facade.contains("contributeRepairTools(")),
                () -> assertFalse(facade.contains("contributeChangePackageTool(")),
                () -> assertFalse(facade.contains("contributeInspectionTaskTool(")),
                () -> assertFalse(facade.contains("contributeChannelTool(")),
                () -> assertFalse(facade.contains("WorkSessionMetadataKeys")),
                () -> assertFalse(facade.contains("RESOURCE_WARN")),
                () -> assertTrue(registry.contains("List.copyOf(ordered)")),
                () -> assertTrue(configuration.contains("OpsRuntimeToolContributorRegistry contributorRegistry")),
                () -> assertTrue(configuration.contains("new OpsRuntimeBuiltInToolContributor(contributorRegistry)")),
                () -> assertTrue(facade.lines().count() < 60));
    }

    @Test
    void pipelineMustOwnOnlyOneBuiltInContributorRuleAndAssemblerMustRemainThin() throws IOException {
        String pipeline = read(RUNTIME + "OpsRuntimeResourcePipeline.java");
        String assembler = read(RUNTIME + "OpsRuntimeResourceAssembler.java");

        assertAll(
                () -> assertTrue(pipeline.contains(
                        "OpsRuntimeBuiltInToolContributor builtInToolContributor")),
                () -> assertTrue(pipeline.contains(
                        "rule(\"BUILT_IN_TOOLS\", builtInToolContributor::contribute)")),
                () -> assertEquals(1, occurrences(assembler, "public OpsRuntimeResourceAssembler(")),
                () -> assertFalse(assembler.contains("OpsRuntimeBuiltInToolContributor")),
                () -> assertFalse(assembler.contains("OpsRepairToolProvider")),
                () -> assertFalse(assembler.contains("OpsProjectServiceCatalogService")),
                () -> assertFalse(assembler.contains("OpsChangePackageToolProvider")),
                () -> assertFalse(assembler.contains("OpsInspectionTaskToolProvider")),
                () -> assertFalse(assembler.contains("OpsChannelToolProvider")),
                () -> assertFalse(assembler.contains("runtimeRunId(")),
                () -> assertFalse(assembler.contains("runtimeActor(")),
                () -> assertTrue(assembler.lines().count() < 70));
    }

    @Test
    void optionalProvidersMustBeOwnedByDedicatedContributors() throws IOException {
        String repair = read(RUNTIME + "OpsRepairRuntimeToolContributor.java");
        String change = read(RUNTIME + "OpsChangePackageRuntimeToolContributor.java");
        String inspection = read(RUNTIME + "OpsInspectionTaskRuntimeToolContributor.java");
        String channel = read(RUNTIME + "OpsChannelRuntimeToolContributor.java");

        assertAll(
                () -> assertTrue(repair.contains("ObjectProvider<OpsRepairToolProvider>")),
                () -> assertTrue(repair.contains("ObjectProvider<OpsProjectServiceCatalogService>")),
                () -> assertTrue(change.contains("ObjectProvider<OpsChangePackageToolProvider>")),
                () -> assertTrue(inspection.contains("ObjectProvider<OpsInspectionTaskToolProvider>")),
                () -> assertTrue(channel.contains("ObjectProvider<OpsChannelToolProvider>")),
                () -> assertTrue(change.contains("ChangePackage ToolProvider 未返回受控工具")),
                () -> assertTrue(change.contains("contextBundleId")),
                () -> assertTrue(read(RUNTIME + "OpsRuntimeToolContributionSupport.java")
                        .contains("WorkSessionMetadataKeys.OPS_ANALYSIS_REQUEST")));
    }

    private int occurrences(String source, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
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
