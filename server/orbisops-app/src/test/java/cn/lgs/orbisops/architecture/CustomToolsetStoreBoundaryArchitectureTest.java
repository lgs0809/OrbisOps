package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomToolsetStoreBoundaryArchitectureTest {

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
    void legacyCustomToolsetServiceMustRemainADtoFacade()
            throws IOException {
        String facade = read(TRIGGER
                + "cn/lgs/orbisops/trigger/ops/toolset/"
                + "OpsCustomToolsetService.java");

        assertAll(
                () -> assertTrue(facade.contains(
                        "CustomToolsetStoreApplicationService storeService")),
                () -> assertTrue(facade.contains("storeService.list(")),
                () -> assertTrue(facade.contains("storeService.upsert(")),
                () -> assertTrue(facade.contains("storeService.setEnabled(")),
                () -> assertFalse(facade.contains("JdbcTemplate")),
                () -> assertFalse(facade.contains("DataAccessException")),
                () -> assertFalse(facade.contains("@Value")),
                () -> assertFalse(facade.contains("@PostConstruct")),
                () -> assertFalse(facade.contains("ConcurrentHashMap")),
                () -> assertFalse(facade.contains("CREATE TABLE")),
                () -> assertFalse(facade.contains("INSERT INTO ai_ops_toolset")),
                () -> assertFalse(facade.contains("UPDATE ai_ops_toolset")));
    }

    @Test
    void infrastructureStoreMustOwnJdbcJsonSchemaAndFallback()
            throws IOException {
        String adapter = read(INFRASTRUCTURE
                + "JdbcCustomToolsetStoreAdapter.java");

        assertAll(
                () -> assertTrue(adapter.contains(
                        "implements CustomToolsetStorePort")),
                () -> assertTrue(adapter.contains("JdbcTemplate")),
                () -> assertTrue(adapter.contains("JSON.toJSONString")),
                () -> assertTrue(adapter.contains("JSON.parseArray")),
                () -> assertTrue(adapter.contains("ConcurrentHashMap")),
                () -> assertTrue(adapter.contains("CREATE TABLE IF NOT EXISTS ai_ops_toolset")),
                () -> assertTrue(adapter.contains("INSERT INTO ai_ops_toolset")),
                () -> assertTrue(adapter.contains("UPDATE ai_ops_toolset")),
                () -> assertFalse(adapter.contains("OpsToolsetDefinition")),
                () -> assertFalse(adapter.contains("cn.lgs.orbisops.trigger")));
    }

    @Test
    void applicationStoreBoundaryMustRemainFrameworkNeutral()
            throws IOException {
        String application = read(APPLICATION
                + "CustomToolDefinitionRecord.java")
                + read(APPLICATION + "CustomToolsetRecord.java")
                + read(APPLICATION + "CustomToolsetStorePort.java")
                + read(APPLICATION
                        + "CustomToolsetStoreApplicationService.java");

        assertAll(
                () -> assertFalse(application.contains("org.springframework")),
                () -> assertFalse(application.contains("JdbcTemplate")),
                () -> assertFalse(application.contains("java.sql")),
                () -> assertFalse(application.contains("com.alibaba.fastjson")),
                () -> assertFalse(application.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(application.contains("OpsToolsetDefinition")));
    }

    @Test
    void existingDomainAndApplicationPolicyMustRemainAuthoritative()
            throws IOException {
        String policy = read(DOMAIN + "ToolsetPolicy.java");
        String useCase = read(APPLICATION + "ToolsetApplicationService.java");
        String configuration = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/toolset/"
                + "OpsToolsetApplicationConfiguration.java");

        assertAll(
                () -> assertTrue(policy.contains("DANGEROUS_COMMAND_TERMS")),
                () -> assertTrue(policy.contains("requiresChangePackage")),
                () -> assertTrue(policy.contains("requiresApproval")),
                () -> assertTrue(useCase.contains("policy.customToolset(")),
                () -> assertTrue(useCase.contains("auditPort.record(")),
                () -> assertTrue(configuration.contains(
                        "CustomToolsetStorePort storePort")),
                () -> assertTrue(configuration.contains(
                        "new CustomToolsetStoreApplicationService(storePort)")));
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
