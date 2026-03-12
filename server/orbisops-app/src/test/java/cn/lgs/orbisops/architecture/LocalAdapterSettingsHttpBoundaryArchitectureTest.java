package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalAdapterSettingsHttpBoundaryArchitectureTest {

    private static final String TOOLSET =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/ops/toolset/";
    private static final String CONFIGURATION =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/application/toolset/"
                    + "OpsLocalAdapterConfiguration.java";

    @Test
    void localAdapterConfigurationInputAndHttpMustHaveExplicitOwners() throws IOException {
        String facade = read(TOOLSET + "OpsLocalOpsAdapterService.java");
        String settings = read(TOOLSET + "OpsLocalAdapterSettings.java");
        String arguments = read(TOOLSET + "OpsLocalToolArguments.java");
        String http = read(TOOLSET + "OpsLocalHttpTransport.java");
        String configuration = read(CONFIGURATION);

        assertAll(
                () -> assertTrue(facade.contains("OpsLocalAdapterSettings settings")),
                () -> assertTrue(facade.contains("new OpsLocalToolArguments(arguments)")),
                () -> assertFalse(facade.contains("@Value")),
                () -> assertFalse(facade.contains("${orbisops.")),
                () -> assertFalse(facade.contains("HttpClient")),
                () -> assertFalse(facade.contains("HttpRequest")),
                () -> assertFalse(facade.contains("JSON.")),
                () -> assertFalse(facade.contains("replaceAll(")),
                () -> assertTrue(settings.contains("http://127.0.0.1:9090")),
                () -> assertTrue(settings.contains("http://127.0.0.1:9200")),
                () -> assertTrue(settings.contains("\"./logs\"")),
                () -> assertTrue(settings.contains("65_536")),
                () -> assertTrue(settings.contains("Math.max(1, timeoutSeconds)")),
                () -> assertTrue(settings.contains("Math.max(1, maxRows)")),
                () -> assertTrue(settings.contains("Math.max(1024, maxResponseBytes)")),
                () -> assertFalse(settings.contains("org.springframework")),
                () -> assertFalse(settings.contains("HttpClient")),
                () -> assertTrue(arguments.contains("Collections.unmodifiableMap(")),
                () -> assertTrue(arguments.contains("public Object raw(")),
                () -> assertTrue(arguments.contains("public String required(")),
                () -> assertTrue(arguments.contains("public int boundedInt(")),
                () -> assertFalse(arguments.contains("org.springframework")),
                () -> assertTrue(http.contains("HttpClient.newBuilder()")),
                () -> assertTrue(http.contains("HttpRequest.newBuilder(")),
                () -> assertTrue(http.contains("Duration.ofSeconds(settings.timeoutSeconds())")),
                () -> assertTrue(http.contains("settings.maxResponseBytes()")),
                () -> assertTrue(http.contains("replaceAll(")),
                () -> assertTrue(http.contains("interface Sender")),
                () -> assertFalse(http.contains("LocalMySqlApplicationService")),
                () -> assertFalse(http.contains("LocalRedisApplicationService")),
                () -> assertFalse(http.contains("LocalHostApplicationService")),
                () -> assertFalse(http.contains("@Service")),
                () -> assertFalse(http.contains("@Value")),
                () -> assertTrue(configuration.contains(
                        "${orbisops.prometheus-url:http://127.0.0.1:9090}")),
                () -> assertTrue(configuration.contains(
                        "${orbisops.elasticsearch-url:http://127.0.0.1:9200}")),
                () -> assertTrue(configuration.contains("${orbisops.elasticsearch-index:}")),
                () -> assertTrue(configuration.contains(
                        "${orbisops.elasticsearch-index-whitelist:${orbisops.elasticsearch-index:}}")),
                () -> assertTrue(configuration.contains(
                        "${orbisops.local-log.allowed-roots:./logs}")),
                () -> assertTrue(configuration.contains(
                        "${orbisops.docker-compose.allowed-roots:./}")),
                () -> assertTrue(configuration.contains(
                        "${orbisops.local-adapter.timeout-seconds:8}")),
                () -> assertTrue(configuration.contains(
                        "${orbisops.local-adapter.max-rows:200}")),
                () -> assertTrue(configuration.contains(
                        "${orbisops.local-adapter.max-response-bytes:65536}")));
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
