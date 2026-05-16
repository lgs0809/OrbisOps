package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.changepackage.ChangeVerificationQueuePort;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic Landing facts in disposable real MySQL; never marks application fixtures approved. */
@Testcontainers(disabledWithoutDocker=true)
class ChangeVerificationQueueMySqlTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("verification_queue").withUsername("fixture").withPassword("fixture");
    JdbcTemplate jdbc;
    JdbcChangeVerificationQueue queue;
    TransactionTemplate tx;
    String project;
    ChangeVerificationQueuePort.Binding binding;
    @BeforeEach void setup() throws Exception {
        var ds=new DriverManagerDataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());
        jdbc=new JdbcTemplate(ds); tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        var beans=new DefaultListableBeanFactory();beans.registerSingleton("jdbc",jdbc);
        var schema=new JdbcChangePackageSchemaInitializer(beans.getBeanProvider(JdbcTemplate.class));
        ReflectionTestUtils.setField(schema,"autoInit",true);schema.initialize();
        jdbc.execute(Files.readString(Path.of("../db/migrations/sql/ops-change-verification-queue.sql")));
        jdbc.update("DELETE FROM ai_ops_change_verification_job");
        queue=new JdbcChangeVerificationQueue(jdbc,tx.getTransactionManager());project="p-"+UUID.randomUUID();
        binding=new ChangeVerificationQueuePort.Binding(project,"workflow-c",5,"workflow-hash");
    }
    void landed(String id,String status,int secondsAgo) {
        jdbc.update("""
                INSERT INTO ai_ops_change_package(package_id,project_id,session_id,incident_id,objective,summary,
                   preparation_agent_id,preparation_agent_version,package_type,status,version,package_hash,
                   approved_version,approved_package_hash,landing_run_id,create_by)
                VALUES (?,?,'session','','fixture','fixture','agent',1,'MCP_OPERATION_PACKAGE',?,3,'hash',3,'hash',?,'creator')
                """,id,project,status,"landing-"+id);
        jdbc.update("""
                INSERT INTO ai_ops_change_package_landing_run(run_id,package_id,project_id,approved_version,
                   approved_package_hash,idempotency_key,status,finished_at)
                VALUES (?,?,?,3,'hash',?,'SUCCEEDED',TIMESTAMPADD(SECOND,?,CURRENT_TIMESTAMP))
                ""","landing-"+id,id,project,id,-secondsAgo);
    }
    Optional<ChangeVerificationQueuePort.Task> claim() { return tx.execute(s->queue.claim()); }
    @Test void onlyCommittedMatchingLandingIsQueuedAndWindowMustFinish() {
        landed("a"+project,"LANDED",100);landed("b"+project,"LANDING",1200);
        assertEquals(1,queue.discover(binding,50));assertTrue(claim().isEmpty());
        assertEquals("creator",jdbc.queryForObject("SELECT owner FROM ai_ops_change_verification_job",String.class));
    }
    @Test void mismatchedApprovalOrProjectNeverDispatches() {
        landed("a"+project,"LANDED",1200);
        jdbc.update("UPDATE ai_ops_change_package_landing_run SET approved_package_hash='different' WHERE project_id=?",project);
        assertEquals(0,queue.discover(binding,50));
        jdbc.update("UPDATE ai_ops_change_package_landing_run SET approved_package_hash='hash',project_id='other' WHERE project_id=?",project);
        assertEquals(0,queue.discover(binding,50));
    }
    @Test void uncommittedLandingCannotBecomeBackgroundWork() {
        tx.execute(status -> {
            landed("uncommitted-"+project,"LANDED",1200);
            assertEquals(0,queue.discover(binding,50));
            status.setRollbackOnly();
            return null;
        });
        assertTrue(claim().isEmpty());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_change_verification_job",Integer.class));
    }
    @Test void duplicateNotificationAndNewPublicationKeepOneFrozenOwnedRun() {
        landed("a"+project,"LANDED",1200);queue.discover(binding,50);
        queue.discover(new ChangeVerificationQueuePort.Binding(project,"workflow-c",6,"new-hash"),50);
        var t=claim().orElseThrow();assertEquals(5,t.workflowVersion());assertEquals("workflow-hash",t.workflowHash());
        assertEquals("creator",t.owner());assertTrue(t.runId().length()<=80);assertTrue(t.sessionId().length()<=80);
        assertTrue(queue.settle(t,"COMPLETED","",0,false));queue.discover(binding,50);assertTrue(claim().isEmpty());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_change_verification_job",Integer.class));
    }
    @RepeatedTest(5) void concurrentDiscoveryAndClaimHaveOneWinner() throws Exception {
        landed("a"+project,"LANDED",1200);
        var pool=Executors.newFixedThreadPool(8);
        try {
            var futures=new ArrayList<Future<?>>();
            var start=new CyclicBarrier(8);
            for(int i=0;i<8;i++) futures.add(pool.submit(()->{ start.await(10,TimeUnit.SECONDS);return queue.discover(binding,50); }));
            for(var f:futures) f.get(20,TimeUnit.SECONDS);
            var claims=new ArrayList<Future<Optional<ChangeVerificationQueuePort.Task>>>();
            for(int i=0;i<8;i++) claims.add(pool.submit(this::claim));
            int winners=0;for(var f:claims) if(f.get(20,TimeUnit.SECONDS).isPresent()) winners++;
            assertEquals(1,winners);
        } finally {pool.shutdownNow();}
    }
    @Test void restartReclaimsSameRunAndRejectsLateWorker() {
        landed("a"+project,"LANDED",1200);queue.discover(binding,50);var old=claim().orElseThrow();
        jdbc.update("UPDATE ai_ops_change_verification_job SET lease_until=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP)");
        queue=new JdbcChangeVerificationQueue(jdbc,tx.getTransactionManager());var recovered=claim().orElseThrow();
        assertEquals(old.runId(),recovered.runId());assertNotEquals(old.leaseToken(),recovered.leaseToken());
        assertFalse(queue.owns(old));assertFalse(queue.settle(old,"COMPLETED","",0,false));
        assertTrue(queue.settle(recovered,"PENDING","network",120,true));assertTrue(claim().isEmpty());
        assertEquals(1,jdbc.queryForObject("SELECT failures FROM ai_ops_change_verification_job",Integer.class));
    }
    @Test void discoveryBatchDoesNotStarveOlderUnqueuedFacts() {
        for(int i=0;i<4;i++) landed("a"+i+project,"LANDED",1200+i);
        assertEquals(2,queue.discover(binding,2));assertEquals(2,queue.discover(binding,2));assertEquals(0,queue.discover(binding,2));
    }
    @Test void blockedOwnerIsRecheckedAfterDelayWithoutReplacingRunOrCountingModelFailure() {
        landed("a"+project,"LANDED",1200);queue.discover(binding,50);var original=claim().orElseThrow();
        assertTrue(queue.settle(original,"BLOCKED","PROJECT_ACCESS_FORBIDDEN",60,false));
        assertTrue(claim().isEmpty());
        jdbc.update("UPDATE ai_ops_change_verification_job SET next_attempt_at=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP)");
        var recheck=claim().orElseThrow();
        assertEquals(original.runId(),recheck.runId());assertEquals(original.owner(),recheck.owner());
        assertEquals(0,recheck.failures());assertNotEquals(original.leaseToken(),recheck.leaseToken());
    }
}
