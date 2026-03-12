package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsWorkSessionRunControlBoundaryArchitectureTest {

    private static final String APPLICATION =
            "orbisops-application/src/main/java/"
                    + "cn/lgs/orbisops/application/worksession/run/";
    private static final String TRIGGER =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/application/worksession/";
    private static final String CHAT =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/application/ops/"
                    + "OpsChatApplicationService.java";

    @Test
    void runControlProcessMustRemainInApplicationAndChatServiceMustDelegate() throws IOException {
        String useCase = read(APPLICATION + "WorkSessionRunControlUseCase.java");
        String runPort = read(APPLICATION + "WorkSessionRunControlPort.java");
        String cancelPort = read(APPLICATION + "WorkSessionLocalCancellationPort.java");
        String schedulerPort = read(APPLICATION + "WorkSessionResumeSchedulerPort.java");
        String facade = read(TRIGGER + "OpsWorkSessionRunApplicationFacade.java");
        String adapter = read(TRIGGER + "OpsWorkSessionRunControlAdapter.java");
        String chat = read(CHAT);

        assertAll(
                () -> assertTrue(useCase.contains("public final class WorkSessionRunControlUseCase")),
                () -> assertTrue(useCase.contains("runPort.assertActorCanRead")),
                () -> assertTrue(useCase.contains("resumeScheduler.schedule(request)")),
                () -> assertTrue(useCase.contains("if (persisted) localCancellation.markCanceled")),
                () -> assertFalse(useCase.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(useCase.contains("org.springframework")),
                () -> assertFalse(useCase.contains("Map<String, Object>")),
                () -> assertTrue(runPort.contains("interface WorkSessionRunControlPort")),
                () -> assertTrue(cancelPort.contains("interface WorkSessionLocalCancellationPort")),
                () -> assertTrue(schedulerPort.contains("interface WorkSessionResumeSchedulerPort")),
                () -> assertTrue(adapter.contains("implements WorkSessionRunControlPort")),
                () -> assertTrue(facade.contains("new WorkSessionRunControlUseCase")),
                () -> assertTrue(chat.contains("workSessionRunFacade.events")),
                () -> assertTrue(chat.contains("workSessionRunFacade.resume")),
                () -> assertTrue(chat.contains("workSessionRunFacade.requestCancel")),
                () -> assertFalse(chat.contains("workSessionRunService.requestCancel")),
                () -> assertFalse(chat.contains("workSessionRunService.resumeRequest")),
                () -> assertFalse(chat.contains("if (persisted) cancellationRegistry.markCanceled")),
                () -> assertFalse(chat.contains("return Map.of(\n                \"runId\", runId")));
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
