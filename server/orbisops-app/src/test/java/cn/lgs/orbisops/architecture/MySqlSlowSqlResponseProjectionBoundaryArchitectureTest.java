package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MySqlSlowSqlResponseProjectionBoundaryArchitectureTest {

    private static final String OPS = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/";

    @Test
    void mysqlSlowSqlAgentDelegatesQueryConstructionAndApplicationResultProjection() throws IOException {
        String subAgent = read(OPS + "MySqlSlowSqlOpsSubAgent.java");
        String projector = read(OPS + "OpsMySqlSlowSqlResponseProjector.java");
        String queryFactory = read(OPS + "OpsMySqlSlowSqlQueryFactory.java");

        assertAll(
                () -> assertTrue(subAgent.contains("OpsMySqlSlowSqlResponseProjector responseProjector")),
                () -> assertTrue(subAgent.contains("OpsMySqlSlowSqlQueryFactory queryFactory")),
                () -> assertTrue(subAgent.contains("queryFactory.create(request, settings)")),
                () -> assertTrue(subAgent.contains("responseProjector.project(result)")),
                () -> assertFalse(subAgent.contains("new MySqlSlowSqlQuery(")),
                () -> assertTrue(queryFactory.contains("new MySqlSlowSqlQuery(")),
                () -> assertTrue(subAgent.contains("queryService.query(")),
                () -> assertTrue(subAgent.contains("assertNotCanceled(request);")),
                () -> assertTrue(subAgent.contains("response.setSlowSqlSummary(outcome.summary())")),
                () -> assertFalse(subAgent.contains("MySqlSlowSqlSample")),
                () -> assertFalse(subAgent.contains("Optional")),
                () -> assertFalse(subAgent.contains("sampleView(")),
                () -> assertFalse(subAgent.contains("private OpsAnalysisResponseDTO.SlowSqlSummaryDTO summary(")),
                () -> assertFalse(subAgent.contains("record QueryOutcome")),
                () -> assertTrue(subAgent.lines().count() <= 250),
                () -> assertTrue(projector.contains("record Projection(")),
                () -> assertTrue(projector.contains("MySqlSlowSqlQueryResult")),
                () -> assertTrue(projector.contains("MySqlSlowSqlSample")),
                () -> assertTrue(projector.contains("SlowSqlSampleDTO.builder()")),
                () -> assertTrue(projector.contains("SlowSqlSummaryDTO.builder()")),
                () -> assertTrue(projector.contains(".totalStatements((long) samples.size())")),
                () -> assertTrue(projector.contains(".slowStatements((long) samples.size())")),
                () -> assertTrue(projector.contains(".avgQueryTimeMs(round(average, 2))")),
                () -> assertTrue(projector.contains(".maxQueryTimeMs(round(max, 2))")),
                () -> assertTrue(projector.contains(".rowsExamined(rowsExamined)")),
                () -> assertFalse(projector.contains("@Service")),
                () -> assertFalse(projector.contains("@Component")),
                () -> assertFalse(projector.contains("@Value")),
                () -> assertFalse(projector.contains("@Autowired")),
                () -> assertFalse(projector.contains("OpsAgentRunRequestDTO")),
                () -> assertFalse(projector.contains("OpsQuestionContext")),
                () -> assertFalse(projector.contains("OpsSubAgentDecision")),
                () -> assertFalse(projector.contains("MySqlSlowSqlQueryApplicationService")),
                () -> assertTrue(projector.lines().count() <= 110),
                () -> assertFalse(queryFactory.contains("@Service")),
                () -> assertFalse(queryFactory.contains("@Component")),
                () -> assertFalse(queryFactory.contains("@Value")),
                () -> assertFalse(queryFactory.contains("JdbcTemplate")));
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
