package cn.lgs.orbisops.trigger.ops.toolset;

import cn.lgs.orbisops.application.execution.ExecutionResourceRuntimeDirectoryApplicationService;
import cn.lgs.orbisops.domain.execution.adapter.repository.IExecutionResourceRepository;
import cn.lgs.orbisops.domain.execution.model.ExecutionAdapterType;
import cn.lgs.orbisops.domain.execution.model.ExecutionResource;
import cn.lgs.orbisops.domain.execution.model.ExecutionResourceStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsLocalJavaServiceToolExecutionHandlerTest {

    @TempDir
    Path temp;

    @Test
    void shouldPhysicallyDeployAndRollbackPreviousJavaArtifact() throws Exception {
        int port = freePort();
        Path artifacts = Files.createDirectories(temp.resolve("artifacts"));
        Path releases = Files.createDirectories(temp.resolve("releases"));
        Path candidate = jar(artifacts.resolve("candidate-a.jar"), "A");
        Path target = jar(releases.resolve("service.jar"), "B");
        String previousSha = sha(target);
        Path pidFile = releases.resolve("service.pid");
        Path logFile = releases.resolve("service.log");
        String healthUrl = "http://127.0.0.1:" + port + "/actuator/health";

        Map<String, Object> targetConfig = new LinkedHashMap<>();
        targetConfig.put("artifactTarget", target.toString());
        targetConfig.put("pidFile", pidFile.toString());
        targetConfig.put("logFile", logFile.toString());
        targetConfig.put("javaBinary", "java");
        targetConfig.put("fixedArgs", List.of("--server.port=" + port));
        targetConfig.put("environment", Map.of());
        Map<String, Object> service = new LinkedHashMap<>();
        service.put("healthUrl", healthUrl);
        service.put("healthTimeoutMs", 15000);
        service.put("targets", List.of(targetConfig));
        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("allowedArtifactRoot", artifacts.toString());
        configuration.put("releaseRoot", releases.toString());
        configuration.put("services", Map.of("svc", service));

        ExecutionResource resource = new ExecutionResource(
                "prod-like", "project", "prod-like", "worker",
                ExecutionAdapterType.LOCAL_JAVA_SERVICE, "", List.of("prod"), configuration,
                ExecutionResourceStatus.ENABLED, LocalDateTime.now(), LocalDateTime.now());
        ExecutionResourceRuntimeDirectoryApplicationService directory =
                new ExecutionResourceRuntimeDirectoryApplicationService(new SingleResourceRepository(resource));
        OpsLocalJavaServiceToolExecutionHandler handler = new OpsLocalJavaServiceToolExecutionHandler(directory);

        Map<String, Object> deployed = handler.execute(
                OpsLocalJavaServiceToolExecutionHandler.ADAPTER,
                OpsLocalJavaServiceToolExecutionHandler.DEPLOY,
                new OpsLocalToolArguments(Map.of(
                        "projectId", "project",
                        "executionResourceId", "prod-like",
                        "serviceId", "svc",
                        "executionKey", "landing:run:1",
                        "artifactPath", candidate.toString(),
                        "artifactSha256", sha(candidate))));
        try {
            assertEquals("SUCCEEDED", deployed.get("status"));
            assertEquals("landing:run:1", deployed.get("executionKey"));
            assertEquals("A", healthVersion(healthUrl));
            assertEquals(sha(candidate), sha(target));
            assertEquals(previousSha, deployed.get("previousArtifactSha256"));
            assertTrue(Files.isRegularFile(Path.of(String.valueOf(deployed.get("previousArtifactPath")))));

            long deployPid = Long.parseLong(String.valueOf(deployed.get("pid")));
            Map<String, Object> rolledBack = handler.execute(
                    OpsLocalJavaServiceToolExecutionHandler.ADAPTER,
                    OpsLocalJavaServiceToolExecutionHandler.ROLLBACK,
                    new OpsLocalToolArguments(Map.of(
                            "projectId", "project",
                            "executionResourceId", "prod-like",
                            "serviceId", "svc",
                            "executionKey", "landing:run:1")));
            assertEquals("SUCCEEDED", rolledBack.get("status"));
            assertEquals("B", healthVersion(healthUrl));
            assertEquals(previousSha, sha(target));
            long rollbackPid = Long.parseLong(String.valueOf(rolledBack.get("pid")));
            assertNotEquals(deployPid, rollbackPid);
            stop(rollbackPid);
        } finally {
            if (Files.isRegularFile(pidFile)) {
                try {
                    stop(Long.parseLong(Files.readString(pidFile, StandardCharsets.UTF_8).trim()));
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    private Path jar(Path jar, String version) throws Exception {
        Path sourceDir = Files.createDirectories(temp.resolve("src-" + version));
        Path classes = Files.createDirectories(temp.resolve("classes-" + version));
        Path source = sourceDir.resolve("HealthApp.java");
        Files.writeString(source, """
                import com.sun.net.httpserver.HttpServer;
                import java.net.InetSocketAddress;
                import java.nio.charset.StandardCharsets;
                public class HealthApp {
                  public static void main(String[] args) throws Exception {
                    int port = 0;
                    for (String arg : args) if (arg.startsWith("--server.port=")) port = Integer.parseInt(arg.substring(14));
                    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
                    server.createContext("/actuator/health", exchange -> {
                      byte[] body = "{\\\"status\\\":\\\"UP\\\",\\\"version\\\":\\\"%s\\\"}".getBytes(StandardCharsets.UTF_8);
                      exchange.sendResponseHeaders(200, body.length);
                      exchange.getResponseBody().write(body);
                      exchange.close();
                    });
                    server.start();
                  }
                }
                """.formatted(version));
        int compiled = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-d", classes.toString(), source.toString());
        assertEquals(0, compiled);
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "HealthApp");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            output.putNextEntry(new JarEntry("HealthApp.class"));
            Files.copy(classes.resolve("HealthApp.class"), output);
            output.closeEntry();
        }
        return jar;
    }

    private String healthVersion(String url) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        String body = response.body();
        return body.contains("\"version\":\"A\"") ? "A" : body.contains("\"version\":\"B\"") ? "B" : "";
    }

    private int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private String sha(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(Files.readAllBytes(path));
        return HexFormat.of().formatHex(digest.digest());
    }

    private void stop(long pid) {
        ProcessHandle.of(pid).ifPresent(handle -> {
            handle.descendants().forEach(ProcessHandle::destroy);
            handle.destroy();
            try {
                handle.onExit().get();
            } catch (Exception ignored) {
                handle.destroyForcibly();
            }
        });
    }

    private record SingleResourceRepository(ExecutionResource resource) implements IExecutionResourceRepository {
        @Override
        public List<ExecutionResource> findAllVisible() { return List.of(resource); }
        @Override
        public Optional<ExecutionResource> find(String projectId, String resourceId) {
            return resource.projectId().equals(projectId) && resource.resourceId().equals(resourceId)
                    ? Optional.of(resource) : Optional.empty();
        }
        @Override
        public boolean existsWorkerResourceOutsideProject(String workerId, String resourceId, String projectId) { return false; }
        @Override
        public ExecutionResource save(ExecutionResource resource) { return resource; }
    }
}
