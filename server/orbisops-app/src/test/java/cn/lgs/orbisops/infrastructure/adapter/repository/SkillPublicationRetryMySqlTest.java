package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.*;
import cn.lgs.orbisops.domain.skill.model.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.ExposedPort;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Isolated synthetic retry records; no business publication or approval is fabricated. */
@Testcontainers
class SkillPublicationRetryMySqlTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0.36")
        .withDatabaseName("retry").withUsername("fixture").withPassword("fixture")
        .withCreateContainerCmdModifier(c->c.getHostConfig().withPortBindings(
            new PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1",0),new ExposedPort(3306))));
    JdbcTemplate jdbc;TransactionTemplate tx;JdbcSkillPublicationRetryRepository repo;String candidate;
    @BeforeEach void setup() throws Exception {
        var ds=new DriverManagerDataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());
        jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        Path root=Path.of("").toAbsolutePath();while(!Files.exists(root.resolve("db/migrations/sql/ops-skill-publication-retry.sql")))root=root.getParent();
        try(var connection=ds.getConnection()) {ScriptUtils.executeSqlScript(connection,new FileSystemResource(root.resolve("db/migrations/sql/ops-skill-publication-retry.sql")));}
        jdbc.update("DELETE FROM ai_ops_skill_publication_retry");
        repo=new JdbcSkillPublicationRetryRepository(jdbc);candidate="synthetic-"+UUID.randomUUID();
    }
    SkillPublicationRetryPort.Claim claim() {return tx.execute(s->repo.claim().orElseThrow());}
    Map<String,Object> row() {return jdbc.queryForMap("SELECT * FROM ai_ops_skill_publication_retry WHERE candidate_id=?",candidate);}
    @Test void invalidTransactionResultRollsBackBeforeCommit() {
        var beans=new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        beans.registerSingleton("mysqlTransactionManager",new DataSourceTransactionManager(jdbc.getDataSource()));
        var adapter=new SpringSkillTransactionAdapter(beans.getBeanProvider(org.springframework.transaction.PlatformTransactionManager.class));
        assertThrows(IllegalStateException.class,()->adapter.required(()->{repo.enqueue("project",candidate);return null;}));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM ai_ops_skill_publication_retry",Integer.class));
    }
    @Test void concurrentDuplicateRequestsPersistExactlyOneUnacceleratedRetry() throws Exception {
        var pool=Executors.newFixedThreadPool(8);
        try {
            var tasks=new ArrayList<Callable<Map<String,Object>>>();for(int i=0;i<24;i++)tasks.add(()->repo.enqueue("project",candidate));
            for(var f:pool.invokeAll(tasks))assertEquals("PENDING",f.get().get("status"));
        } finally {pool.shutdownNow();}
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM ai_ops_skill_publication_retry",Integer.class));
        var current=claim();repo.defer(current,"SKILL_CONTENT_REVIEW_UNAVAILABLE");var before=row();
        repo.enqueue("project",candidate);assertEquals(before.get("next_run_at"),row().get("next_run_at"));
        assertTrue(tx.execute(s->repo.claim()).isEmpty());
    }
    @Test void independentWorkersCannotClaimSameLiveCandidate() throws Exception {
        repo.enqueue("project",candidate);
        var pool=Executors.newFixedThreadPool(8);
        try {
            var tasks=new ArrayList<Callable<Optional<SkillPublicationRetryPort.Claim>>>();
            for(int i=0;i<12;i++)tasks.add(()->tx.execute(s->new JdbcSkillPublicationRetryRepository(jdbc).claim()));
            int owners=0;for(var f:pool.invokeAll(tasks))if(f.get().isPresent())owners++;
            assertEquals(1,owners);
        } finally {pool.shutdownNow();}
    }
    @Test void expiredWorkerCannotCommitAfterRestartClaimsItsRecord() {
        repo.enqueue("project",candidate);var stale=claim();
        jdbc.update("UPDATE ai_ops_skill_publication_retry SET lease_until=DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 1 SECOND)");
        repo=new JdbcSkillPublicationRetryRepository(jdbc);var current=claim();
        assertEquals(2,current.attempt());assertNotEquals(stale.token(),current.token());
        assertThrows(IllegalStateException.class,()->tx.execute(s->{repo.requireCurrent(stale);return null;}));
        repo.defer(stale,"STALE");assertEquals(current.token(),row().get("lease_token"));
        repo.requireCurrent(current);repo.defer(current,"SKILL_CONTENT_REVIEW_UNAVAILABLE");
        assertEquals("PENDING",row().get("status"));assertEquals(2,row().get("attempts"));
        Long delay=jdbc.queryForObject("SELECT TIMESTAMPDIFF(SECOND,CURRENT_TIMESTAMP(3),next_run_at) FROM ai_ops_skill_publication_retry",Long.class);
        assertTrue(delay>=58&&delay<=60);
    }
    @Test void disabledDefersButPolicyRejectionAndCompletedReleaseAreTerminal() {
        repo.enqueue("project",candidate);var disabled=claim();repo.complete(disabled,SkillReleaseStartOutcome.disabled(candidate));
        assertEquals("PENDING",row().get("status"));
        jdbc.update("UPDATE ai_ops_skill_publication_retry SET next_run_at=CURRENT_TIMESTAMP(3)");
        var rejected=claim();repo.complete(rejected,SkillReleaseStartOutcome.validationRejected(candidate,new SkillPatchValidationDecision("POLICY_REJECTED",false,List.of("SOURCE_REVOKED"))));
        assertEquals("REJECTED",repo.enqueue("project",candidate).get("status"));
        candidate="complete-"+UUID.randomUUID();repo.enqueue("project",candidate);var accepted=claim();
        var release=new SkillReleaseSnapshot("release",candidate,"project","agent","skill",SkillReleaseStatus.PENDING_INDEX,0,0,"","WAITING_FOR_BODY_AND_INDEX",1,"hash",Map.of());
        repo.complete(accepted,SkillReleaseStartOutcome.released(release));
        assertEquals("COMPLETED",repo.enqueue("project",candidate).get("status"));assertTrue(tx.execute(s->repo.claim()).isEmpty());
    }
}
