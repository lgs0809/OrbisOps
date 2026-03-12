package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkSessionLifecycleBoundaryArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void lifecycleCoordinatorMustRemainAStableFacade() throws IOException {
        String lifecycle = read(RUNTIME + "OpsWorkSessionLifecycleCoordinator.java");

        assertAll(
                () -> assertTrue(lifecycle.contains(
                        "private final OpsWorkSessionPreparationCoordinator preparationCoordinator;")),
                () -> assertTrue(lifecycle.contains(
                        "private final OpsWorkSessionEngineExecutor engineExecutor;")),
                () -> assertTrue(lifecycle.contains(
                        "private final OpsWorkSessionTerminalCoordinator terminalCoordinator;")),
                () -> assertTrue(lifecycle.contains(
                        "private final OpsWorkSessionCapabilityPresenter capabilityPresenter;")),
                () -> assertTrue(lifecycle.contains("catch (OpsRunCanceledException error)")),
                () -> assertFalse(lifecycle.contains("OpsTaskContextAdapter")),
                () -> assertFalse(lifecycle.contains("OpsWorkSessionFinalizer.Hooks")),
                () -> assertFalse(lifecycle.contains("runtimeEventJournal.record(")),
                () -> assertFalse(lifecycle.contains("engineDispatcher.executePlan(")),
                () -> assertFalse(lifecycle.contains("conversationContextCoordinator.appendMessage(")),
                () -> assertTrue(lifecycle.contains("isWorkSessionSuspension")),
                () -> assertTrue(lifecycle.contains("terminalCoordinator.waitingApproval(context, pending)")),
                () -> assertTrue(lifecycle.lines().count() < 120));
    }

    @Test
    void preparationCoordinatorMustOwnPreEngineLifecycleOnly() throws IOException {
        String preparation = read(RUNTIME + "OpsWorkSessionPreparationCoordinator.java");

        assertAll(
                () -> assertTrue(preparation.contains("RUN_ACCEPTED")),
                () -> assertTrue(preparation.contains("RUNTIME_PLANNED")),
                () -> assertTrue(preparation.contains("RUN_STARTED")),
                () -> assertTrue(preparation.contains("prepareMainQuestion(")),
                () -> assertTrue(preparation.contains("bindContextBundle(")),
                () -> assertFalse(preparation.contains("OpsWorkSessionFinalizer.Hooks")),
                () -> assertFalse(preparation.contains("engineDispatcher.executePlan(")),
                () -> assertFalse(preparation.contains("recordSkillUsage(")),
                () -> assertTrue(preparation.lines().count() < 190));
    }

    @Test
    void engineExecutorMustOwnOnlyCancellationGuardAndDispatch() throws IOException {
        String engine = read(RUNTIME + "OpsWorkSessionEngineExecutor.java");

        assertAll(
                () -> assertTrue(engine.contains("requestControl.assertNotCanceled(")),
                () -> assertTrue(engine.contains("engineDispatcher.executePlan(")),
                () -> assertTrue(engine.contains("WORK_SESSION_NOT_PREPARED")),
                () -> assertFalse(engine.contains("OpsWorkSessionFinalizer")),
                () -> assertFalse(engine.contains("OpsTelemetryService")),
                () -> assertFalse(engine.contains("OpsRuntimeEventJournal")),
                () -> assertTrue(engine.lines().count() < 50));
    }

    @Test
    void terminalCoordinatorMustOwnFinalizerAdaptationAndHooks() throws IOException {
        String terminal = read(RUNTIME + "OpsWorkSessionTerminalCoordinator.java");

        assertAll(
                () -> assertTrue(terminal.contains("workSessionFinalizer.success(")),
                () -> assertTrue(terminal.contains("workSessionFinalizer.canceled(")),
                () -> assertTrue(terminal.contains("workSessionFinalizer.failed(")),
                () -> assertTrue(terminal.contains("new OpsWorkSessionFinalizer.Hooks()")),
                () -> assertTrue(terminal.contains("recordSkillUsage(")),
                () -> assertTrue(terminal.contains("finishDurable(")),
                () -> assertFalse(terminal.contains("engineDispatcher.executePlan(")),
                () -> assertFalse(terminal.contains("RUN_ACCEPTED")),
                () -> assertTrue(terminal.lines().count() < 220));
    }

    @Test
    void capabilityPresenterMustOwnOnlyRuleTreeProjection() throws IOException {
        String presenter = read(RUNTIME + "OpsWorkSessionCapabilityPresenter.java");

        assertAll(
                () -> assertTrue(presenter.contains("describeRuleTree(")),
                () -> assertTrue(presenter.contains("adapterKeys(")),
                () -> assertFalse(presenter.contains("OpsWorkSessionFinalizer")),
                () -> assertFalse(presenter.contains("OpsRuntimeEvent")),
                () -> assertFalse(presenter.contains("executePlan(")),
                () -> assertTrue(presenter.lines().count() < 35));
    }

    @Test
    void lifecycleBoundariesMustAvoidDynamicInjectionAndPersistenceLeakage() throws IOException {
        String combined = read(RUNTIME + "OpsWorkSessionLifecycleCoordinator.java")
                + read(RUNTIME + "OpsWorkSessionPreparationCoordinator.java")
                + read(RUNTIME + "OpsWorkSessionEngineExecutor.java")
                + read(RUNTIME + "OpsWorkSessionTerminalCoordinator.java")
                + read(RUNTIME + "OpsWorkSessionCapabilityPresenter.java");

        assertAll(
                () -> assertFalse(combined.contains("ObjectProvider")),
                () -> assertFalse(combined.contains("@Autowired")),
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
