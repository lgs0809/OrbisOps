package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceHealthBoundaryArchitectureTest {

    private static final String APPLICATION =
            "orbisops-application/src/main/java/"
                    + "cn/lgs/orbisops/application/resourcehealth/";
    private static final String INFRASTRUCTURE =
            "orbisops-infrastructure/src/main/java/"
                    + "cn/lgs/orbisops/infrastructure/adapter/resourcehealth/";
    private static final String TRIGGER =
            "orbisops-trigger/src/main/java/";

    @Test
    void legacyServiceMustRemainAMapPresenter() throws IOException {
        String facade = read(TRIGGER
                + "cn/lgs/orbisops/trigger/ops/OpsResourceHealthService.java");

        assertAll(
                () -> assertTrue(facade.contains(
                        "ResourceHealthApplicationService applicationService")),
                () -> assertTrue(facade.contains("applicationService.snapshot()")),
                () -> assertTrue(facade.contains("applicationService.capabilities()")),
                () -> assertFalse(facade.contains("JdbcTemplate")),
                () -> assertFalse(facade.contains("HttpClient")),
                () -> assertFalse(facade.contains("@Value")),
                () -> assertFalse(facade.contains("@Autowired")),
                () -> assertFalse(facade.contains("com.alibaba.fastjson")),
                () -> assertFalse(facade.contains("IAiClientToolMcpConfigRepository")),
                () -> assertFalse(facade.contains("OpsChannelNotificationService")),
                () -> assertTrue(facade.lines().count() < 80));
    }

    @Test
    void applicationMustAggregateTypedIndependentProbePorts()
            throws IOException {
        String service = read(APPLICATION
                + "ResourceHealthApplicationService.java");
        String application = service
                + read(APPLICATION + "ResourceHealthCheck.java")
                + read(APPLICATION + "ResourceHealthSnapshot.java")
                + read(APPLICATION + "ResourceCapability.java")
                + read(APPLICATION + "ResourceHealthSettings.java")
                + read(APPLICATION + "MySqlResourceHealthProbePort.java")
                + read(APPLICATION + "PgVectorResourceHealthProbePort.java")
                + read(APPLICATION + "ElasticsearchResourceHealthProbePort.java")
                + read(APPLICATION + "PrometheusResourceHealthProbePort.java")
                + read(APPLICATION + "ModelResourceHealthProbePort.java")
                + read(APPLICATION + "McpRegistryResourceHealthProbePort.java")
                + read(APPLICATION + "ChannelResourceHealthProbePort.java");

        assertAll(
                () -> assertTrue(service.contains("mySqlProbe::probe")),
                () -> assertTrue(service.contains("pgVectorProbe.probe(")),
                () -> assertTrue(service.contains("elasticsearchProbe.probe(")),
                () -> assertTrue(service.contains("prometheusProbe.probe(")),
                () -> assertTrue(service.contains("modelProbe::probe")),
                () -> assertTrue(service.contains("mcpProbe::probe")),
                () -> assertTrue(service.contains("channelProbe::probe")),
                () -> assertTrue(service.contains("Clock clock")),
                () -> assertTrue(service.contains("LocalDateTime.now(clock)")),
                () -> assertFalse(application.contains("ResourceHealthClockPort")),
                () -> assertFalse(application.contains("org.springframework")),
                () -> assertFalse(application.contains("JdbcTemplate")),
                () -> assertFalse(application.contains("java.net.http")),
                () -> assertFalse(application.contains("com.alibaba.fastjson")),
                () -> assertFalse(application.contains("cn.lgs.orbisops.trigger")));
    }

    @Test
    void infrastructureMustOwnJdbcAndHttpMechanics()
            throws IOException {
        String mysql = read(INFRASTRUCTURE
                + "JdbcMySqlResourceHealthProbeAdapter.java");
        String pgVector = read(INFRASTRUCTURE
                + "JdbcPgVectorResourceHealthProbeAdapter.java");
        String http = read(INFRASTRUCTURE
                + "HttpResourceHealthProbeAdapter.java");

        assertAll(
                () -> assertTrue(mysql.contains("implements MySqlResourceHealthProbePort")),
                () -> assertTrue(mysql.contains("JdbcTemplate")),
                () -> assertTrue(mysql.contains("information_schema.tables")),
                () -> assertTrue(pgVector.contains("implements PgVectorResourceHealthProbePort")),
                () -> assertTrue(pgVector.contains("vector_dims(embedding)")),
                () -> assertTrue(http.contains("ElasticsearchResourceHealthProbePort")),
                () -> assertTrue(http.contains("PrometheusResourceHealthProbePort")),
                () -> assertTrue(http.contains("HttpClient")),
                () -> assertTrue(http.contains("JSON.parseObject")),
                () -> assertFalse(mysql.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(pgVector.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(http.contains("cn.lgs.orbisops.trigger")));
    }

    @Test
    void runtimeSpecificAdaptersMustRemainAtTriggerBoundary()
            throws IOException {
        String model = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/resourcehealth/"
                + "OpsModelResourceHealthAdapter.java");
        String mcp = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/resourcehealth/"
                + "OpsMcpRegistryResourceHealthAdapter.java");
        String channel = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/resourcehealth/"
                + "OpsChannelResourceHealthAdapter.java");
        String configuration = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/resourcehealth/"
                + "OpsResourceHealthApplicationConfiguration.java");

        assertAll(
                () -> assertTrue(model.contains("implements ModelResourceHealthProbePort")),
                () -> assertTrue(mcp.contains("implements McpRegistryResourceHealthProbePort")),
                () -> assertTrue(channel.contains("implements ChannelResourceHealthProbePort")),
                () -> assertTrue(configuration.contains("@Configuration")),
                () -> assertTrue(configuration.contains("ResourceHealthSettings")),
                () -> assertTrue(configuration.contains("new ResourceHealthApplicationService(")));
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
