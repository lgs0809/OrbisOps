package cn.lgs.orbisops.trigger.ops.toolset;

import com.alibaba.fastjson2.JSON;
import cn.lgs.orbisops.application.execution.ExecutionResourceRuntimeDirectoryApplicationService;
import cn.lgs.orbisops.domain.execution.model.ExecutionAdapterType;
import cn.lgs.orbisops.domain.execution.model.ExecutionResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
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
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Physical local Java artifact deployment for approved Landing only. */
@Component
public final class OpsLocalJavaServiceToolExecutionHandler implements OpsLocalToolExecutionHandler {

    public static final String ADAPTER = "LOCAL_JAVA_SERVICE";
    public static final String DEPLOY = "artifact_deploy";
    public static final String ROLLBACK = "artifact_rollback";

    private final ExecutionResourceRuntimeDirectoryApplicationService resources;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public OpsLocalJavaServiceToolExecutionHandler(ExecutionResourceRuntimeDirectoryApplicationService resources) {
        if (resources == null) throw new IllegalArgumentException("EXECUTION_RESOURCE_DIRECTORY_REQUIRED");
        this.resources = resources;
    }

    @Override
    public Set<String> supportedAdapterTypes() {
        return Set.of(ADAPTER);
    }

    @Override
    public Map<String, Object> execute(String adapterType, String toolName, OpsLocalToolArguments arguments) {
        if (!ADAPTER.equalsIgnoreCase(text(adapterType))) throw new SecurityException("LOCAL_JAVA_ADAPTER_MISMATCH");
        if (arguments == null) throw new IllegalArgumentException("LOCAL_TOOL_ARGUMENTS_REQUIRED");
        return switch (text(toolName)) {
            case DEPLOY -> deploy(arguments);
            case ROLLBACK -> rollback(arguments);
            default -> throw new SecurityException("LOCAL_JAVA_TOOL_NOT_ALLOWED:" + toolName);
        };
    }

    private Map<String, Object> deploy(OpsLocalToolArguments args) {
        String projectId = required(args.raw("projectId"), "PROJECT_ID_REQUIRED");
        String resourceId = required(args.raw("executionResourceId"), "EXECUTION_RESOURCE_ID_REQUIRED");
        String serviceId = required(args.raw("serviceId"), "SERVICE_ID_REQUIRED");
        String executionKey = required(args.raw("executionKey"), "EXECUTION_KEY_REQUIRED");
        String historyKey = safeId(executionKey);
        String expectedSha = sha(required(args.raw("artifactSha256"), "ARTIFACT_SHA_REQUIRED"));
        ExecutionResource resource = resource(projectId, resourceId);
        Map<String, Object> config = resource.configuration();
        Path allowedArtifactRoot = canonicalExistingDirectory(required(config.get("allowedArtifactRoot"), "ALLOWED_ARTIFACT_ROOT_REQUIRED"));
        Path releaseRoot = canonicalDirectory(required(config.get("releaseRoot"), "RELEASE_ROOT_REQUIRED"));
        Path candidate = canonicalExistingFile(required(args.raw("artifactPath"), "ARTIFACT_PATH_REQUIRED"));
        requireWithin(candidate, allowedArtifactRoot, "ARTIFACT_OUTSIDE_ALLOWED_ROOT");
        String actualSha = digest(candidate);
        if (!expectedSha.equals(actualSha)) throw new SecurityException("ARTIFACT_SHA_MISMATCH");

        Target target = target(config, serviceId, releaseRoot);
        Path historyDir = releaseRoot.resolve(".landing-history").resolve(historyKey).normalize();
        requireWithin(historyDir, releaseRoot, "HISTORY_OUTSIDE_RELEASE_ROOT");
        mkdir(historyDir);
        Path previous = historyDir.resolve("previous.jar");
        Path previousShaFile = historyDir.resolve("previous.sha256");
        boolean hadPrevious = Files.isRegularFile(target.artifactTarget());
        String previousSha = "";
        if (hadPrevious) {
            previousSha = digest(target.artifactTarget());
            copy(target.artifactTarget(), previous);
            writeText(previousShaFile, previousSha);
        } else {
            delete(previous);
            delete(previousShaFile);
        }

        stopOwned(target);
        install(candidate, target.artifactTarget());
        Process process = start(target);
        boolean healthy = waitHealthy(target.healthUrl(), target.healthTimeoutMs());
        if (!healthy) {
            stopProcess(process, target);
            if (hadPrevious) {
                install(previous, target.artifactTarget());
                Process rollbackProcess = start(target);
                boolean rollbackHealthy = waitHealthy(target.healthUrl(), target.healthTimeoutMs());
                if (!rollbackHealthy) {
                    stopProcess(rollbackProcess, target);
                    throw new IllegalStateException("ARTIFACT_DEPLOY_FAILED_ROLLBACK_UNHEALTHY");
                }
            }
            throw new IllegalStateException("ARTIFACT_DEPLOY_HEALTH_CHECK_FAILED_ROLLED_BACK");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "SUCCEEDED");
        result.put("action", "ARTIFACT_DEPLOY");
        result.put("projectId", projectId);
        result.put("executionResourceId", resourceId);
        result.put("serviceId", serviceId);
        result.put("executionKey", executionKey);
        result.put("artifactSha256", actualSha);
        result.put("artifactTarget", target.artifactTarget().toString());
        result.put("previousArtifactPath", hadPrevious ? previous.toString() : "");
        result.put("previousArtifactSha256", previousSha);
        result.put("pid", process.pid());
        result.put("healthUrl", target.healthUrl());
        result.put("health", "UP");
        result.put("observedAt", Instant.now().toString());
        return Map.copyOf(result);
    }

    private Map<String, Object> rollback(OpsLocalToolArguments args) {
        String projectId = required(args.raw("projectId"), "PROJECT_ID_REQUIRED");
        String resourceId = required(args.raw("executionResourceId"), "EXECUTION_RESOURCE_ID_REQUIRED");
        String serviceId = required(args.raw("serviceId"), "SERVICE_ID_REQUIRED");
        String executionKey = required(args.raw("executionKey"), "EXECUTION_KEY_REQUIRED");
        ExecutionResource resource = resource(projectId, resourceId);
        Path releaseRoot = canonicalDirectory(required(resource.configuration().get("releaseRoot"), "RELEASE_ROOT_REQUIRED"));
        Path historyDir = releaseRoot.resolve(".landing-history").resolve(safeId(executionKey)).normalize();
        requireWithin(historyDir, releaseRoot, "HISTORY_OUTSIDE_RELEASE_ROOT");
        Path rollbackArtifact = canonicalExistingFile(historyDir.resolve("previous.jar").toString());
        Path shaFile = canonicalExistingFile(historyDir.resolve("previous.sha256").toString());
        String expectedSha = sha(readText(shaFile));
        if (!expectedSha.equals(digest(rollbackArtifact))) throw new SecurityException("ROLLBACK_ARTIFACT_SHA_MISMATCH");
        Target target = target(resource.configuration(), serviceId, releaseRoot);
        stopOwned(target);
        install(rollbackArtifact, target.artifactTarget());
        Process process = start(target);
        if (!waitHealthy(target.healthUrl(), target.healthTimeoutMs())) {
            stopProcess(process, target);
            throw new IllegalStateException("ARTIFACT_ROLLBACK_HEALTH_CHECK_FAILED");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "SUCCEEDED");
        result.put("action", "ARTIFACT_ROLLBACK");
        result.put("projectId", projectId);
        result.put("executionResourceId", resourceId);
        result.put("serviceId", serviceId);
        result.put("executionKey", executionKey);
        result.put("artifactSha256", expectedSha);
        result.put("pid", process.pid());
        result.put("health", "UP");
        result.put("healthUrl", target.healthUrl());
        result.put("observedAt", Instant.now().toString());
        return Map.copyOf(result);
    }

    private ExecutionResource resource(String projectId, String resourceId) {
        ExecutionResource resource = resources.find(projectId, resourceId)
                .orElseThrow(() -> new IllegalArgumentException("EXECUTION_RESOURCE_NOT_FOUND"));
        if (!resource.enabled()) throw new SecurityException("EXECUTION_RESOURCE_DISABLED");
        if (resource.adapter() != ExecutionAdapterType.LOCAL_JAVA_SERVICE) throw new SecurityException("EXECUTION_RESOURCE_ADAPTER_MISMATCH");
        if (resource.environments().stream().noneMatch(env -> "prod".equalsIgnoreCase(env)
                || "local_production_like".equalsIgnoreCase(env))) {
            throw new SecurityException("EXECUTION_RESOURCE_PRODUCTION_ENV_REQUIRED");
        }
        return resource;
    }

    private Target target(Map<String, Object> config, String serviceId, Path releaseRoot) {
        Map<String, Object> services = map(config.get("services"));
        Map<String, Object> service = map(services.get(serviceId));
        if (service.isEmpty()) throw new IllegalArgumentException("EXECUTION_SERVICE_NOT_FOUND");
        List<Map<String, Object>> targets = mapList(service.get("targets"));
        if (targets.size() != 1) throw new IllegalArgumentException("LOCAL_JAVA_SINGLE_TARGET_REQUIRED");
        Map<String, Object> raw = targets.get(0);
        Path artifactTarget = canonicalDirectoryParent(required(raw.get("artifactTarget"), "ARTIFACT_TARGET_REQUIRED"));
        Path pidFile = canonicalDirectoryParent(required(raw.get("pidFile"), "PID_FILE_REQUIRED"));
        Path logFile = canonicalDirectoryParent(required(raw.get("logFile"), "LOG_FILE_REQUIRED"));
        requireWithin(artifactTarget, releaseRoot, "ARTIFACT_TARGET_OUTSIDE_RELEASE_ROOT");
        requireWithin(pidFile, releaseRoot, "PID_FILE_OUTSIDE_RELEASE_ROOT");
        requireWithin(logFile, releaseRoot, "LOG_FILE_OUTSIDE_RELEASE_ROOT");
        String javaBinary = required(raw.get("javaBinary"), "JAVA_BINARY_REQUIRED");
        if (!"java".equals(javaBinary)) throw new SecurityException("JAVA_BINARY_NOT_ALLOWED");
        List<String> fixedArgs = stringList(raw.get("fixedArgs"));
        Map<String, String> environment = stringMap(raw.get("environment"));
        return new Target(artifactTarget, pidFile, logFile, javaBinary, fixedArgs, environment,
                required(service.get("healthUrl"), "HEALTH_URL_REQUIRED"), integer(service.get("healthTimeoutMs"), 90000));
    }

    private Process start(Target target) {
        try {
            mkdir(target.artifactTarget().getParent());
            mkdir(target.pidFile().getParent());
            mkdir(target.logFile().getParent());
            List<String> command = new java.util.ArrayList<>();
            command.add(target.javaBinary());
            command.add("-jar");
            command.add(target.artifactTarget().toString());
            command.addAll(target.fixedArgs());
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.environment().putAll(target.environment());
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(target.logFile().toFile()));
            builder.redirectError(ProcessBuilder.Redirect.appendTo(target.logFile().toFile()));
            Process process = builder.start();
            Files.writeString(target.pidFile(), Long.toString(process.pid()), StandardCharsets.UTF_8);
            return process;
        } catch (IOException error) {
            throw new IllegalStateException("LOCAL_JAVA_START_FAILED", error);
        }
    }

    private void stopOwned(Target target) {
        if (!Files.isRegularFile(target.pidFile())) return;
        try {
            String raw = Files.readString(target.pidFile(), StandardCharsets.UTF_8).trim();
            if (raw.isBlank()) return;
            long pid = Long.parseLong(raw);
            ProcessHandle.of(pid).ifPresent(handle -> {
                String commandLine = handle.info().commandLine().orElse("");
                if (!commandLine.contains(target.artifactTarget().toString())) {
                    throw new SecurityException("PID_FILE_PROCESS_OWNERSHIP_MISMATCH");
                }
                handle.descendants().forEach(ProcessHandle::destroy);
                handle.destroy();
                try { handle.onExit().get(10, TimeUnit.SECONDS); } catch (Exception ignored) { handle.destroyForcibly(); }
            });
            Files.deleteIfExists(target.pidFile());
        } catch (IOException | NumberFormatException error) {
            throw new IllegalStateException("LOCAL_JAVA_STOP_FAILED", error);
        }
    }

    private void stopProcess(Process process, Target target) {
        if (process != null && process.isAlive()) {
            process.descendants().forEach(ProcessHandle::destroy);
            process.destroy();
            try { process.waitFor(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            if (process.isAlive()) process.destroyForcibly();
        }
        try { Files.deleteIfExists(target.pidFile()); } catch (IOException ignored) { }
    }

    private boolean waitHealthy(String healthUrl, int timeoutMs) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(healthUrl)).timeout(Duration.ofSeconds(3)).GET().build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300
                        && "UP".equalsIgnoreCase(text(JSON.parseObject(response.body()).get("status")))) return true;
            } catch (Exception ignored) { }
            try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
        }
        return false;
    }

    private void install(Path source, Path target) {
        try {
            mkdir(target.getParent());
            Path temp = target.resolveSibling(target.getFileName() + ".landing.tmp");
            Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ignored) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException error) {
            throw new IllegalStateException("ARTIFACT_INSTALL_FAILED", error);
        }
    }

    private void copy(Path source, Path target) {
        try { Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING); }
        catch (IOException error) { throw new IllegalStateException("ARTIFACT_BACKUP_FAILED", error); }
    }

    private void writeText(Path path, String value) {
        try { Files.writeString(path, value, StandardCharsets.UTF_8); }
        catch (IOException error) { throw new IllegalStateException("LANDING_HISTORY_WRITE_FAILED", error); }
    }

    private String readText(Path path) {
        try { return Files.readString(path, StandardCharsets.UTF_8).trim(); }
        catch (IOException error) { throw new IllegalStateException("LANDING_HISTORY_READ_FAILED", error); }
    }

    private void delete(Path path) {
        try { Files.deleteIfExists(path); }
        catch (IOException error) { throw new IllegalStateException("LANDING_HISTORY_DELETE_FAILED", error); }
    }

    private String digest(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                for (int read; (read = input.read(buffer)) >= 0; ) if (read > 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception error) { throw new IllegalStateException("ARTIFACT_HASH_FAILED", error); }
    }

    private Path canonicalExistingFile(String raw) {
        try {
            Path path = Path.of(raw).toRealPath();
            if (!Files.isRegularFile(path)) throw new IllegalArgumentException("ARTIFACT_FILE_REQUIRED");
            return path;
        } catch (IOException error) { throw new IllegalArgumentException("ARTIFACT_FILE_NOT_FOUND", error); }
    }

    private Path canonicalExistingDirectory(String raw) {
        try {
            Path path = Path.of(raw).toRealPath();
            if (!Files.isDirectory(path)) throw new IllegalArgumentException("DIRECTORY_REQUIRED");
            return path;
        } catch (IOException error) { throw new IllegalArgumentException("DIRECTORY_NOT_FOUND", error); }
    }

    private Path canonicalDirectory(String raw) {
        Path path = Path.of(raw).toAbsolutePath().normalize();
        mkdir(path);
        return canonicalExistingDirectory(path.toString());
    }

    private Path canonicalDirectoryParent(String raw) {
        Path path = Path.of(raw).toAbsolutePath().normalize();
        mkdir(path.getParent());
        try { return path.getParent().toRealPath().resolve(path.getFileName()).normalize(); }
        catch (IOException error) { throw new IllegalArgumentException("TARGET_PATH_INVALID", error); }
    }

    private void requireWithin(Path path, Path root, String error) {
        if (!path.normalize().startsWith(root.normalize())) throw new SecurityException(error);
    }

    private void mkdir(Path path) {
        try { if (path != null) Files.createDirectories(path); }
        catch (IOException error) { throw new IllegalStateException("DIRECTORY_CREATE_FAILED", error); }
    }

    private String safeId(String value) {
        if (!value.matches("[A-Za-z0-9._:-]{1,160}")) throw new IllegalArgumentException("EXECUTION_KEY_INVALID");
        return value.replace(':', '_');
    }

    private String sha(String value) {
        String normalized = text(value).toLowerCase();
        if (!normalized.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("ARTIFACT_SHA_INVALID");
        return normalized;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private List<Map<String, Object>> mapList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(this::map).toList();
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(this::text).toList();
    }

    private Map<String, String> stringMap(Object value) {
        Map<String, Object> raw = map(value);
        Map<String, String> result = new LinkedHashMap<>();
        raw.forEach((key, item) -> result.put(key, text(item)));
        return result;
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try { return Integer.parseInt(text(value)); } catch (RuntimeException ignored) { return fallback; }
    }

    private String required(Object value, String error) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }

    private record Target(Path artifactTarget, Path pidFile, Path logFile, String javaBinary,
                          List<String> fixedArgs, Map<String, String> environment,
                          String healthUrl, int healthTimeoutMs) { }
}
