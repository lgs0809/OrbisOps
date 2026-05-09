package cn.lgs.orbisops.infrastructure.adapter.repository;

import org.junit.jupiter.api.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** Actual SQL against disposable fixtures; this is not a real model canary observation. */
@Testcontainers(disabledWithoutDocker=true)
class SkillCanaryRevocationMySqlTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("canary_revocation").withUsername("fixture").withPassword("fixture");
    JdbcTemplate jdbc; JdbcSkillReleaseAdapter adapter; String project, candidate, release;
    final String hash="a".repeat(64);
    @BeforeEach void setup() throws Exception {
        var ds=new DriverManagerDataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());
        jdbc=new JdbcTemplate(ds); adapter=new JdbcSkillReleaseAdapter(jdbc);
        Path root=Path.of("").toAbsolutePath();
        while(root!=null&&!Files.exists(root.resolve("scripts/fixtures/ops06-related-skill-tables.sql")))root=root.getParent();
        assertNotNull(root);
        try(var connection=ds.getConnection()) {
            ScriptUtils.executeSqlScript(connection,new FileSystemResource(root.resolve("scripts/fixtures/ops06-related-skill-tables.sql")));
        }
        String ddl=Files.readString(root.resolve("server/db/migrations/sql/ops-intent-memory-skill-channel.sql"));
        for(String table:List.of("ai_ops_skill_patch_candidate","ai_ops_skill_release")) {
            var matcher=Pattern.compile("CREATE TABLE IF NOT EXISTS `"+table+"` \\(.*?;",Pattern.DOTALL).matcher(ddl);
            assertTrue(matcher.find()); jdbc.execute(matcher.group());
        }
        if(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='ai_ops_skill_patch_candidate' AND column_name='artifacts_json'",Integer.class)==0)
            jdbc.execute("ALTER TABLE ai_ops_skill_patch_candidate ADD artifacts_json MEDIUMTEXT");
        project="p-"+UUID.randomUUID();candidate="c-"+UUID.randomUUID();release="r-"+UUID.randomUUID();
    }
    void skill(String p,String id,String execution) {
        jdbc.update("INSERT INTO ai_ops_skill(skill_id,project_id,scope,skill_name,content,execution_mode) VALUES (?,?,'PROJECT',?,'SYNTHETIC',?)",id,p,id,execution);
    }
    void seed(String type,String target) {
        jdbc.update("INSERT INTO ai_ops_skill_patch_candidate(candidate_id,candidate_hash,source_type,project_id,agent_id,target_skill_id,patch_type,base_skill_version,evidence_refs_json,changes_json,eval_cases_json,artifacts_json) VALUES (?,?,'SYNTHETIC_TEST',?,'agent',?,?,1,'[]','{}','[]','[]')",candidate,hash,project,target,type);
        jdbc.update("INSERT INTO ai_ops_skill_release(release_id,candidate_id,project_id,agent_id,target_skill_id,status) VALUES (?,?,?,'agent',?,'CANARY')",release,candidate,project,target);
    }
    void visible(boolean expected) {
        assertEquals(expected,!adapter.findCanaryCandidates(project,"agent",20).isEmpty());
        assertEquals(expected,adapter.findFrozenCandidate(candidate,project,hash,release,"agent").isPresent());
    }
    @Test void disablingOrPausingTargetRevokesNewAndFrozenCanariesImmediatelyAndRecoveryRestoresAccess() {
        skill(project,"method","ENABLED"); seed("PATCH","method"); visible(true);
        for(String mode:List.of("DISABLED","QUARANTINED","SHADOW_ONLY")) {
            jdbc.update("UPDATE ai_ops_skill SET execution_mode=? WHERE project_id=?",mode,project);visible(false);
        }
        jdbc.update("UPDATE ai_ops_skill SET execution_mode='ENABLED',lifecycle_status='PAUSED' WHERE project_id=?",project);visible(false);
        jdbc.update("UPDATE ai_ops_skill SET lifecycle_status='ACTIVE' WHERE project_id=?",project);visible(true);
    }
    @Test void aCreateCandidateNeedsNoPublishedEntryButCannotBypassAnExplicitlyDisabledTarget() {
        seed("CREATE",""); visible(true);
        skill(project,"evolved-"+hash.substring(0,16),"DISABLED"); visible(false);
        jdbc.update("UPDATE ai_ops_skill SET execution_mode='ENABLED' WHERE project_id=?",project); visible(true);
        jdbc.update("UPDATE ai_ops_skill_release SET status='ACTIVE',target_skill_id=? WHERE release_id=?","evolved-"+hash.substring(0,16),release);
        assertTrue(adapter.findFrozenCandidate(candidate,project,hash,release,"agent").isPresent());
        jdbc.update("UPDATE ai_ops_skill SET execution_mode='DISABLED' WHERE project_id=?",project);
        assertTrue(adapter.findFrozenCandidate(candidate,project,hash,release,"agent").isEmpty());
    }
    @Test void anotherProjectsEnabledSkillCannotAuthorizeAnAbsentLocalPatchTarget() {
        skill(project+"-other","method","ENABLED");seed("PATCH","method");visible(false);
        skill(project,"method","ENABLED");visible(true);
        assertTrue(adapter.findFrozenCandidate(candidate,project+"-other",hash,release,"agent").isEmpty());
    }
    @Test void candidateAndReleaseMustBothPermitTheAgentAndProjectAndProjectScope() {
        skill(project,"method","ENABLED");seed("PATCH","method");visible(true);
        jdbc.update("UPDATE ai_ops_skill_release SET agent_id='another' WHERE release_id=?",release);visible(false);
        jdbc.update("UPDATE ai_ops_skill_release SET agent_id='agent' WHERE release_id=?",release);
        jdbc.update("UPDATE ai_ops_skill_patch_candidate SET agent_id='another' WHERE candidate_id=?",candidate);visible(false);
        jdbc.update("UPDATE ai_ops_skill_patch_candidate SET agent_id='agent',project_id=? WHERE candidate_id=?",project+"-other",candidate);visible(false);
        jdbc.update("UPDATE ai_ops_skill_patch_candidate SET project_id=?,scope='GLOBAL' WHERE candidate_id=?",project,candidate);visible(false);
    }
}
