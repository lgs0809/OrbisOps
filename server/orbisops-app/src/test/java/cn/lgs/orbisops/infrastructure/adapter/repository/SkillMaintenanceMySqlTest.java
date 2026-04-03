package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.SkillMaintenancePort;
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
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class SkillMaintenanceMySqlTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0.36")
        .withDatabaseName("maintenance").withUsername("fixture").withPassword("fixture")
        .withCreateContainerCmdModifier(c->c.getHostConfig().withPortBindings(new PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1",0),new ExposedPort(3306))));
    JdbcTemplate jdbc;TransactionTemplate tx;JdbcSkillMaintenanceRepository repo;
    @BeforeEach void setup() throws Exception {
        var ds=new DriverManagerDataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());
        jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        Path root=Path.of("").toAbsolutePath();while(!Files.exists(root.resolve("db/migrations/sql/ops-skill-maintenance.sql")))root=root.getParent();
        try(var connection=ds.getConnection()) {ScriptUtils.executeSqlScript(connection,new FileSystemResource(root.resolve("db/migrations/sql/ops-skill-maintenance.sql")));}
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_ops_skill_runtime_publication(scope varchar(24),project_id varchar(128),skill_id varchar(128),skill_version int)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_ops_skill(id bigint primary key,project_id varchar(128),skill_id varchar(128),scope varchar(24),status varchar(32),current_version int,current_skill_hash varchar(128),current_package_hash varchar(128),content text,create_time timestamp,update_mode varchar(32),auto_update_enabled tinyint,lock_type varchar(32))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_ops_skill_runtime_usage(project_id varchar(128),skill_id varchar(128),created_at timestamp)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_ops_skill_version(scope varchar(24),project_id varchar(128),skill_id varchar(128),version int,evolution_job_id varchar(80))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_ops_skill_patch_candidate(candidate_id varchar(80),patch_type varchar(48))");
        jdbc.update("DELETE FROM ai_ops_skill_maintenance");jdbc.update("DELETE FROM ai_ops_skill_runtime_publication");
        for(String table:List.of("ai_ops_skill","ai_ops_skill_runtime_usage","ai_ops_skill_version","ai_ops_skill_patch_candidate"))jdbc.update("DELETE FROM "+table);
        repo=new JdbcSkillMaintenanceRepository(jdbc);
    }
    SkillMaintenancePort.Subject subject() {return new SkillMaintenancePort.Subject(1,"project","method",1,"hash","package","body",Instant.EPOCH,null,5,0,true);}
    SkillMaintenancePort.Claim claim() {return tx.execute(s->repo.claim().orElseThrow());}
    @Test void scanCountsOnlyActualPatchVersionsAndKeepsRecentUsageProjectScoped() {
        jdbc.update("INSERT INTO ai_ops_skill VALUES(1,'project','method','PROJECT','ENABLED',8,'hash','package','body','2026-01-01','AUTO',1,'NONE')");
        jdbc.update("INSERT INTO ai_ops_skill_runtime_usage VALUES('other','method','2026-09-26')");
        for(int v=1;v<=9;v++) {
            jdbc.update("INSERT INTO ai_ops_skill_version VALUES('PROJECT','project','method',?,?)",v,"c"+v);
            jdbc.update("INSERT INTO ai_ops_skill_patch_candidate VALUES(?,?)","c"+v,v==1?"CREATE_SKILL":"UPDATE_DIAGNOSTIC_RECIPE");
        }
        var first=repo.scan(0,100).get(0);assertEquals(7,first.patches());assertNull(first.lastUseAt());assertTrue(first.automatic());
        jdbc.update("INSERT INTO ai_ops_skill_runtime_usage VALUES('project','method','2026-09-25')");
        var current=repo.scan(0,100).get(0);assertNotNull(current.lastUseAt());
        repo.enqueue(current,"COMPRESS_CHECK",100,"fixture","FIVE_NEW_PATCHES");repo.finish(claim(),"NO_CHANGE","NO_DUPLICATES",Map.of(),0);
        assertEquals(7,repo.scan(0,100).get(0).checkedPatches());assertTrue(repo.scan(1,100).isEmpty());
    }
    @Test void dailyDuplicateChecksAndCompetingWorkersHaveOneDurableOwner() throws Exception {
        var pool=Executors.newFixedThreadPool(6);
        try {
            var writes=new ArrayList<Callable<Void>>();for(int i=0;i<12;i++)writes.add(()->{repo.enqueue(subject(),"COMPRESS_CHECK",2100,"fixture","BODY_OVER_2000_TOKENS");return null;});
            for(var f:pool.invokeAll(writes))f.get();
            assertEquals(1,repo.list("project",100).size());
            var claims=new ArrayList<Callable<Optional<SkillMaintenancePort.Claim>>>();for(int i=0;i<12;i++)claims.add(()->tx.execute(s->repo.claim()));
            int owners=0;for(var f:pool.invokeAll(claims))if(f.get().isPresent())owners++;
            assertEquals(1,owners);
        } finally {pool.shutdownNow();}
    }
    @Test void restartLeaseFencesOldPublicationAndRetainsExponentialBackoff() {
        repo.enqueue(subject(),"COMPRESS_CHECK",2100,"fixture","BODY_OVER_2000_TOKENS");var stale=claim();
        jdbc.update("UPDATE ai_ops_skill_maintenance SET lease_until=DATE_SUB(CURRENT_TIMESTAMP(3),INTERVAL 1 SECOND)");
        repo=new JdbcSkillMaintenanceRepository(jdbc);var fresh=claim();assertEquals(2,fresh.attempt());
        assertThrows(IllegalStateException.class,()->tx.execute(s->{repo.requireCurrent(stale);return true;}));
        assertThrows(IllegalStateException.class,()->repo.finish(stale,"PENDING_INDEX","PUBLISHED",Map.of(),2));
        repo.defer(stale,"STALE");assertEquals("RUNNING",repo.list("project",100).get(0).get("status"));
        repo.defer(fresh,"TIMEOUT");var before=repo.list("project",100).get(0).get("next_run_at");
        repo.enqueue(subject(),"COMPRESS_CHECK",2100,"fixture","BODY_OVER_2000_TOKENS");
        assertEquals(before,repo.list("project",100).get(0).get("next_run_at"));
        Long delay=jdbc.queryForObject("SELECT TIMESTAMPDIFF(SECOND,CURRENT_TIMESTAMP(3),next_run_at) FROM ai_ops_skill_maintenance",Long.class);
        assertTrue(delay>=58 && delay<=60);assertTrue(tx.execute(s->repo.claim()).isEmpty());
    }
    @Test void databaseTransactionRollsBackPublicationReceiptAndIndexIsRequiredBeforeActive() {
        repo.enqueue(subject(),"COMPRESS_CHECK",2100,"fixture","BODY_OVER_2000_TOKENS");var c=claim();
        assertThrows(IllegalStateException.class,()->tx.execute(s->{repo.requireCurrent(c);repo.finish(c,"PENDING_INDEX","PUBLISHED",Map.of(),2);throw new IllegalStateException("crash");}));
        assertEquals("RUNNING",repo.list("project",100).get(0).get("status"));
        tx.execute(s->{repo.requireCurrent(c);repo.finish(c,"PENDING_INDEX","PUBLISHED",Map.of(),2);return true;});
        tx.execute(s->repo.claim());assertEquals("PENDING_INDEX",repo.list("project",100).get(0).get("status"));
        // Synthetic projection fixture in disposable test DB; not a real publication acceptance result.
        jdbc.update("INSERT INTO ai_ops_skill_runtime_publication VALUES('PROJECT','project','method',1)");
        tx.execute(s->repo.claim());assertEquals("PENDING_INDEX",repo.list("project",100).get(0).get("status"));
        jdbc.update("INSERT INTO ai_ops_skill_runtime_publication VALUES('PROJECT','project','method',2)");
        tx.execute(s->repo.claim());assertEquals("ACTIVE",repo.list("project",100).get(0).get("status"));
    }
    @Test void inactivityIsNeverClaimedForAutomaticMutationAndAcknowledgementIsProjectScoped() {
        repo.enqueue(subject(),"INACTIVITY_REVIEW",10,"fixture","NO_RECORDED_USE_FOR_90_DAYS");
        assertTrue(tx.execute(s->repo.claim()).isEmpty());var id=String.valueOf(repo.list("project",100).get(0).get("check_id"));
        assertThrows(IllegalStateException.class,()->repo.acknowledge("other",id,"admin","still useful"));
        assertThrows(IllegalArgumentException.class,()->repo.acknowledge("project",id,"admin"," "));
        assertEquals("REVIEWED_KEEP",repo.acknowledge("project",id,"admin","still useful").get("status"));
        repo.enqueue(subject(),"INACTIVITY_REVIEW",10,"fixture","NO_RECORDED_USE_FOR_90_DAYS");
        assertEquals("REVIEWED_KEEP",repo.list("project",100).get(0).get("status"));
        assertThrows(IllegalStateException.class,()->repo.acknowledge("project",id,"admin","duplicate"));
    }
}
