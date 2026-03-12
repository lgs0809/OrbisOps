package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataStatisticsArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/statistics/";
    private static final String APPLICATION = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/statistics/";
    private static final String INFRASTRUCTURE = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/";
    private static final String TRIGGER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/";
    private static final String TRIGGER_STATISTICS = TRIGGER + "application/statistics/";

    @Test
    void domainOwnsTypedFactsSnapshotAndSuccessRatePolicy() throws IOException {
        String domain = readJavaTree(DOMAIN);
        String policy = read(DOMAIN + "service/DataStatisticsPolicy.java");

        assertAll(
                () -> assertTrue(domain.contains("record ExecutionStatisticsFacts")),
                () -> assertTrue(domain.contains("record ExecutionStatisticsSummary")),
                () -> assertTrue(domain.contains("record DataCatalogCounts")),
                () -> assertTrue(domain.contains("record DataStatisticsSnapshot")),
                () -> assertTrue(domain.contains("interface IDataStatisticsReadRepository")),
                () -> assertTrue(policy.contains("facts.auditTotal() > 0")),
                () -> assertTrue(policy.contains("facts.terminalRuns() > 0")),
                () -> assertTrue(policy.contains("facts.terminalLegacyTasks() > 0")),
                () -> assertTrue(policy.contains("RoundingMode.HALF_UP")),
                () -> assertFalse(domain.contains("org.springframework")),
                () -> assertFalse(domain.contains("JdbcTemplate")),
                () -> assertFalse(domain.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(domain.contains("SELECT ")),
                () -> assertFalse(domain.contains("ai_ops_agent_run")));
    }

    @Test
    void applicationCombinesCatalogAndExecutionThroughNarrowPorts() throws IOException {
        String application = readJavaTree(APPLICATION);
        String query = read(APPLICATION + "DataStatisticsQueryApplicationService.java");

        assertAll(
                () -> assertTrue(application.contains("interface DataCatalogStatisticsPort")),
                () -> assertTrue(query.contains("IDataStatisticsReadRepository")),
                () -> assertTrue(query.contains("DataStatisticsPolicy")),
                () -> assertTrue(query.contains("public DataStatisticsSnapshot snapshot()")),
                () -> assertFalse(application.contains("org.springframework")),
                () -> assertFalse(application.contains("JdbcTemplate")),
                () -> assertFalse(application.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(application.contains("DataStatisticsResponseDTO")),
                () -> assertFalse(application.contains("SELECT ")),
                () -> assertFalse(application.contains("ai_ops_agent_run")));
    }

    @Test
    void infrastructureOwnsFixedStatisticsSqlWithoutArbitrarySqlPort() throws IOException {
        String repository = read(INFRASTRUCTURE + "JdbcDataStatisticsReadRepository.java");
        String infrastructure = readJavaTree(INFRASTRUCTURE);

        assertAll(
                () -> assertTrue(repository.contains("implements IDataStatisticsReadRepository")),
                () -> assertTrue(repository.contains("JdbcTemplate")),
                () -> assertTrue(repository.contains("ai_ops_agent_run")),
                () -> assertTrue(repository.contains("ai_ops_chat_message")),
                () -> assertTrue(repository.contains("ai_agent_task_execution")),
                () -> assertTrue(repository.contains("ai_ops_agent_audit")),
                () -> assertTrue(repository.contains("information_schema.tables")),
                () -> assertFalse(infrastructure.contains("implements IDataStatisticsRepository")),
                () -> assertFalse(infrastructure.contains("class DataStatisticsRepository")),
                () -> assertFalse(infrastructure.contains("public long count(String tableName, String sql)")));
    }

    @Test
    void triggerOnlyAdaptsCatalogMapsDtoAndWiresQueryService() throws IOException {
        String trigger = readJavaTree(TRIGGER);
        String statistics = readJavaTree(TRIGGER_STATISTICS);
        String controller = read(TRIGGER + "http/admin/AiAgentDataStatisticsAdminController.java");

        assertAll(
                () -> assertTrue(statistics.contains("class OpsDataCatalogStatisticsAdapter")),
                () -> assertTrue(statistics.contains("class OpsDataStatisticsMapper")),
                () -> assertTrue(statistics.contains("class DataStatisticsApplicationConfiguration")),
                () -> assertTrue(controller.contains("DataStatisticsQueryApplicationService")),
                () -> assertTrue(controller.contains("OpsDataStatisticsMapper")),
                () -> assertFalse(trigger.contains("class DataStatisticsApplicationService")),
                () -> assertFalse(trigger.contains("IDataStatisticsRepository")),
                () -> assertFalse(statistics.contains("JdbcTemplate")),
                () -> assertFalse(statistics.contains("@Transactional")),
                () -> assertFalse(statistics.contains("SELECT ")),
                () -> assertFalse(statistics.contains("ai_ops_agent_run")),
                () -> assertFalse(controller.contains("DataStatisticsResponseDTO.builder")));
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
