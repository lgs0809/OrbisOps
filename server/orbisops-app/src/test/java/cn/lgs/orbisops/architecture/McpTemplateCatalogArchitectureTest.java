package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpTemplateCatalogArchitectureTest {

    private static final String DOMAIN_ROOT = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/mcp/";
    private static final String APPLICATION_SERVICE = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/mcp/McpTemplateCatalogApplicationService.java";
    private static final String INFRASTRUCTURE_REPOSITORY = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/JdbcMcpTemplateRepository.java";
    private static final String TRIGGER_ADAPTER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/mcp/OpsMcpTemplateAdapter.java";
    private static final String TRIGGER_CONFIGURATION = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/mcp/OpsProgressiveMcpApplicationConfiguration.java";
    private static final String TRIGGER_MAIN = "orbisops-trigger/src/main/java";
    private static final String LEGACY_TRIGGER_SERVICE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/OpsMcpTemplateService.java";

    @Test
    void domainAndApplicationOwnTypedCatalogWithoutFrameworkLeakage() throws IOException {
        String entry = read(DOMAIN_ROOT + "model/McpTemplateCatalogEntry.java");
        String definition = read(DOMAIN_ROOT + "model/McpTemplateDefinition.java");
        String repository = read(DOMAIN_ROOT + "adapter/repository/IMcpTemplateRepository.java");
        String defaults = read(DOMAIN_ROOT + "service/McpTemplateDefaults.java");
        String application = read(APPLICATION_SERVICE);

        assertAll(
                () -> assertTrue(entry.contains("record McpTemplateCatalogEntry")),
                () -> assertTrue(definition.contains("record McpTemplateDefinition")),
                () -> assertTrue(repository.contains("interface IMcpTemplateRepository")),
                () -> assertTrue(repository.contains("McpTemplateCatalogEntry insert")),
                () -> assertTrue(defaults.contains("McpTemplateDefinition")),
                () -> assertTrue(application.contains("IMcpTemplateRepository")),
                () -> assertTrue(application.contains("McpTemplatePolicy")),
                () -> assertFalse(repository.contains("org.springframework")),
                () -> assertFalse(repository.contains("JdbcTemplate")),
                () -> assertFalse(repository.contains("CREATE TABLE")),
                () -> assertFalse(application.contains("org.springframework")),
                () -> assertFalse(application.contains("JdbcTemplate")),
                () -> assertFalse(application.contains("com.alibaba.fastjson")),
                () -> assertFalse(application.contains("CREATE TABLE")));
    }

    @Test
    void infrastructureExclusivelyOwnsJdbcDdlJsonAndDefaultSeeding() throws IOException {
        String infrastructure = read(INFRASTRUCTURE_REPOSITORY);

        assertAll(
                () -> assertTrue(infrastructure.contains("implements IMcpTemplateRepository")),
                () -> assertTrue(infrastructure.contains("JdbcTemplate")),
                () -> assertTrue(infrastructure.contains("CREATE TABLE IF NOT EXISTS ai_ops_mcp_template")),
                () -> assertTrue(infrastructure.contains("com.alibaba.fastjson")),
                () -> assertTrue(infrastructure.contains("McpTemplateDefaults.values()")));
    }

    @Test
    void triggerUsesApplicationCatalogAndLegacyImplementationIsAbsent() throws IOException {
        String adapter = read(TRIGGER_ADAPTER);
        String configuration = read(TRIGGER_CONFIGURATION);
        String triggerMain = readJavaTree(TRIGGER_MAIN);

        assertAll(
                () -> assertTrue(adapter.contains("McpTemplateCatalogApplicationService")),
                () -> assertTrue(adapter.contains("catalogService.listEntries()")),
                () -> assertTrue(adapter.contains("catalogService.createDefinition(definition)")),
                () -> assertTrue(adapter.contains("catalogService.updateStatusDefinition(templateId, status)")),
                () -> assertTrue(configuration.contains("new McpTemplateCatalogApplicationService(repository)")),
                () -> assertFalse(adapter.contains("JdbcTemplate")),
                () -> assertFalse(adapter.contains("CREATE TABLE")),
                () -> assertFalse(configuration.contains("JdbcTemplate")),
                () -> assertFalse(triggerMain.contains("OpsMcpTemplateService")),
                () -> assertFalse(triggerMain.contains("ai_ops_mcp_template")),
                () -> assertFalse(Files.exists(projectRoot().resolve(LEGACY_TRIGGER_SERVICE))));
    }

    private String readJavaTree(String relativeRoot) throws IOException {
        Path root = projectRoot().resolve(relativeRoot);
        StringBuilder source = new StringBuilder();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                source.append(Files.readString(file)).append('\n');
            }
        }
        return source.toString();
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
