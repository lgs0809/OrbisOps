package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkSessionContextPreparationBoundaryArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void serviceMustRemainAThinPreparationFacade() throws IOException {
        String service = read(RUNTIME + "OpsWorkSessionContextPreparationService.java");

        assertAll(
                () -> assertTrue(service.contains(
                        "private final OpsWorkSessionMemoryContextAssembler memoryContextAssembler;")),
                () -> assertTrue(service.contains(
                        "private final OpsWorkSessionQueryRewriteCoordinator queryRewriteCoordinator;")),
                () -> assertTrue(service.contains("memoryContextAssembler.assemble(")),
                () -> assertTrue(service.contains("queryRewriteCoordinator.rewrite(")),
                () -> assertFalse(service.contains("@Autowired")),
                () -> assertFalse(service.contains("assembleSelection(")),
                () -> assertFalse(service.contains("createBundle(")),
                () -> assertFalse(service.contains("OpsLlmTraceContext")),
                () -> assertFalse(service.contains("refreshAnalysisQuestionContext(")),
                () -> assertTrue(service.lines().count() < 110));
    }

    @Test
    void memoryAssemblerMustOwnSelectionAndSceneMetadata() throws IOException {
        String memory = read(RUNTIME + "OpsWorkSessionMemoryContextAssembler.java");

        assertAll(
                () -> assertTrue(memory.contains("assembleSelection(")),
                () -> assertTrue(memory.contains("memoryRuntimeInjectionService.select(")),
                () -> assertTrue(memory.contains("_authoritativeMemorySelection")),
                () -> assertTrue(memory.contains("OPS_TROUBLESHOOTING")),
                () -> assertTrue(memory.contains("PRE_APPROVAL_WORK_SESSION")),
                () -> assertFalse(memory.contains("OpsLlmTraceContext")),
                () -> assertFalse(memory.contains("questionRewriteService")),
                () -> assertFalse(memory.contains("createBundle(")),
                () -> assertTrue(memory.lines().count() < 180));
    }

    @Test
    void bundleCoordinatorMustOwnPersistenceProjectionAndFailClosed() throws IOException {
        String bundle = read(RUNTIME + "OpsWorkSessionContextBundleCoordinator.java");

        assertAll(
                () -> assertTrue(bundle.contains("runtimeContextBundleService.createBundle(")),
                () -> assertTrue(bundle.contains("BUNDLE_FIELDS")),
                () -> assertTrue(bundle.contains("CONTEXT_BUNDLE_FAILED")),
                () -> assertTrue(bundle.contains("禁止启动 Work Session")),
                () -> assertFalse(bundle.contains("assembleSelection(")),
                () -> assertFalse(bundle.contains("OpsLlmTraceContext")),
                () -> assertFalse(bundle.contains("OpsQuestionContext")),
                () -> assertTrue(bundle.lines().count() < 130));
    }

    @Test
    void rewriteCoordinatorMustOwnGatesTraceAndAnalysisProjection() throws IOException {
        String rewrite = read(RUNTIME + "OpsWorkSessionQueryRewriteCoordinator.java");

        assertAll(
                () -> assertTrue(rewrite.contains("QUERY_REWRITE_STARTED")),
                () -> assertTrue(rewrite.contains("QUERY_REWRITE_SKIPPED")),
                () -> assertTrue(rewrite.contains("QUERY_REWRITE_DEGRADED")),
                () -> assertTrue(rewrite.contains("OpsLlmTraceContext.withTrace(")),
                () -> assertTrue(rewrite.contains("refreshAnalysisQuestionContext(")),
                () -> assertFalse(rewrite.contains("assembleSelection(")),
                () -> assertFalse(rewrite.contains("createBundle(")),
                () -> assertFalse(rewrite.contains("OpsMemorySelection")),
                () -> assertTrue(rewrite.lines().count() < 250));
    }

    @Test
    void boundariesMustUseExplicitConstructionAndAvoidPersistenceLeakage() throws IOException {
        String combined = read(RUNTIME + "OpsWorkSessionContextPreparationService.java")
                + read(RUNTIME + "OpsWorkSessionMemoryContextAssembler.java")
                + read(RUNTIME + "OpsWorkSessionContextBundleCoordinator.java")
                + read(RUNTIME + "OpsWorkSessionQueryRewriteCoordinator.java");

        assertAll(
                () -> assertFalse(combined.contains("ObjectProvider")),
                () -> assertFalse(combined.contains("@Autowired(required = false)")),
                () -> assertFalse(combined.contains("JdbcTemplate")),
                () -> assertFalse(combined.contains("EntityManager")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-trigger"))) return current;
        Path parent = current.getParent();
        if (parent != null
                && Files.isDirectory(parent.resolve("orbisops-trigger"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-trigger"))) return nested;
        throw new IllegalStateException(
                "Cannot locate orbisops reactor root from " + current);
    }
}
