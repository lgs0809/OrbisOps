package cn.lgs.orbisops.trigger.ops.toolset;

import cn.lgs.orbisops.application.execution.ExecutionResourceRuntimeDirectoryApplicationService;
import cn.lgs.orbisops.domain.execution.adapter.repository.IExecutionResourceRepository;
import cn.lgs.orbisops.domain.execution.model.ExecutionAdapterType;
import cn.lgs.orbisops.domain.execution.model.ExecutionResource;
import cn.lgs.orbisops.domain.execution.model.ExecutionResourceStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in local acceptance for the real demo-project PROD-LIKE runtime on port 8092.
 * Normal CI skips it. Enable only on a prepared local workstation with the
 * production-like MySQL/Redis/RabbitMQ dependencies running.
 */
@EnabledIfSystemProperty(named = "orbisops.prodlike.landing.acceptance", matches = "true")
class OpsProdLikeJavaLandingAcceptanceTest {

    private static final String PROJECT = "demo-project";
    private static final String RESOURCE = "demo-project-prod-like-runtime";
    private static final String SERVICE = "order-service";
    private static final String HEALTH_URL = "http://127.0.0.1:8092/actuator/health";

    @TempDir
    Path temp;

    @Test
    void deploysVerifiedArtifactTo8092AndRollsBackBaseline() throws Exception {
        Path root = Path.of(requiredProperty("orbisops.prodlike.repository.root")).toAbsolutePath().normalize();
        Path candidate = Path.of(requiredProperty("orbisops.prodlike.candidate.artifact")).toAbsolutePath().normalize();
        Path baseline = Path.of(requiredProperty("orbisops.prodlike.baseline.artifact")).toAbsolutePath().normalize();
        String candidateSha = requiredProperty("orbisops.prodlike.candidate.sha256").toLowerCase();
        assertEquals(candidateSha, sha(candidate));
        assertNotEquals(candidateSha, sha(baseline));
        assertFalse(healthy(), "8092 must be stopped before the opt-in physical Landing acceptance");

        Path artifactRoot = root.resolve(".ops-repair/artifacts").normalize();
        Path releaseRoot = root.resolve(".ops-repair/prod-like/releases").normalize();
        Path target = releaseRoot.resolve("demo-project/order-service.jar").normalize();
        Path pidFile = releaseRoot.resolve("demo-project/order-service.pid").normalize();
        Path logFile = releaseRoot.resolve("demo-project/order-service.log").normalize();
        Path history = releaseRoot.resolve(".landing-history/prod-like-8092-acceptance").normalize();
        assertTrue(candidate.startsWith(artifactRoot));

        Files.createDirectories(target.getParent());
        boolean targetExisted = Files.isRegularFile(target);
        Path originalTarget = temp.resolve("original-target.jar");
        if (targetExisted) Files.copy(target, originalTarget, StandardCopyOption.REPLACE_EXISTING);
        boolean pidExisted = Files.isRegularFile(pidFile);
        String originalPid = pidExisted ? Files.readString(pidFile, StandardCharsets.UTF_8) : "";

        Files.copy(baseline, target, StandardCopyOption.REPLACE_EXISTING);
        String baselineSha = sha(target);

        Map<String, Object> targetConfig = new LinkedHashMap<>();
        targetConfig.put("artifactTarget", target.toString());
        targetConfig.put("pidFile", pidFile.toString());
        targetConfig.put("logFile", logFile.toString());
        targetConfig.put("javaBinary", "java");
        targetConfig.put("fixedArgs", List.of(
                "--spring.profiles.active=dev",
                "--server.port=8092",
                "--spring.datasource.url=jdbc:mysql://127.0.0.1:23307/demo_db?useUnicode=true&characterEncoding=utf8&autoReconnect=true&zeroDateTimeBehavior=convertToNull&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true",
                "--redis.sdk.config.host=127.0.0.1", "--redis.sdk.config.port=27379",
                "--orbisops.config.register.host=127.0.0.1", "--orbisops.config.register.port=27379",
                "--spring.rabbitmq.addresses=127.0.0.1", "--spring.rabbitmq.port=25672"));
        targetConfig.put("environment", Map.of());

        Map<String, Object> service = new LinkedHashMap<>();
        service.put("healthUrl", HEALTH_URL);
        service.put("healthTimeoutMs", 120000);
        service.put("targets", List.of(targetConfig));
        Map<String, Object> configuration = new LinkedHashMap<>();
        configuration.put("allowedArtifactRoot", artifactRoot.toString());
        configuration.put("releaseRoot", releaseRoot.toString());
        configuration.put("services", Map.of(SERVICE, service));

        ExecutionResource resource = new ExecutionResource(
                RESOURCE, PROJECT, "示例 PROD-LIKE Java 运行环境", "local-worker",
                ExecutionAdapterType.LOCAL_JAVA_SERVICE, "", List.of("prod"), configuration,
                ExecutionResourceStatus.ENABLED, LocalDateTime.now(), LocalDateTime.now());
        OpsLocalJavaServiceToolExecutionHandler handler = new OpsLocalJavaServiceToolExecutionHandler(
                new ExecutionResourceRuntimeDirectoryApplicationService(new SingleResourceRepository(resource)));

        long lastPid = -1L;
        try {
            Map<String, Object> deployed = handler.execute(
                    OpsLocalJavaServiceToolExecutionHandler.ADAPTER,
                    OpsLocalJavaServiceToolExecutionHandler.DEPLOY,
                    new OpsLocalToolArguments(Map.of(
                            "projectId", PROJECT,
                            "executionResourceId", RESOURCE,
                            "serviceId", SERVICE,
                            "executionKey", "prod-like-8092-acceptance",
                            "artifactPath", candidate.toString(),
                            "artifactSha256", candidateSha)));
            assertEquals("SUCCEEDED", deployed.get("status"));
            assertEquals(candidateSha, deployed.get("artifactSha256"));
            assertEquals(baselineSha, deployed.get("previousArtifactSha256"));
            assertEquals(candidateSha, sha(target));
            assertTrue(healthy());
            long deployPid = Long.parseLong(String.valueOf(deployed.get("pid")));

            Map<String, Object> rolledBack = handler.execute(
                    OpsLocalJavaServiceToolExecutionHandler.ADAPTER,
                    OpsLocalJavaServiceToolExecutionHandler.ROLLBACK,
                    new OpsLocalToolArguments(Map.of(
                            "projectId", PROJECT,
                            "executionResourceId", RESOURCE,
                            "serviceId", SERVICE,
                            "executionKey", "prod-like-8092-acceptance")));
            assertEquals("SUCCEEDED", rolledBack.get("status"));
            assertEquals(baselineSha, sha(target));
            assertTrue(healthy());
            lastPid = Long.parseLong(String.valueOf(rolledBack.get("pid")));
            assertNotEquals(deployPid, lastPid);
        } finally {
            if (lastPid > 0) stop(lastPid);
            if (Files.isRegularFile(pidFile)) {
                try { stop(Long.parseLong(Files.readString(pidFile, StandardCharsets.UTF_8).trim())); }
                catch (Exception ignored) { }
            }
            if (targetExisted) Files.copy(originalTarget, target, StandardCopyOption.REPLACE_EXISTING);
            else Files.deleteIfExists(target);
            if (pidExisted) Files.writeString(pidFile, originalPid, StandardCharsets.UTF_8);
            else Files.deleteIfExists(pidFile);
            deleteTree(history);
        }
    }

    private boolean healthy() {
        try {
            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(500)).build()
                    .send(HttpRequest.newBuilder(URI.create(HEALTH_URL)).timeout(Duration.ofSeconds(1)).GET().build(),
                            HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 && response.body().contains("\"status\":\"UP\"");
        } catch (Exception ignored) {
            return false;
        }
    }

    private String sha(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(Files.readAllBytes(path));
        return HexFormat.of().formatHex(digest.digest());
    }

    private String requiredProperty(String name) {
        String value = System.getProperty(name, "").trim();
        if (value.isBlank()) throw new IllegalArgumentException("missing system property: " + name);
        return value;
    }

    private void stop(long pid) {
        ProcessHandle.of(pid).ifPresent(handle -> {
            handle.descendants().forEach(ProcessHandle::destroy);
            handle.destroy();
            try { handle.onExit().get(); }
            catch (Exception ignored) { handle.destroyForcibly(); }
        });
    }

    private void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            stream.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (Exception ignored) { }
            });
        } catch (Exception ignored) { }
    }

    private record SingleResourceRepository(ExecutionResource resource) implements IExecutionResourceRepository {
        @Override public List<ExecutionResource> findAllVisible() { return List.of(resource); }
        @Override public Optional<ExecutionResource> find(String projectId, String resourceId) {
            return resource.projectId().equals(projectId) && resource.resourceId().equals(resourceId)
                    ? Optional.of(resource) : Optional.empty();
        }
        @Override public boolean existsWorkerResourceOutsideProject(String workerId, String resourceId, String projectId) { return false; }
        @Override public ExecutionResource save(ExecutionResource resource) { return resource; }
    }
}
