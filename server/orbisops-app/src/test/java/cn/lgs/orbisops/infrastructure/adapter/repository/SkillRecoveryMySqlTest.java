package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.*;
import cn.lgs.orbisops.domain.skill.model.*;
import cn.lgs.orbisops.domain.skill.service.SkillPackageManifest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;

/** Disposable synthetic catalog; verifies actual quarantine/CAS and current dependency checks. */
@Testcontainers(disabledWithoutDocker=true)
class SkillRecoveryMySqlTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0.36").withDatabaseName("skill_recovery").withUsername("fixture").withPassword("fixture");
    JdbcTemplate jdbc; SkillRollbackUseCase recovery; TransactionTemplate tx; String project;
    @BeforeEach void setup() throws Exception {
        var ds=new DriverManagerDataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());
        jdbc=new JdbcTemplate(ds);tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        var beans=new DefaultListableBeanFactory();beans.registerSingleton("mysqlJdbcTemplate",jdbc);
        var root=Path.of("").toAbsolutePath();while(root!=null&&!Files.exists(root.resolve("scripts/fixtures/ops06-related-skill-tables.sql"))) root=root.getParent();assertNotNull(root);
        try(var c=ds.getConnection()) {for(String fixture:List.of("ops06-related-skill-tables.sql","ops06-published-skill-tables.sql"))
            ScriptUtils.executeSqlScript(c,new FileSystemResource(root.resolve("scripts/fixtures").resolve(fixture)));}
        SkillTransactionPort transactions=new SkillTransactionPort(){public <T>T required(Supplier<T> action){return tx.execute(status->action.get());}};
        recovery=new SkillRollbackUseCase(new JdbcSkillCatalogRepository(jdbc),new JdbcSkillPackageRepository(beans.getBeanProvider(JdbcTemplate.class)),transactions,SkillPackageManifest.Limits.defaults());
        project="p-"+UUID.randomUUID();seed(project,"method",1,"{\"dependencies\":[]}");
    }
    void seed(String p,String id,int version,String manifest) {
        jdbc.update("INSERT INTO ai_ops_skill(skill_id,project_id,scope,skill_name,content,current_version,current_skill_hash,package_manifest_json) VALUES (?,?,'PROJECT',?,'SYNTHETIC fixture',?,'hash',?)",id,p,id,version,manifest);
    }
    SkillReleaseSnapshot release(int version) {return new SkillReleaseSnapshot("r","c",project,"a","method",SkillReleaseStatus.ROLLING_BACK,10,0,"","",version,"hash",Map.of());}
    @Test void concurrentGuardActuallyQuarantinesTheMethodWithoutAppendingOrTouchingAnotherProject() throws Exception {
        seed(project+"-other","method",1,"{\"dependencies\":[]}");
        var pool=Executors.newFixedThreadPool(8);
        try {var jobs=new ArrayList<Callable<Boolean>>();for(int i=0;i<8;i++) jobs.add(()->{try{return !recovery.recoverRelease(release(1)).restored();}catch(IllegalStateException conflict){return false;}});
            assertTrue(pool.invokeAll(jobs).stream().anyMatch(f->{try{return f.get();}catch(Exception e){throw new RuntimeException(e);}}));
        }finally{pool.shutdownNow();}
        assertEquals("QUARANTINED",jdbc.queryForObject("SELECT execution_mode FROM ai_ops_skill WHERE project_id=?",String.class,project));
        assertEquals("ENABLED",jdbc.queryForObject("SELECT execution_mode FROM ai_ops_skill WHERE project_id=?",String.class,project+"-other"));
        assertEquals(1,jdbc.queryForObject("SELECT current_version FROM ai_ops_skill WHERE project_id=?",Integer.class,project));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_skill_version WHERE project_id=?",Integer.class,project));
        assertThrows(IllegalStateException.class,()->recovery.recoverRelease(release(2)));
    }
    @Test void rollbackDependenciesRequireCurrentEnabledVersionsAndRejectUnknownToolContracts() {
        var safety=new JdbcSkillRecoverySafetyAdapter(jdbc);
        String dependency="{\"dependencies\":[{\"type\":\"SKILL\",\"id\":\"dependency\",\"version\":\"1\"}]}";
        seed(project+"-other","dependency",1,"{\"dependencies\":[]}");
        assertEquals(Boolean.FALSE, tx.execute(status->safety.dependenciesCompatible(project,dependency,new HashSet<>(),0)));
        seed(project,"dependency",1,"{\"dependencies\":[]}");
        assertEquals(Boolean.TRUE, tx.execute(status->safety.dependenciesCompatible(project,dependency,new HashSet<>(),0)));
        jdbc.update("UPDATE ai_ops_skill SET current_version=2 WHERE project_id=? AND skill_id='dependency'",project);
        assertEquals(Boolean.FALSE, tx.execute(status->safety.dependenciesCompatible(project,dependency,new HashSet<>(),0)));
        jdbc.update("UPDATE ai_ops_skill SET current_version=1,execution_mode='QUARANTINED' WHERE project_id=? AND skill_id='dependency'",project);
        assertEquals(Boolean.FALSE, tx.execute(status->safety.dependenciesCompatible(project,dependency,new HashSet<>(),0)));
        assertFalse(safety.dependenciesCompatible(project,"{\"dependencies\":[{\"type\":\"TOOLSET\",\"id\":\"tool\",\"version\":\"1\"}]}",new HashSet<>(),0));
    }
}
