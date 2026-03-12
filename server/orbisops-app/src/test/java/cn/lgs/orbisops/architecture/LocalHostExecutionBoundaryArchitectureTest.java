package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalHostExecutionBoundaryArchitectureTest {

    private static final String APPLICATION =
            "orbisops-application/src/main/java/"
                    + "cn/lgs/orbisops/application/toolset/";
    private static final String DOMAIN =
            "orbisops-domain/src/main/java/"
                    + "cn/lgs/orbisops/domain/toolset/service/";
    private static final String INFRASTRUCTURE =
            "orbisops-infrastructure/src/main/java/"
                    + "cn/lgs/orbisops/infrastructure/adapter/toolset/";
    private static final String TRIGGER =
            "orbisops-trigger/src/main/java/";

    @Test
    void triggerMustOnlyRouteDockerAndLocalLogProtocols()
            throws IOException {
        String facade = read(TRIGGER
                + "cn/lgs/orbisops/trigger/ops/toolset/"
                + "OpsLocalOpsAdapterService.java");
        String docker = read(TRIGGER
                + "cn/lgs/orbisops/trigger/ops/toolset/"
                + "OpsLocalDockerAdapter.java");
        String logs = read(TRIGGER
                + "cn/lgs/orbisops/trigger/ops/toolset/"
                + "OpsLocalLogAdapter.java");
        String dockerHandler = read(TRIGGER
                + "cn/lgs/orbisops/trigger/ops/toolset/"
                + "OpsDockerLocalToolExecutionHandler.java");
        String logHandler = read(TRIGGER
                + "cn/lgs/orbisops/trigger/ops/toolset/"
                + "OpsLocalLogToolExecutionHandler.java");

        assertAll(
                () -> assertTrue(facade.contains("handlerRegistry.execute(")),
                () -> assertTrue(dockerHandler.contains("adapter.execute(toolName, arguments)")),
                () -> assertTrue(logHandler.contains("adapter.execute(toolName, arguments)")),
                () -> assertFalse(facade.contains("localHostService.run(")),
                () -> assertFalse(facade.contains("localHostService.tail(")),
                () -> assertFalse(facade.contains("localHostService.search(")),
                () -> assertFalse(facade.contains("localHostService.dockerName(")),
                () -> assertTrue(docker.contains("service.run(")),
                () -> assertTrue(docker.contains("service.dockerName(")),
                () -> assertTrue(logs.contains("service.tail(")),
                () -> assertTrue(logs.contains("service.search(")),
                () -> assertFalse(docker.contains("ProcessBuilder")),
                () -> assertFalse(logs.contains("Files.lines")),
                () -> assertFalse(docker.contains("java.nio.file.Path")),
                () -> assertFalse(logs.contains("LOCAL_LOG_PATH_NOT_ALLOWED")),
                () -> assertFalse(docker.contains("DOCKER_COMPOSE_PATH_NOT_ALLOWED")),
                () -> assertFalse(docker.contains("非法 Docker 资源名称")));
    }

    @Test
    void domainAndApplicationMustOwnHostPolicyAndUseCases()
            throws IOException {
        String policy = read(DOMAIN + "LocalHostPolicy.java");
        String application = read(APPLICATION
                + "LocalHostCommandResult.java")
                + read(APPLICATION + "LocalHostCommandPort.java")
                + read(APPLICATION + "LocalLogFilePort.java")
                + read(APPLICATION + "LocalHostApplicationService.java");

        assertAll(
                () -> assertTrue(policy.contains("allowedFile(")),
                () -> assertTrue(policy.contains("allowedDirectory(")),
                () -> assertTrue(policy.contains("dockerName(")),
                () -> assertTrue(policy.contains("mask(")),
                () -> assertTrue(application.contains("commandPort.execute(")),
                () -> assertTrue(application.contains("logFilePort.tail(")),
                () -> assertTrue(application.contains("logFilePort.search(")),
                () -> assertFalse(policy.contains("org.springframework")),
                () -> assertFalse(policy.contains("ProcessBuilder")),
                () -> assertFalse(policy.contains("Files.lines")),
                () -> assertFalse(policy.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(application.contains("org.springframework")),
                () -> assertFalse(application.contains("ProcessBuilder")),
                () -> assertFalse(application.contains("Files.lines")),
                () -> assertFalse(application.contains("cn.lgs.orbisops.trigger")));
    }

    @Test
    void infrastructureMustOwnProcessAndFilesystemMechanics()
            throws IOException {
        String process = read(INFRASTRUCTURE
                + "ProcessLocalHostCommandAdapter.java");
        String files = read(INFRASTRUCTURE
                + "FileLocalLogAdapter.java");
        String configuration = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/toolset/"
                + "OpsToolsetApplicationConfiguration.java");

        assertAll(
                () -> assertTrue(process.contains(
                        "implements LocalHostCommandPort")),
                () -> assertTrue(process.contains("ProcessBuilder")),
                () -> assertTrue(process.contains("process.waitFor(")),
                () -> assertTrue(process.contains("destroyForcibly()")),
                () -> assertTrue(files.contains("implements LocalLogFilePort")),
                () -> assertTrue(files.contains("Files.lines(")),
                () -> assertTrue(files.contains("ArrayDeque")),
                () -> assertFalse(process.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(files.contains("cn.lgs.orbisops.trigger")),
                () -> assertTrue(configuration.contains(
                        "LocalHostCommandPort commandPort")),
                () -> assertTrue(configuration.contains(
                        "LocalLogFilePort logFilePort")),
                () -> assertTrue(configuration.contains(
                        "new LocalHostApplicationService(commandPort, logFilePort)")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(
                current.resolve("orbisops-application"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null
                && Files.isDirectory(
                parent.resolve("orbisops-application"))) {
            return parent;
        }
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(
                nested.resolve("orbisops-application"))) {
            return nested;
        }
        throw new IllegalStateException(
                "Cannot locate orbisops reactor root from " + current);
    }
}
