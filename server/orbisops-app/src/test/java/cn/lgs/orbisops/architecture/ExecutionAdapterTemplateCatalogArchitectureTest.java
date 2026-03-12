package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionAdapterTemplateCatalogArchitectureTest {

    private static final String DOMAIN_ROOT = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/execution/";
    private static final String APPLICATION_ROOT = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/execution/";
    private static final String INFRASTRUCTURE_REPOSITORY = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/JdbcExecutionAdapterTemplateRepository.java";
    private static final String LEGACY_TRIGGER_SERVICE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/change/OpsExecutionAdapterTemplateService.java";
    private static final String TRIGGER_ROOT = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/execution/";

    @Test
    void domainAndApplicationOwnTypedTemplateStateWithoutProtocolParsing() throws IOException {
        String model = read(DOMAIN_ROOT + "model/ExecutionAdapterTemplate.java");
        String repository = read(DOMAIN_ROOT + "adapter/repository/IExecutionAdapterTemplateRepository.java");
        String defaults = read(DOMAIN_ROOT + "service/ExecutionAdapterTemplateDefaults.java");
        String policy = read(DOMAIN_ROOT + "service/ExecutionAdapterTemplatePolicy.java");
        String commands = read(APPLICATION_ROOT + "ExecutionAdapterTemplateCommands.java");
        String catalog = read(APPLICATION_ROOT + "ExecutionAdapterTemplateCatalogApplicationService.java");
        String application = read(APPLICATION_ROOT + "ExecutionAdapterTemplateApplicationService.java");
        String port = read(APPLICATION_ROOT + "ExecutionAdapterTemplatePort.java");

        assertAll(
                () -> assertTrue(model.contains("record ExecutionAdapterTemplate")),
                () -> assertTrue(model.contains("ExecutionAdapterType adapterType")),
                () -> assertTrue(model.contains("ExecutionRiskLevel riskLevel")),
                () -> assertTrue(model.contains("ExecutionResourceStatus status")),
                () -> assertTrue(repository.contains("ExecutionAdapterTemplate insert")),
                () -> assertTrue(defaults.contains("mysql-controlled-template")),
                () -> assertTrue(defaults.contains("redis-controlled-template")),
                () -> assertTrue(policy.contains("ExecutionAdapterTemplate create(ExecutionAdapterTemplate candidate)")),
                () -> assertTrue(policy.contains("ExecutionAdapterTemplate update(ExecutionAdapterTemplate current")),
                () -> assertFalse(policy.contains("Map<String, Object>")),
                () -> assertFalse(policy.contains(".get(\"")),
                () -> assertTrue(commands.contains("record Mutation")),
                () -> assertTrue(commands.contains("Field<ExecutionRiskLevel> riskLevel")),
                () -> assertTrue(commands.contains("Field<ExecutionResourceStatus> status")),
                () -> assertTrue(port.contains("ExecutionAdapterTemplate createTemplate(")),
                () -> assertTrue(port.contains("ExecutionAdapterTemplate updateTemplateStatus(")),
                () -> assertFalse(port.contains("Map<String, Object> create(")),
                () -> assertFalse(port.contains("Map<String, Object> updateStatus(")),
                () -> assertTrue(catalog.contains("create(ExecutionAdapterTemplateCommands.Mutation command)")),
                () -> assertTrue(application.contains("create(ExecutionAdapterTemplateCommands.Mutation command)")),
                () -> assertFalse(catalog.contains("normalized.get(")),
                () -> assertFalse(catalog.contains("request.get(")),
                () -> assertFalse(application.contains("command.get(")),
                () -> assertFalse(application.contains("candidate.get(")),
                () -> assertFalse(model.contains("org.springframework")),
                () -> assertFalse(repository.contains("JdbcTemplate")),
                () -> assertFalse(application.contains("org.springframework")),
                () -> assertFalse(catalog.contains("JdbcTemplate")),
                () -> assertFalse(catalog.contains("CREATE TABLE")));
    }

    @Test
    void triggerOwnsAliasesAndInfrastructureOwnsSql() throws IOException {
        String infrastructure = read(INFRASTRUCTURE_REPOSITORY);
        String adapter = read(TRIGGER_ROOT + "OpsExecutionAdapterTemplateAdapter.java");
        String mapper = read(TRIGGER_ROOT + "OpsExecutionAdapterTemplateCommandMapper.java");

        assertAll(
                () -> assertTrue(infrastructure.contains("implements IExecutionAdapterTemplateRepository")),
                () -> assertTrue(infrastructure.contains("JdbcTemplate")),
                () -> assertTrue(infrastructure.contains("CREATE TABLE IF NOT EXISTS ai_ops_execution_adapter_template")),
                () -> assertTrue(infrastructure.contains("ExecutionAdapterTemplateDefaults.values()")),
                () -> assertTrue(infrastructure.contains("JSON.toJSONString")),
                () -> assertFalse(Files.exists(projectRoot().resolve(LEGACY_TRIGGER_SERVICE))),
                () -> assertTrue(mapper.contains("Map<String, Object> request")),
                () -> assertTrue(mapper.contains("adapterTemplateId")),
                () -> assertTrue(mapper.contains("templateId")),
                () -> assertTrue(mapper.contains("ExecutionRiskLevel::require")),
                () -> assertTrue(mapper.contains("ExecutionResourceStatus::require")),
                () -> assertTrue(adapter.contains("catalogService.createTemplate(command)")),
                () -> assertTrue(adapter.contains("ExecutionAdapterTemplate")),
                () -> assertFalse(adapter.contains("Map<String, Object> create(")),
                () -> assertTrue(adapter.contains("ExecutionAdapterTargetGenerationApplicationService")),
                () -> assertTrue(adapter.contains("targetGenerationMapper.input")),
                () -> assertFalse(adapter.contains("OpsExecutionAdapterTemplateService")));
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
