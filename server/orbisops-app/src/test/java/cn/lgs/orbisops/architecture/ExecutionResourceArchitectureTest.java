package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionResourceArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/execution/";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/execution/";
    private static final String INFRASTRUCTURE = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/JdbcExecutionResourceRepository.java";
    private static final String TRIGGER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/";
    private static final String LEGACY = TRIGGER
            + "ops/change/OpsExecutionResourceService.java";

    @Test
    void domainOwnsAggregateRepositoryConfigurationAndCapabilityRules() throws IOException {
        String aggregate = read(DOMAIN + "model/ExecutionResource.java");
        String draft = read(DOMAIN + "model/ExecutionResourceDraft.java");
        String repository = read(DOMAIN + "adapter/repository/IExecutionResourceRepository.java");
        String policy = read(DOMAIN + "service/ExecutionResourcePolicy.java");
        String configuration = read(DOMAIN + "service/ExecutionResourceConfigurationPolicy.java");
        String domain = aggregate + draft + repository + policy + configuration;

        assertAll(
                () -> assertTrue(aggregate.contains("record ExecutionResource")),
                () -> assertTrue(repository.contains("interface IExecutionResourceRepository")),
                () -> assertTrue(policy.contains("ExecutionResourceCapability capability")),
                () -> assertTrue(policy.contains("assertWorkerResourceUnique")),
                () -> assertTrue(configuration.contains("MYSQL_SET_GLOBAL_VARIABLE")),
                () -> assertTrue(configuration.contains("REDIS_CONFIG_SET")),
                () -> assertTrue(configuration.contains("RABBITMQ_UPSERT_POLICY")
                        || policy.contains("RABBITMQ_UPSERT_POLICY")),
                () -> assertFalse(domain.contains("org.springframework")),
                () -> assertFalse(domain.contains("JdbcTemplate")),
                () -> assertFalse(domain.contains("CREATE TABLE")),
                () -> assertFalse(domain.contains("com.alibaba.fastjson")));
    }

    @Test
    void applicationOwnsCommandsQueriesAndRuntimePublicationOrder() throws IOException {
        String command = read(APPLICATION + "ExecutionResourceCommandApplicationService.java");
        String query = read(APPLICATION + "ExecutionResourceQueryApplicationService.java");
        String directory = read(APPLICATION + "ExecutionResourceRuntimeDirectoryApplicationService.java");
        String projectPort = read(APPLICATION + "ExecutionResourceProjectPort.java");
        String application = command + query + directory + projectPort;

        assertAll(
                () -> assertTrue(command.contains("IExecutionResourceRepository")),
                () -> assertTrue(command.contains("ExecutionResourceProjectPort")),
                () -> assertTrue(command.contains("ExecutionResourcePolicy")),
                () -> assertTrue(command.indexOf("repository.save(candidate)")
                        < command.indexOf("directory.publish(saved)")),
                () -> assertTrue(directory.contains("ConcurrentHashMap")),
                () -> assertTrue(directory.contains("repository.findAllVisible()")),
                () -> assertTrue(query.contains("proposalCatalog")),
                () -> assertTrue(query.contains("workerResources")),
                () -> assertFalse(application.contains("org.springframework")),
                () -> assertFalse(application.contains("JdbcTemplate")),
                () -> assertFalse(application.contains("CREATE TABLE")),
                () -> assertFalse(application.contains("com.alibaba.fastjson")));
    }

    @Test
    void infrastructureExclusivelyOwnsJdbcDdlAndJsonMapping() throws IOException {
        String infrastructure = read(INFRASTRUCTURE);

        assertAll(
                () -> assertTrue(infrastructure.contains("implements IExecutionResourceRepository")),
                () -> assertTrue(infrastructure.contains("JdbcTemplate")),
                () -> assertTrue(infrastructure.contains("CREATE TABLE IF NOT EXISTS ai_ops_execution_resource")),
                () -> assertTrue(infrastructure.contains("UPDATE ai_ops_execution_resource")),
                () -> assertTrue(infrastructure.contains("INSERT INTO ai_ops_execution_resource")),
                () -> assertFalse(infrastructure.contains("ON DUPLICATE KEY UPDATE")),
                () -> assertTrue(infrastructure.contains("com.alibaba.fastjson")),
                () -> assertTrue(infrastructure.contains("ExecutionResource resource(ResultSet")));
    }

    @Test
    void triggerUsesTypedApplicationPortsAndLegacyFacadeIsDeleted() throws IOException {
        String adapter = read(TRIGGER + "application/execution/OpsExecutionResourceAdapter.java");
        String configuration = read(TRIGGER + "application/execution/OpsExecutionApplicationConfiguration.java");
        String generatedQuery = read(TRIGGER
                + "application/execution/OpsExecutionAdapterGeneratedTargetQueryAdapter.java");
        String provisioning = read(TRIGGER
                + "application/execution/OpsExecutionTargetProvisioningAdapter.java");
        String agentCapabilityCatalog = read(TRIGGER
                + "application/agentdefinition/OpsAgentCapabilityCatalogService.java");
        String agentCapabilityPolicy = read(TRIGGER
                + "application/agentdefinition/OpsAgentCapabilityBindingPolicy.java");
        String agentDefinitions = read(TRIGGER
                + "application/ops/OpsAgentDefinitionApplicationService.java");
        String sourceExecutionAdapter = read(TRIGGER
                + "application/source/OpsSourceExecutionResourceAdapter.java");
        String production = readJavaTreeExcluding(TRIGGER, LEGACY);

        assertAll(
                () -> assertTrue(adapter.contains("ExecutionResourceCommandApplicationService")),
                () -> assertTrue(adapter.contains("ExecutionResourceQueryApplicationService")),
                () -> assertTrue(adapter.contains("OpsExecutionResourceMapper")),
                () -> assertTrue(configuration.contains("ExecutionResourceRuntimeDirectoryApplicationService")),
                () -> assertTrue(generatedQuery.contains("ExecutionResourceQueryApplicationService")),
                () -> assertTrue(provisioning.contains("ExecutionResourceCommandApplicationService")),
                () -> assertTrue(agentCapabilityCatalog.contains("ExecutionResourceQueryApplicationService")),
                () -> assertTrue(agentCapabilityPolicy.contains("ExecutionResourceQueryApplicationService")),
                () -> assertFalse(agentDefinitions.contains("ExecutionResourceQueryApplicationService")),
                () -> assertTrue(sourceExecutionAdapter.contains("implements SourceExecutionResourcePort")),
                () -> assertTrue(sourceExecutionAdapter.contains("ExecutionResourceQueryApplicationService")),
                () -> assertFalse(production.contains("OpsExecutionResourceService")),
                () -> assertFalse(production.contains("ai_ops_execution_resource")),
                () -> assertFalse(adapter.contains("com.alibaba.fastjson")),
                () -> assertFalse(adapter.contains("JdbcTemplate")),
                () -> assertFalse(Files.exists(projectRoot().resolve(LEGACY))));
    }

    private String readJavaTreeExcluding(String relativeRoot, String excludedFile) throws IOException {
        Path root = projectRoot().resolve(relativeRoot);
        Path excluded = projectRoot().resolve(excludedFile).normalize();
        StringBuilder source = new StringBuilder();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.normalize().equals(excluded))
                    .toList()) {
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
