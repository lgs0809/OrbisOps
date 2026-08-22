package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MySqlSlowSqlBoundaryArchitectureTest {

    private static final String APPLICATION =
            "orbisops-application/src/main/java/"
                    + "cn/lgs/orbisops/application/analysis/";
    private static final String INFRASTRUCTURE =
            "orbisops-infrastructure/src/main/java/"
                    + "cn/lgs/orbisops/infrastructure/adapter/analysis/";
    private static final String TRIGGER =
            "orbisops-trigger/src/main/java/";

    @Test
    void triggerSubAgentMustOnlyOrchestrateTypedQueryFactoryAndProjector() throws IOException {
        String subAgent = read(TRIGGER
                + "cn/lgs/orbisops/trigger/ops/MySqlSlowSqlOpsSubAgent.java");
        String projector = read(TRIGGER
                + "cn/lgs/orbisops/trigger/ops/OpsMySqlSlowSqlResponseProjector.java");
        String settings = read(TRIGGER
                + "cn/lgs/orbisops/trigger/ops/OpsMySqlSlowSqlSettings.java");
        String queryFactory = read(TRIGGER
                + "cn/lgs/orbisops/trigger/ops/OpsMySqlSlowSqlQueryFactory.java");
        String configuration = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/ops/OpsDatasourceSubAgentConfiguration.java");

        assertAll(
                () -> assertTrue(subAgent.contains("MySqlSlowSqlQueryApplicationService queryService")),
                () -> assertTrue(subAgent.contains("OpsMySqlSlowSqlResponseProjector responseProjector")),
                () -> assertTrue(subAgent.contains("OpsMySqlSlowSqlSettings settings")),
                () -> assertTrue(subAgent.contains("OpsSubAgentRequestPolicy requestPolicy")),
                () -> assertTrue(subAgent.contains("OpsMySqlSlowSqlQueryFactory queryFactory")),
                () -> assertTrue(subAgent.contains("queryFactory.create(request, settings)")),
                () -> assertTrue(subAgent.contains("queryService.query(")),
                () -> assertTrue(subAgent.contains("responseProjector.project(result)")),
                () -> assertFalse(subAgent.contains("new MySqlSlowSqlQuery(")),
                () -> assertFalse(subAgent.contains("@Value")),
                () -> assertFalse(subAgent.contains("JdbcTemplate")),
                () -> assertFalse(subAgent.contains("ConnectionCallback")),
                () -> assertFalse(subAgent.contains("PreparedStatement")),
                () -> assertFalse(subAgent.contains("ResultSet")),
                () -> assertFalse(subAgent.contains("DataAccessException")),
                () -> assertFalse(subAgent.contains("private int safeSampleSize(")),
                () -> assertFalse(subAgent.contains("private int safeRangeMinutes(")),
                () -> assertFalse(subAgent.contains("private int queryTimeoutSeconds(")),
                () -> assertTrue(projector.contains("OpsAnalysisResponseDTO.SlowSqlSampleDTO")),
                () -> assertTrue(settings.contains("public record OpsMySqlSlowSqlSettings(")),
                () -> assertTrue(settings.contains("public static OpsMySqlSlowSqlSettings defaults()")),
                () -> assertFalse(settings.contains("org.springframework")),
                () -> assertFalse(settings.contains("@Value")),
                () -> assertTrue(queryFactory.contains("new MySqlSlowSqlQuery(")),
                () -> assertTrue(queryFactory.contains("safeRangeMinutes(request)")),
                () -> assertTrue(queryFactory.contains("queryTimeoutSeconds(request)")),
                () -> assertFalse(queryFactory.contains("org.springframework")),
                () -> assertFalse(queryFactory.contains("JdbcTemplate")),
                () -> assertFalse(queryFactory.contains("java.sql")),
                () -> assertTrue(configuration.contains("OpsMySqlSlowSqlSettings opsMySqlSlowSqlSettings(")),
                () -> assertTrue(configuration.contains("${orbisops.mysql-slow-sql.enabled:true}")),
                () -> assertTrue(configuration.contains("${orbisops.mysql-slow-sql.sample-size:10}")),
                () -> assertTrue(configuration.contains("${orbisops.mysql-slow-sql.threshold-ms:500}")),
                () -> assertTrue(configuration.contains("${orbisops.mysql-slow-sql.performance-schema-fallback:true}")));
    }

    @Test
    void infrastructureAdapterMustOwnJdbcCollectionAndFallback() throws IOException {
        String adapter = read(INFRASTRUCTURE
                + "JdbcMySqlSlowSqlQueryAdapter.java");

        assertAll(
                () -> assertTrue(adapter.contains("implements MySqlSlowSqlQueryPort")),
                () -> assertTrue(adapter.contains("JdbcTemplate")),
                () -> assertTrue(adapter.contains("ConnectionCallback")),
                () -> assertTrue(adapter.contains("PreparedStatement")),
                () -> assertTrue(adapter.contains("FROM mysql.slow_log")),
                () -> assertTrue(adapter.contains("FROM performance_schema.events_statements_summary_by_digest")),
                () -> assertTrue(adapter.contains("query.performanceSchemaFallback()")),
                () -> assertFalse(adapter.contains("OpsAnalysisResponseDTO")),
                () -> assertFalse(adapter.contains("cn.lgs.orbisops.trigger")));
    }

    @Test
    void applicationQueryBoundaryMustRemainFrameworkNeutral() throws IOException {
        String application = read(APPLICATION + "MySqlSlowSqlQuery.java")
                + read(APPLICATION + "MySqlSlowSqlSample.java")
                + read(APPLICATION + "MySqlSlowSqlQueryResult.java")
                + read(APPLICATION + "MySqlSlowSqlQueryPort.java")
                + read(APPLICATION + "MySqlSlowSqlQueryApplicationService.java");

        assertAll(
                () -> assertFalse(application.contains("org.springframework")),
                () -> assertFalse(application.contains("JdbcTemplate")),
                () -> assertFalse(application.contains("java.sql")),
                () -> assertFalse(application.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(application.contains("OpsAnalysisResponseDTO")),
                () -> assertFalse(application.contains("com.alibaba.fastjson")));
    }

    @Test
    void compositionRootMustExposeTheApplicationService() throws IOException {
        String configuration = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/analysis/"
                + "OpsAnalysisQueryApplicationConfiguration.java");

        assertAll(
                () -> assertTrue(configuration.contains("@Configuration")),
                () -> assertTrue(configuration.contains("@Bean")),
                () -> assertTrue(configuration.contains("MySqlSlowSqlQueryPort queryPort")),
                () -> assertTrue(configuration.contains("return new MySqlSlowSqlQueryApplicationService(queryPort);")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-application"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-application"))) {
            return parent;
        }
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-application"))) {
            return nested;
        }
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
