package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.schedule.ScheduledTaskExecutionCommand;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic state-machine records in disposable MySQL, not business approval evidence. */
@Testcontainers(disabledWithoutDocker = true)
class ScheduledTaskStateMySqlTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("schedule_states").withUsername("fixture").withPassword("fixture");
    JdbcTemplate jdbc;
    TaskExecutionRepository repository;
    @BeforeEach void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
        var beans = new DefaultListableBeanFactory(); beans.registerSingleton("jdbc", jdbc);
        repository = new TaskExecutionRepository(beans.getBeanProvider(JdbcTemplate.class));
        repository.ensureStorage();
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_ops_agent_run(run_id VARCHAR(128) PRIMARY KEY, project_id VARCHAR(128), status VARCHAR(32), updated_at DATETIME, response_json JSON, error_message TEXT)");
    }
    long waiting() {
        long id = repository.create(new ScheduledTaskExecutionCommand(7L,"synthetic inspection","workflow","MANUAL","{}"));
        repository.updateInput(id,"{\"projectId\":\"project-a\"}");
        repository.markIncomplete(id,"WAITING_APPROVAL","waiting report");
        jdbc.update("INSERT INTO ai_ops_agent_run VALUES (?, 'project-a', 'WAITING_APPROVAL', NOW(), NULL, NULL)","task_7_"+id);
        return id;
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"SUCCEEDED", "FAILED", "CANCELED"})
    void waitingIsNotCompletedAndAuthoritativeResumeConvergesAfterRepositoryRestart(String terminal) {
        long id = waiting();
        assertNull(jdbc.queryForObject("SELECT ended_at FROM ai_agent_task_execution WHERE id=?",java.sql.Timestamp.class,id));
        assertEquals(0, repository.reconcileWaitingRuns());
        jdbc.update("UPDATE ai_ops_agent_run SET status=?, response_json='{\"content\":\"final evidence\"}' WHERE run_id=?",terminal,"task_7_"+id);
        var beans = new DefaultListableBeanFactory(); beans.registerSingleton("jdbc",jdbc);
        var fresh = new TaskExecutionRepository(beans.getBeanProvider(JdbcTemplate.class));
        assertEquals(1, fresh.reconcileWaitingRuns());
        var row = jdbc.queryForMap("SELECT status,ended_at,output FROM ai_agent_task_execution WHERE id=?",id);
        assertEquals(terminal.equals("SUCCEEDED")?"SUCCESS":terminal,row.get("status"));
        assertNotNull(row.get("ended_at")); assertEquals("final evidence",row.get("output"));
        assertEquals(0, fresh.reconcileWaitingRuns());
        repository.markIncomplete(id,"WAITING_APPROVAL","late worker");
        assertEquals(row,jdbc.queryForMap("SELECT status,ended_at,output FROM ai_agent_task_execution WHERE id=?",id));
    }
    @Test void wrongProjectAndActiveRuntimeCannotCompleteWaitingInspection() {
        long id=waiting();
        jdbc.update("UPDATE ai_ops_agent_run SET project_id='project-b',status='SUCCEEDED' WHERE run_id=?","task_7_"+id);
        assertEquals(0,repository.reconcileWaitingRuns());
        jdbc.update("UPDATE ai_ops_agent_run SET project_id='project-a',status='RUNNING' WHERE run_id=?","task_7_"+id);
        assertEquals(0,repository.reconcileWaitingRuns());
        assertEquals("WAITING_APPROVAL",jdbc.queryForObject("SELECT status FROM ai_agent_task_execution WHERE id=?",String.class,id));
    }
}
