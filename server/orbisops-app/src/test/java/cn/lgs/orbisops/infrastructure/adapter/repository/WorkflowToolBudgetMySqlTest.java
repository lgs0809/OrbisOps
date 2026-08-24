package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.runtime.workflow.adapter.repository.IWorkflowToolCallBudgetRepository.Dispatch;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Isolated repository fixtures only; SQL RUNNING rows here do not represent business run outcomes. */
@EnabledIfEnvironmentVariable(named = "ORBISOPS_WORKFLOW_TEST_URL", matches = ".+orbisops_workflow_regression.*")
class WorkflowToolBudgetMySqlTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private String run;
    private String agent;
    private JdbcAgentDefinitionRepository definitions;

    @BeforeEach void setup() {
        var dataSource = new DriverManagerDataSource(System.getenv("ORBISOPS_WORKFLOW_TEST_URL"),
                System.getenv("ORBISOPS_WORKFLOW_TEST_USER"), System.getenv("ORBISOPS_WORKFLOW_TEST_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        org.springframework.beans.factory.support.DefaultListableBeanFactory beans = new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        beans.registerSingleton("mysqlJdbcTemplate", jdbc);
        definitions = new JdbcAgentDefinitionRepository(beans.getBeanProvider(JdbcTemplate.class));
        run = "ops03-budget-unit-" + UUID.randomUUID();
        agent = "ops03-budget-agent-" + UUID.randomUUID();
        System.out.println("OPS-03 repository-only fixture run=" + run);
    }

    @Test void concurrentNodesAndReconstructedRepositoryMustShareTwelveReservations() throws Exception {
        seed(12);
        ExecutorService pool = Executors.newFixedThreadPool(12);
        try {
            List<Callable<Boolean>> requests = new ArrayList<>();
            for (int index = 0; index < 24; index++) {
                int call = index;
                requests.add(() -> {
                    try { reserve("call-" + call, 1, "fixture-project"); return true; }
                    catch (SecurityException exhausted) { assertTrue(exhausted.getMessage().contains("EXHAUSTED")); return false; }
                });
            }
            int succeeded = 0;
            for (Future<Boolean> result : pool.invokeAll(requests)) if (result.get(10, TimeUnit.SECONDS)) succeeded++;
            assertEquals(12, succeeded);
            assertEquals(12, used());
            assertThrows(SecurityException.class, () -> reserve("after-reconstruction", 1, "fixture-project"));
            assertEquals(12, used());
        } finally { pool.shutdownNow(); }
    }

    @Test void retriesConsumeBudgetWhileDuplicateDispatchAndNewPublishedVersionCannotResetIt() {
        seed(3);
        assertEquals(1, reserve("logical-one", 1, "fixture-project"));
        assertThrows(SecurityException.class, () -> reserve("logical-one", 1, "fixture-project"));
        assertEquals(1, used());
        addVersion(2, 100, "new-version-hash");
        assertEquals(2, reserve("logical-one", 2, "fixture-project"));
        assertEquals(3, reserve("logical-two", 1, "fixture-project"));
        assertThrows(SecurityException.class, () -> reserve("logical-two", 2, "fixture-project"));
        assertEquals(3, used());
        assertEquals(List.of(3), jdbc.queryForList("SELECT DISTINCT budget_limit FROM ai_ops_workflow_tool_dispatch WHERE run_id=?", Integer.class, run));
    }

    @Test void otherProjectCanceledRunAndChangedSnapshotMustFailBeforeReservation() {
        seed(3);
        assertThrows(SecurityException.class, () -> reserve("wrong-project", 1, "other-project"));
        jdbc.update("UPDATE ai_ops_agent_run SET cancel_requested=1 WHERE run_id=?", run);
        assertThrows(SecurityException.class, () -> reserve("canceled", 1, "fixture-project"));
        jdbc.update("UPDATE ai_ops_agent_run SET cancel_requested=0,agent_definition_hash='different-hash' WHERE run_id=?", run);
        assertThrows(SecurityException.class, () -> reserve("stale-definition", 1, "fixture-project"));
        assertEquals(0, used());
    }

    private int reserve(String logical, int attempt, String project) {
        return transactions.execute(status -> new JdbcWorkflowToolCallBudgetRepository(jdbc, definitions).reserve(
                new Dispatch(project, run, "node", "fixture-mcp", "probe", logical, attempt, "rpc-" + logical + "-" + attempt)));
    }
    private int used() { return jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_workflow_tool_dispatch WHERE run_id=?", Integer.class, run); }
    private void seed(int limit) {
        addVersion(1, limit, "frozen-hash");
        jdbc.update("""
                INSERT INTO ai_ops_agent_run (run_id,project_id,agent_id,agent_version,agent_definition_hash,status,created_at,updated_at)
                VALUES (?,'fixture-project',?,1,'frozen-hash','RUNNING','2026-09-09T00:00:00Z','2026-09-09T00:00:00Z')
                """, run, agent);
    }
    private void addVersion(int version, int limit, String hash) {
        String definition = CanonicalJson.stringify(Map.of("startNodeId", "start", "definitionHash", hash,
                "nodes", List.of(Map.of("nodeId", "start", "type", "START", "config", Map.of("maxRealToolCalls", limit)))));
        jdbc.update("""
                INSERT INTO ai_ops_agent_definition_version (agent_id,version,project_id,definition_json)
                VALUES (?,?,'fixture-project',?)
                """, agent, version, definition);
    }
}
