package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.incident.AlertCorrelationApplicationService;
import cn.lgs.orbisops.domain.incident.correlation.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

/** Projection fixtures use real InnoDB; these are not fabricated business remediation results. */
@Testcontainers(disabledWithoutDocker = true)
class AlertCorrelationMySqlTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("correlation_test").withUsername("agent").withPassword("agent");
    private static final AtomicLong IDS = new AtomicLong(10000);
    private final Instant now = Instant.parse("2026-09-09T03:00:00Z");
    private JdbcTemplate jdbc;
    private DefaultListableBeanFactory beans;
    private String project;
    @BeforeEach void setup() throws Exception {
        var ds=new DriverManagerDataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());
        jdbc=new JdbcTemplate(ds); beans=new DefaultListableBeanFactory();
        beans.registerSingleton("mysqlJdbcTemplate",jdbc); beans.registerSingleton("mysqlTransactionManager",new DataSourceTransactionManager(ds));
        Path root=Path.of("").toAbsolutePath();
        while(root!=null && !Files.exists(root.resolve("db/migrations/sql/ops-alert-incident-correlation.sql"))) root=root.getParent();
        assertNotNull(root);
        try(var connection=ds.getConnection()) { ScriptUtils.executeSqlScript(connection,
                new FileSystemResource(root.resolve("db/migrations/sql/ops-alert-incident-correlation.sql"))); }
        project="correlation-"+UUID.randomUUID();
    }
    private JdbcAlertCorrelationStore store() { return new JdbcAlertCorrelationStore(beans.getBeanProvider(JdbcTemplate.class),beans.getBeanProvider(PlatformTransactionManager.class)); }
    private AlertCorrelationApplicationService service() { return new AlertCorrelationApplicationService(store()); }
    private CorrelationSignal signal(String entity) { return new CorrelationSignal(IDS.incrementAndGet(),"i-"+project+"-"+entity,project,"test",entity,
            now,now.plusSeconds(60),Map.of(),"symptom-"+entity,false); }
    private CorrelationTopologyEdge edge(String a,String b) { return new CorrelationTopologyEdge(a,b,"inventory:"+a+"/"+b,now.minusSeconds(60),now.plusSeconds(600)); }

    @Test void concurrentIndependentInstancesRespectGroupCapAndNeverLoseOrDuplicateEvents() throws Exception {
        var topology=new ArrayList<CorrelationTopologyEdge>();
        for(int i=0;i<50;i++) topology.add(edge("root","child-"+i));
        service().configure(project,"test",topology,"fixture");
        var root=signal("root"); service().observe(root);
        var pool=Executors.newFixedThreadPool(8);
        try {
            List<Callable<Void>> work=new ArrayList<>();
            for(int i=0;i<50;i++) { var item=signal("child-"+i); work.add(() -> { service().observe(item); service().observe(item); return null; }); }
            for(var future:pool.invokeAll(work)) future.get(30,TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        var groups=service().groups(project,"test",100);
        assertTrue(groups.stream().allMatch(group -> ((List<?>) group.get("members")).size()<=32));
        assertEquals(51, groups.stream().mapToInt(group -> ((List<?>) group.get("members")).size()).sum());
        assertTrue(groups.stream().anyMatch(group -> ((List<?>) group.get("members")).size()==32));
        assertEquals(51, jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_alert_correlation_decision d JOIN ai_ops_alert_correlation_group g ON g.group_id=d.group_id WHERE g.project_id=?",Integer.class,project));
        assertEquals(1L, jdbc.queryForObject("SELECT occurrence_count FROM ai_ops_alert_correlation_member WHERE incident_id=?",Long.class,root.incidentId()));
    }

    @Test void lateUpstreamEvidenceAutomaticallyMergesDifferentSymptomsAndOneRecoveryDoesNotCloseTheGroup() {
        service().configure(project,"test",List.of(edge("caller","x"),edge("caller","y")),"fixture");
        var x=signal("x"); var y=signal("y");
        service().observe(x); service().observe(y);
        assertEquals(2,service().groups(project,"test",100).size());
        service().observe(signal("caller"));
        var groups=service().groups(project,"test",100);
        assertEquals(1,groups.size()); assertEquals(3,((List<?>)groups.get(0).get("members")).size());
        assertEquals(false,groups.get(0).get("rootCauseConfirmed"));
        service().observe(new CorrelationSignal(IDS.incrementAndGet(),x.incidentId(),project,"test","x",now,now.plusSeconds(80),Map.of(),"recovered",true));
        groups=service().groups(project,"test",100); assertEquals(1,groups.size());
        assertEquals(3,((List<?>)groups.get(0).get("members")).size());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_alert_correlation_revision WHERE group_id=? AND action_type='LATE_EVIDENCE_REGROUP'",Integer.class,groups.get(0).get("groupId")));
        assertEquals(4,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_alert_correlation_decision d JOIN ai_ops_alert_correlation_group g ON g.group_id=d.group_id WHERE g.project_id=?",Integer.class,project));
    }

    @Test void rollbackThenReconstructedServiceRetriesWithoutLosingTheSignalAndTopologyIsIdempotent() {
        var topology=List.of(edge("x","y"));
        service().configure(project,"test",topology,"fixture"); service().configure(project,"test",topology,"fixture");
        assertEquals(1L,jdbc.queryForObject("SELECT revision FROM ai_ops_alert_correlation_topology WHERE scope_key=?",Long.class,JdbcAlertCorrelationStore.scope(project,"test")));
        var signal=signal("x");
        var failing=new JdbcAlertCorrelationStore(beans.getBeanProvider(JdbcTemplate.class),beans.getBeanProvider(PlatformTransactionManager.class)) {
            @Override public void record(String group, CorrelationSignal s, AlertCorrelationPolicy.Decision d) {
                super.record(group,s,d); throw new IllegalStateException("simulated-process-failure-before-commit");
            }
        };
        assertThrows(IllegalStateException.class,()->new AlertCorrelationApplicationService(failing).observe(signal));
        assertFalse(store().recorded(signal.eventId())); assertTrue(service().groups(project,"test",100).isEmpty());
        service().observe(signal); service().observe(signal);
        assertTrue(store().recorded(signal.eventId())); assertEquals(1,service().groups(project,"test",100).size());
    }

    @Test void durableReplayDoesNotAbandonUnprocessedAlertsAfterADayOfDowntime() {
        // Minimal source-ledger schema in the disposable MySQL container, not business-success fixtures.
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_ops_alert_trigger_event (id BIGINT PRIMARY KEY, project_id VARCHAR(80), "
                + "status VARCHAR(40), service_name VARCHAR(80), alert_name VARCHAR(100), labels_json TEXT, payload_json TEXT, "
                + "create_time TIMESTAMP, dedup_key VARCHAR(100))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_ops_incident (incident_id VARCHAR(80) PRIMARY KEY, dedup_key VARCHAR(200))");
        long id=IDS.incrementAndGet(); String incident="late-"+id;
        jdbc.update("INSERT INTO ai_ops_incident VALUES (?,?)",incident,project+":backlog");
        jdbc.update("INSERT INTO ai_ops_alert_trigger_event VALUES (?,?,'QUEUED','service-a','different-symptom',?, ?,CURRENT_TIMESTAMP - INTERVAL 2 DAY,'backlog')",
                id,project,"{\"environment\":\"test\"}","{\"startsAt\":\""+Instant.now().minusSeconds(172800)+"\"}");
        var pending=store().pending(100);
        var source=pending.stream().filter(signal->signal.eventId()==id).findFirst().orElseThrow();
        assertTrue(source.receivedAt().isBefore(Instant.now().minusSeconds(86400)));
        service().reconcile(100);
        assertTrue(store().recorded(id));
        assertTrue(store().pending(100).stream().noneMatch(signal->signal.eventId()==id));
    }
}
