package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.episode.*;
import cn.lgs.orbisops.application.skill.*;
import cn.lgs.orbisops.domain.skill.model.*;
import cn.lgs.orbisops.domain.skill.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static cn.lgs.orbisops.infrastructure.adapter.repository.TaskEpisodeJson.*;

/** Synthetic tasks in disposable MySQL; actual transactions, acceptance API, durable jobs and candidate writes. No model result is claimed. */
@Testcontainers(disabledWithoutDocker=true)
class SkillEvolutionMySqlTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("skill_evolution").withUsername("fixture").withPassword("fixture")
            .withCommand("--max-allowed-packet=67108864");
    JdbcTemplate jdbc; DefaultListableBeanFactory beans; PlatformTransactionManager tx;
    JdbcTaskEpisodeStore episodes; JdbcTaskAcceptanceStore acceptance; JdbcSkillEvolutionJobRepository jobs;
    JdbcSkillEvolutionSourceReader sources; JdbcSkillPatchCandidateAdapter candidates;
    String project; AtomicInteger decisions;
    HikariDataSource testDataSource;
    @Test void publicationProjectionTracksLifecycleWithoutBorrowingAnotherProjectOrChangingAudit() {
        var created=new SkillPatchCandidateApplicationService(candidates).create(candidateRequest(task()));
        String candidate=String.valueOf(created.get("candidate_id"));
        var ref=new SkillEvolutionDiagnosticPort.PublicationRef(project,candidate);
        var foreign=new SkillEvolutionDiagnosticPort.PublicationRef(project+"-other",candidate);
        var diagnostics=new JdbcSkillEvolutionDiagnostic(jdbc);
        assertEquals("CANDIDATE",diagnostics.publications(List.of(ref)).get(ref).status());
        assertTrue(diagnostics.publications(List.of(foreign)).isEmpty());
        jdbc.update("INSERT INTO ai_ops_skill_release(release_id,candidate_id,project_id,target_skill_id,status,released_version) VALUES (?,?,?,'target','PENDING_INDEX',2)","r-"+UUID.randomUUID(),candidate,project);
        assertEquals("PENDING_INDEX",diagnostics.publications(List.of(ref)).get(ref).status());
        jdbc.update("UPDATE ai_ops_skill_release SET status='ACTIVE' WHERE candidate_id=?",candidate);
        assertEquals(new SkillEvolutionDiagnosticPort.PublicationState("ACTIVE","CREATE",List.of("target"),2),diagnostics.publications(List.of(ref)).get(ref));
        jdbc.update("INSERT INTO ai_ops_skill_atomic_publication(candidate_id,project_id,operation,status,plan_json,plan_hash) VALUES (?,?,'SPLIT_SKILL','STAGED',?,?)",candidate,project,"{\"targets\":[{\"skillId\":\"split-a\"},{\"skillId\":\"split-b\"}]}","a".repeat(64));
        var staged=diagnostics.publications(List.of(ref,foreign));assertEquals(1,staged.size());
        assertEquals("STAGED",staged.get(ref).status());assertEquals(List.of("split-a","split-b"),staged.get(ref).targetSkillIds());
        jdbc.update("UPDATE ai_ops_skill_atomic_publication SET status='ROLLED_BACK' WHERE candidate_id=?",candidate);
        assertEquals("ROLLED_BACK",diagnostics.publications(List.of(ref)).get(ref).status());
        assertEquals("CANDIDATE",jdbc.queryForObject("SELECT status FROM ai_ops_skill_patch_candidate WHERE candidate_id=?",String.class,candidate));
    }
    @Test void savedExperienceDetailRequiresExactProjectJobAndSourceAndMarksSupersededEvidence() {
        var fixture=task();accept(fixture);enqueue(fixture);
        var claim=jobs.claimPending(3).orElseThrow();var input=sources.load(claim);
        var method=new SkillMethodMemory.Method("Verify the isolated version",List.of("Read-only target"),
                List.of("Read the target version"),List.of("Compare with the approved version"),List.of("VERSION_READ"));
        new JdbcSkillExperienceGroupingStore(jdbc).saveFact(claim,input.sourceHash(),
                new SkillMethodMemory.Extraction(method,"{\"privateProviderPayload\":\"NOT_FOR_API\"}"));
        var diagnostics=new JdbcSkillEvolutionDiagnostic(jdbc);
        var detail=diagnostics.savedExperience(project,claim.jobId(),claim.sourceId());
        assertEquals(method.view(),detail.get("method"));assertEquals(true,detail.get("currentSource"));
        assertFalse(detail.toString().contains("NOT_FOR_API"));
        assertTrue(diagnostics.savedExperience(project+"-other",claim.jobId(),claim.sourceId()).isEmpty());
        assertTrue(diagnostics.savedExperience(project,claim.jobId()+"-other",claim.sourceId()).isEmpty());
        assertTrue(diagnostics.savedExperience(project,claim.jobId(),claim.sourceId()+"-other").isEmpty());
        // A new conversation turn supersedes the accepted Episode through its normal assignment path.
        turn(fixture.session(),3,fixture.episode(),"Correction: inspect the next version","fixture-service");
        assertEquals(false,diagnostics.savedExperience(project,claim.jobId(),claim.sourceId()).get("currentSource"));
    }
    @Test void failedJobKeepsItsOwnAuthoredReleaseVisibleWithoutChangingAuditOrExposingRawReasons() {
        var f=proposals();try {
            var claim=f.claims().get(0);var plan=f.plans().get(0);
            var service=new SkillPatchCandidateApplicationService(candidates);
            String id=text(service.createEvolution(proposalRequest(f.tasks().get(0),plan),claim,
                    sources.load(claim).sourceHash()).get("candidate_id"));
            jobs.rescheduleOrFail(claim,new SkillEvolutionRetryTransition(SkillEvolutionJobStatus.FAILED,claim.attempts()+1,null),"SYNTHETIC_SOURCE_FAILURE");
            jdbc.update("INSERT INTO ai_ops_skill_release(release_id,candidate_id,project_id,target_skill_id,status,released_version,reason_code) VALUES (?,?,?,'target','ROLLED_BACK',0,?)",
                    "r-"+UUID.randomUUID(),id,project,"POLICY_PUBLICATION_BASELINE_STALE:SKILL_EVOLUTION_RELATED_SKILL_CHANGED");
            var diagnostics=new JdbcSkillEvolutionDiagnostic(jdbc);
            var detail=diagnostics.authoredPublication(project,claim.jobId(),claim.sourceId());
            assertEquals(id,detail.get("candidateId"));assertEquals("ROLLED_BACK",detail.get("status"));
            assertEquals("BASELINE_STALE",detail.get("reason"));assertEquals(0,((Number)detail.get("releasedVersion")).intValue());
            assertTrue(diagnostics.authoredPublication(project+"-other",claim.jobId(),claim.sourceId()).isEmpty());
            assertTrue(diagnostics.authoredPublication(project,claim.jobId()+"-other",claim.sourceId()).isEmpty());
            assertTrue(diagnostics.authoredPublication(project,claim.jobId(),claim.sourceId()+"-other").isEmpty());
            jdbc.update("UPDATE ai_ops_skill_release SET reason_code='provider-secret' WHERE candidate_id=?",id);
            assertFalse(diagnostics.authoredPublication(project,claim.jobId(),claim.sourceId()).toString().contains("provider-secret"));
            assertEquals(hash(plan.authoredJson()),jdbc.queryForObject("SELECT authored_hash FROM ai_ops_skill_evolution_proposal WHERE plan_id=?",String.class,plan.planId()));
            assertEquals("FAILED",jdbc.queryForObject("SELECT status FROM ai_ops_skill_evolution_job WHERE job_id=?",String.class,claim.jobId()));
        }finally{settle(f);}
    }
    @Test void authoredDecisionProjectsVerifiedNoChangeWithoutChangingAuditOrBorrowingAnotherSource() {
        var fixtures=List.of(task(),task("different-condition"),task());
        for(var fixture:fixtures) {accept(fixture);enqueue(fixture);}
        var claims=List.of(jobs.claimPending(3).orElseThrow(),jobs.claimPending(3).orElseThrow(),jobs.claimPending(3).orElseThrow());
        var claim=claims.get(0);
        try {
        var frozen=freezeProposal(claim,fixtures,List.of(),true,20);
        String reason="SYNTHETIC existing method covers the read-only check; no publication needed";
        var plan=proxy(new JdbcSkillEvolutionProposalAdapter(jdbc)).authored(claim,frozen,
                Map.of("patchType","NO_CHANGE","reason",reason,"authoringSource","SYNTHETIC_TEST",
                        "privateProviderPayload","NOT_FOR_API"));
        jobs.rescheduleOrFail(claim,new SkillEvolutionRetryTransition(SkillEvolutionJobStatus.SKIPPED,1,null),"SYNTHETIC_TEST_FINISHED");
        var diagnostics=new JdbcSkillEvolutionDiagnostic(jdbc);
        var decision=diagnostics.authoredDecision(project,claim.jobId(),claim.sourceId()).orElseThrow();
        assertEquals(new SkillEvolutionDiagnosticPort.AuthoredDecision(plan.planId(),"NO_CHANGE",reason,"SYNTHETIC_TEST","",true),decision);
        assertFalse(decision.toString().contains("NOT_FOR_API"));
        assertTrue(diagnostics.authoredPublication(project,claim.jobId(),claim.sourceId()).isEmpty());
        assertTrue(diagnostics.authoredDecision(project+"-other",claim.jobId(),claim.sourceId()).isEmpty());
        assertTrue(diagnostics.authoredDecision(project,claim.jobId()+"-other",claim.sourceId()).isEmpty());
        assertTrue(diagnostics.authoredDecision(project,claim.jobId(),claim.sourceId()+"-other").isEmpty());
        assertEquals(hash(plan.authoredJson()),jdbc.queryForObject("SELECT authored_hash FROM ai_ops_skill_evolution_proposal WHERE plan_id=?",String.class,plan.planId()));
        assertEquals(plan.authoredJson(),jdbc.queryForObject("SELECT authored_json FROM ai_ops_skill_evolution_proposal WHERE plan_id=?",String.class,plan.planId()));
        turn(fixtures.get(0).session(),3,fixtures.get(0).episode(),"Correct the target","fixture-service");
        assertFalse(diagnostics.authoredDecision(project,claim.jobId(),claim.sourceId()).orElseThrow().currentSource());
        jdbc.update("UPDATE ai_ops_skill_evolution_proposal SET authored_hash=? WHERE plan_id=?","0".repeat(64),plan.planId());
        assertTrue(diagnostics.authoredDecision(project,claim.jobId(),claim.sourceId()).isEmpty());
        } finally {
            for(var held:claims) jobs.rescheduleOrFail(held,new SkillEvolutionRetryTransition(SkillEvolutionJobStatus.SKIPPED,held.attempts()+1,null),"SYNTHETIC_TEST_FINISHED");
        }
    }
    @Test void failureDiagnosticsAreBoundToProjectJobAndCurrentAttemptAndNeverReturnRawPayloads() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_ops_config_audit(id BIGINT AUTO_INCREMENT PRIMARY KEY,project_id VARCHAR(128),target_id VARCHAR(256),module_name VARCHAR(64),action_name VARCHAR(64),after_json TEXT)");
        String job="diagnostic-"+UUID.randomUUID();
        java.util.function.BiConsumer<String,String> insert=(scope,payload)->jdbc.update("INSERT INTO ai_ops_config_audit(project_id,target_id,module_name,action_name,after_json) VALUES (?,?,'skill-evolver','job-fail',?)",scope,job,payload);
        insert.accept(project,"{\"error\":\"SKILL_GROUPING_DEFERRED: IllegalArgumentException[SKILL_AUTHORING_INPUT_TOO_LARGE]\",\"attempts\":2}");
        insert.accept(project+"-other","{\"error\":\"HTTP timeout\",\"attempts\":2}");
        var diagnostics=new JdbcSkillEvolutionDiagnostic(jdbc);
        assertEquals("SKILL_AUTHORING_INPUT_TOO_LARGE",diagnostics.currentFailure(project,job,2));
        assertEquals("",diagnostics.currentFailure(project,job,3));
        assertEquals("",diagnostics.currentFailure(project,job+"missing",2));
        assertEquals("BACKGROUND_MODEL_TIMEOUT",diagnostics.currentFailure(project+"-other",job,2));
        assertEquals("BACKGROUND_FAILURE_RECORDED",JdbcSkillEvolutionDiagnostic.classify("unknown provider failure token=private"));
        assertEquals("SKILL_AUTHORING_MODEL_INVALID",JdbcSkillEvolutionDiagnostic.classify("SKILL_GROUPING_DEFERRED: JSONException -> JSONException"));
        insert.accept(project,"{\"error\":\"SKILL_AUTHORING_MODEL_INVALID\",\"attempts\":3}");
        assertEquals("SKILL_AUTHORING_MODEL_INVALID",diagnostics.currentFailure(project,job,3));
    }
    @BeforeEach void setup() throws Exception {
        // claimPending is deliberately a global worker queue. Some cases retain a live
        // claim to test fencing, so a slow suite must not let that claim expire into a
        // later case's queue. Each case gets its own schema in this disposable container;
        // application leases and provenance checks remain identical to production.
        String schema="skill_evolution_"+UUID.randomUUID().toString().replace("-","");
        var bootstrap=new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(),"root",MYSQL.getPassword()));
        bootstrap.execute("CREATE DATABASE `"+schema+"`");
        bootstrap.execute("GRANT ALL ON `"+schema+"`.* TO '"+MYSQL.getUsername()+"'@'%'");
        // Reuse real JDBC connections within this case, while retaining separate
        // schemas and enough connections for the eight-worker race regression.
        var ds=new HikariDataSource();testDataSource=ds;
        ds.setJdbcUrl(MYSQL.getJdbcUrl().replace("/"+MYSQL.getDatabaseName(),"/"+schema));
        ds.setUsername(MYSQL.getUsername());ds.setPassword(MYSQL.getPassword());
        ds.setMaximumPoolSize(8);ds.setMinimumIdle(0);ds.setConnectionTimeout(30_000);
        jdbc=new JdbcTemplate(ds);beans=new DefaultListableBeanFactory();tx=new DataSourceTransactionManager(ds);
        beans.registerSingleton("mysqlJdbcTemplate",jdbc);beans.registerSingleton("mysqlTransactionManager",tx);
        Path root=Path.of("").toAbsolutePath();while(root!=null&&!Files.exists(root.resolve("db/migrations/sql/ops-task-episodes.sql")))root=root.getParent();assertNotNull(root);
        Path sql=root.resolve("db/migrations/sql");
        try(var c=ds.getConnection()) {ScriptUtils.executeSqlScript(c,new FileSystemResource(root.getParent().resolve("scripts/fixtures/ops06-related-skill-tables.sql")));}
        try(var c=ds.getConnection()) {ScriptUtils.executeSqlScript(c,new FileSystemResource(root.getParent().resolve("scripts/fixtures/ops06-published-skill-tables.sql")));}
        for(String table:List.of("ai_ops_skill_evolution_job","ai_ops_skill_evolution_patch")) create(sql.resolve("ops-skill-evolution.sql"),table);
        create(sql.resolve("ops-intent-memory-skill-channel.sql"),"ai_ops_skill_patch_candidate");
        create(sql.resolve("ops-intent-memory-skill-channel.sql"),"ai_ops_skill_release");
        try(var c=ds.getConnection()) {ScriptUtils.executeSqlScript(c,new FileSystemResource(sql.resolve("ops-skill-atomic-publication.sql")));}
        if(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='ai_ops_skill_patch_candidate' AND column_name='artifacts_json'",Integer.class)==0)
            jdbc.execute("ALTER TABLE ai_ops_skill_patch_candidate ADD COLUMN artifacts_json MEDIUMTEXT NULL");
        try(var c=ds.getConnection()) {for(String name:List.of("ops-task-episodes","ops-task-acceptance","ops-skill-evolution-sources","ops-skill-experience-layer","ops-alert-incident-correlation","ops-skill-evolution-proposals","ops-skill-evolution-proposal-history","ops-skill-semantic-experience","ops-skill-source-batches","ops-skill-retry-accounting"))
            ScriptUtils.executeSqlScript(c,new FileSystemResource(sql.resolve(name+".sql")));}
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_ops_chat_session(session_id VARCHAR(80) PRIMARY KEY,project_id VARCHAR(128),user_id VARCHAR(80))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_ops_chat_message(id BIGINT AUTO_INCREMENT PRIMARY KEY,project_id VARCHAR(128),session_id VARCHAR(80),turn_id VARCHAR(80),message_seq BIGINT,role VARCHAR(32),content MEDIUMTEXT,metadata TEXT,create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,UNIQUE(session_id,message_seq))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_ops_agent_run(run_id VARCHAR(80) PRIMARY KEY,project_id VARCHAR(128),session_id VARCHAR(80),status VARCHAR(40),agent_id VARCHAR(128) DEFAULT 'fixture-agent',updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_ops_agent_run_checkpoint(id BIGINT AUTO_INCREMENT PRIMARY KEY,run_id VARCHAR(80),project_id VARCHAR(128),checkpoint_seq BIGINT,checkpoint_type VARCHAR(64),checkpoint_json MEDIUMTEXT,checkpoint_hash VARCHAR(64))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_ops_agent_node_trace(id BIGINT AUTO_INCREMENT PRIMARY KEY,run_id VARCHAR(80),sequence_no BIGINT,event_type VARCHAR(80),status VARCHAR(40),summary TEXT,payload_json MEDIUMTEXT)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_ops_tool_result(id BIGINT AUTO_INCREMENT PRIMARY KEY,project_id VARCHAR(128),run_id VARCHAR(80),result_id VARCHAR(80),tool_name VARCHAR(80),status VARCHAR(40),output_hash VARCHAR(64),full_output MEDIUMTEXT,source VARCHAR(80))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_ops_runtime_context_bundle(id BIGINT AUTO_INCREMENT PRIMARY KEY,project_id VARCHAR(128),session_id VARCHAR(80),run_id VARCHAR(80),bundle_id VARCHAR(80),bundle_hash VARCHAR(64),bundle_json TEXT)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_ops_incident(incident_id VARCHAR(80) PRIMARY KEY,project_id VARCHAR(128))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_ops_incident_run(incident_id VARCHAR(80),run_id VARCHAR(80),UNIQUE(incident_id,run_id))");
        episodes=new JdbcTaskEpisodeStore(beans.getBeanProvider(JdbcTemplate.class),beans.getBeanProvider(PlatformTransactionManager.class));
        acceptance=new JdbcTaskAcceptanceStore(beans.getBeanProvider(JdbcTemplate.class),beans.getBeanProvider(PlatformTransactionManager.class));
        jobs=proxy(new JdbcSkillEvolutionJobRepository(beans.getBeanProvider(JdbcTemplate.class)));
        sources=new JdbcSkillEvolutionSourceReader(jdbc);candidates=proxy(new JdbcSkillPatchCandidateAdapter(jdbc));
        project="p-"+UUID.randomUUID();decisions=new AtomicInteger();
    }
    @AfterEach void closeTestConnections() {if(testDataSource!=null)testDataSource.close();}

    @Test
    @EnabledIfSystemProperty(named="orbisops.acceptance.realLeaseWait",matches="true")
    void realFiveMinuteLeaseExpiryRecoversExactlyOnceAndFencesLateWorker() throws Exception {
        var fixture=task();accept(fixture);enqueue(fixture);
        var old=jobs.claimPending(3).orElseThrow();
        long lease=jdbc.queryForObject("SELECT lease_until_ms FROM ai_ops_skill_evolution_job_state WHERE job_id=?",Long.class,old.jobId());
        long now=jdbc.queryForObject("SELECT CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000 AS UNSIGNED)",Long.class);
        assertTrue(lease-now>=295_000&&lease-now<=300_000,"Must wait for the production five-minute lease");
        assertTrue(jobs.claimPending(3).isEmpty(),"A live owner must not be reclaimed");
        long started=System.nanoTime();
        while(jdbc.queryForObject("SELECT CAST(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000 AS UNSIGNED)",Long.class)<=lease)Thread.sleep(1_000);
        assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime()-started)>=295);
        // No UPDATE of lease_until_ms: this is actual database-clock expiry.
        var recovered=jobs.claimPending(3).orElseThrow();
        assertEquals(old.jobId(),recovered.jobId());assertEquals(old.sourceId(),recovered.sourceId());
        assertTrue(recovered.epoch()>old.epoch());assertEquals(old.attempts()+1,recovered.attempts());
        assertTrue(jobs.claimPending(3).isEmpty());assertFalse(jobs.renewLease(old));
        assertTrue(jobs.complete(old,patch(old),SkillEvolutionJobStatus.COMPLETED).isEmpty());
        assertTrue(jobs.complete(recovered,patch(recovered),SkillEvolutionJobStatus.COMPLETED).isPresent());
        assertTrue(jobs.claimPending(3).isEmpty());
    }
    @SuppressWarnings("unchecked") <T>T proxy(T target) {
        var factory=new ProxyFactory(target);factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(tx,new AnnotationTransactionAttributeSource()));return (T)factory.getProxy();
    }
    void create(Path file,String table) throws Exception {
        var pattern=Pattern.compile("CREATE TABLE IF NOT EXISTS `"+table+"` \\(.*?;",Pattern.DOTALL);
        var matcher=pattern.matcher(Files.readString(file));assertTrue(matcher.find());jdbc.execute(matcher.group());
    }
    record Fixture(String session,String run,String episode,String result,String hash,long revision) { }
    Fixture task() {return task("fixture-service");}
    Fixture task(String service) {String s="s-"+UUID.randomUUID();jdbc.update("INSERT INTO ai_ops_chat_session VALUES (?,?,'owner')",s,project);return turn(s,1,"","ACCEPTED_GOAL_INITIAL",service);}
    Fixture turn(String session,long seq,String prior,String message) {return turn(session,seq,prior,message,"fixture-service");}
    Fixture turn(String session,long seq,String prior,String message,String service) {
        String run="r-"+UUID.randomUUID(),result="result-"+UUID.randomUUID();
        jdbc.update("INSERT INTO ai_ops_agent_run(run_id,project_id,session_id,status) VALUES (?,?,?,'SUCCEEDED')",run,project,session);
        for(int i=0;i<2;i++)jdbc.update("INSERT INTO ai_ops_chat_message(project_id,session_id,turn_id,message_seq,role,content,metadata) VALUES (?,?,?,?,?,?, '{}')",project,session,run,seq+i,i==0?"user":"assistant",i==0?message:"SYNTHETIC final report "+message);
        var content=Map.of("scope",Map.of("projectId",project,"environment","synthetic-test","serviceId",service),"resourceIdentity","fixture-resource","status","AVAILABLE","version","fixture-1");
        String raw=cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringify(Map.of("normalizedContent",content,"structuredContent",content,"isError",false,"orbisopsResultVersion",1));
        jdbc.update("INSERT INTO ai_ops_tool_result(project_id,run_id,result_id,tool_name,status,output_hash,full_output,source) VALUES (?,?,?,'fixture_version','SUCCEEDED',?,?,'MCP_REMOTE_TOOL')",project,run,result,hash(raw),raw);
        jdbc.update("INSERT INTO ai_ops_agent_node_trace(run_id,sequence_no,event_type,status,summary,payload_json) VALUES (?,1,'FINAL_OUTPUT','SUCCEEDED','SYNTHETIC final',?)",run,json(Map.of("content","SYNTHETIC report "+message)));
        episodes.captureContext(project,session,run,Map.of("originalUserQuery",message,"memoryContext","CONTEXT_OF_OTHER_GOALS_MUST_NOT_ENTER"));
        episodes.discover(1000);var claim=episodes.claimTurn(session,"synthetic-classifier",System.currentTimeMillis(),true).orElseThrow();
        assertTrue(episodes.assign(claim,new TaskEpisodeModelPort.Decision(prior.isBlank()?"CREATE":"CONTINUE",prior,prior.isBlank()?"核验隔离目标版本":"","SYNTHETIC classifier decision"),System.currentTimeMillis()));
        String episode=jdbc.queryForObject("SELECT episode_id FROM ai_ops_task_episode_turn WHERE source_run_ref=?",String.class,run);
        long revision=jdbc.queryForObject("SELECT revision FROM ai_ops_task_episode WHERE episode_id=?",Long.class,episode);
        return new Fixture(session,run,episode,result,hash(raw),revision);
    }
    void accept(Fixture f) {
        var request=new TaskAcceptanceRequest("request-"+UUID.randomUUID(),f.revision(),"Synthetic fixture: verify declared target version",List.of(new TaskAcceptanceRequest.Criterion(f.result(),f.hash(),"/version","EQ","fixture-1")));
        assertEquals("SUCCEEDED",new TaskAcceptanceApplicationService(acceptance).verify(project,f.episode(),request,"owner",false).get("outcome"));
    }
    SkillEvolutionJobApplicationService worker() {
        return new SkillEvolutionJobApplicationService(jobs,new SkillEvolutionJobPolicy(),new SkillEvolutionInputPolicy(),sources,
                request->{decisions.incrementAndGet();return new SkillEvolutionPipelineDecision("SKIPPED","SKIP_TEST_NO_AUTHORING","","","{}","SKIPPED");},
                new SkillEvolutionPatchJsonEncoder(),()->"patch-"+UUID.randomUUID(),null,Clock.systemUTC());
    }
    SkillEvolutionJobSnapshot enqueue(Fixture f) {return worker().enqueue(f.run(),f.session(),project,"fixture-agent","USER_EXPLICIT_REMEMBER").job();}
    SkillEvolutionPatchSnapshot patch(SkillEvolutionJobSnapshot c) {return new SkillEvolutionPatchSnapshot(0,"patch-"+UUID.randomUUID(),c.jobId(),c.runId(),c.projectId(),"","SKIP_TEST_NO_AUTHORING","{}","{}","SKIPPED",null,"",null,null);}
    <T>List<T> concurrent(Callable<T> action) throws Exception {
        var pool=Executors.newFixedThreadPool(8);try{var tasks=new ArrayList<Callable<T>>();for(int i=0;i<8;i++)tasks.add(action);
            var results=new ArrayList<T>();for(var result:pool.invokeAll(tasks))results.add(result.get(20,TimeUnit.SECONDS));return results;
        }finally{pool.shutdownNow();}
    }
    void expire(SkillEvolutionJobSnapshot c) {jdbc.update("UPDATE ai_ops_skill_evolution_job_state SET lease_until_ms=1 WHERE job_id=?",c.jobId());}
    Map<String,Object> candidateRequest(Fixture f) {return Map.of("projectId",project,"agentId","fixture-agent","sourceRunId",f.run(),"sourceType","SUCCESSFUL_DIAGNOSTIC_PATTERN","patchType","CREATE","riskLevel","LOW","changes",List.of(),"artifacts",List.of(),"evalCases",List.of());}

    @Test void acceptedLargeToolEvidenceIsArchivedWithoutTruncationAndRevalidatedAfterRestart() {
        var f=task();accept(f);
        String raw=json(Map.of("fixtureProvenance","SYNTHETIC_LARGE_EVIDENCE","data","保留完整原始证据".repeat(90_000)));
        jdbc.update("INSERT INTO ai_ops_tool_result(project_id,run_id,result_id,tool_name,status,output_hash,full_output,source) VALUES (?,?,?,'fixture_detail','SUCCEEDED',?,?,'MCP_REMOTE_TOOL')",
                project,f.run(),"detail-"+f.result(),hash(raw),raw);
        var queued=enqueue(f);assertFalse(queued.sourceId().isBlank());var claim=jobs.claimPending(3).orElseThrow();
        try {
            var first=sources.load(claim);assertTrue(first.episodeJson().length()>500_000);
            var restarted=new JdbcSkillEvolutionSourceReader(jdbc).load(claim);
            assertEquals(first.sourceHash(),restarted.sourceHash());assertTrue(restarted.episodeJson().contains("保留完整原始证据".repeat(90_000)));
        } finally {jobs.complete(claim,patch(claim),SkillEvolutionJobStatus.SKIPPED);}
    }

    @Test void acceptanceRedispatchesSkippedRunAndWholeEpisodeExcludesOtherChatGoals() {
        var first=task();var unrelated=turn(first.session(),3,"","UNRELATED_NEW_GOAL_SENTINEL");
        var last=turn(first.session(),5,first.episode(),"继续");
        var initial=enqueue(last);assertTrue(initial.sourceId().isBlank());worker().runBatch(1,3);
        assertEquals(0,decisions.get());assertEquals(SkillEvolutionJobStatus.SKIPPED,jobs.findJob(initial.jobId()).orElseThrow().status());
        accept(last);var queued=worker().enqueueUnobservedCompletedRuns(100);
        assertTrue(queued.stream().anyMatch(q->q.job()!=null && q.job().jobId().equals(initial.jobId())));
        var claim=jobs.claimPending(3).orElseThrow();var input=sources.load(claim);
        assertFalse(input.episodeJson().contains("UNRELATED_NEW_GOAL_SENTINEL"));assertFalse(input.episodeJson().contains("CONTEXT_OF_OTHER_GOALS_MUST_NOT_ENTER"));
        assertTrue(input.episodeJson().contains(first.run()));assertTrue(input.episodeJson().contains(last.run()));assertFalse(input.episodeJson().contains(unrelated.run()));
        assertEquals(hash(input.episodeJson()),input.sourceHash());assertEquals("核验隔离目标版本",new SkillEvolutionInputPolicy().summarize(input).normalizedUserGoal());
        assertEquals(4,input.messages().size());assertEquals(2,new SkillEvolutionInputPolicy().summarize(input).evidenceReferences().size());
        assertTrue(jobs.complete(claim,patch(claim),SkillEvolutionJobStatus.SKIPPED).isPresent());
        assertTrue(worker().enqueueUnobservedCompletedRuns(100).stream().noneMatch(q->q.job()!=null&&q.job().jobId().equals(initial.jobId())));
    }
    @Test void eightConcurrentEnqueuesClaimsAndCompletionsHaveOneDurableResult() throws Exception {
        var f=task();accept(f);var queued=concurrent(()->enqueue(f));assertEquals(1,queued.stream().map(SkillEvolutionJobSnapshot::jobId).distinct().count());
        var beforeClaim=jobs.findJob(queued.get(0).jobId()).orElseThrow();
        var claimed=concurrent(()->jobs.claimPending(3));assertEquals(1,claimed.stream().filter(Optional::isPresent).count(),
                () -> "Queue before="+beforeClaim+" after="+jobs.findJob(beforeClaim.jobId())
                        +" native="+jdbc.queryForList("SELECT j.job_id,j.status,j.attempts,j.next_run_at,s.epoch,s.lease_token,s.lease_until_ms,s.ordinary_failures,CURRENT_TIMESTAMP(3) AS database_now FROM ai_ops_skill_evolution_job j JOIN ai_ops_skill_evolution_job_state s ON s.job_id=j.job_id"));
        var claim=claimed.stream().flatMap(Optional::stream).findFirst().orElseThrow();var patch=patch(claim);
        assertEquals(1,concurrent(()->jobs.complete(claim,patch,SkillEvolutionJobStatus.SKIPPED)).stream().filter(Optional::isPresent).count());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_skill_evolution_source WHERE project_id=?",Integer.class,project));
        assertEquals(1,jobs.findPatches(claim.jobId(),"",10).size());assertEquals(SkillEvolutionJobStatus.SKIPPED,enqueue(f).status());
    }
    @RepeatedTest(32) void contendedSingleJobClaimRetainsOriginalEightWorkerContract() throws Exception {
        eightConcurrentEnqueuesClaimsAndCompletionsHaveOneDurableResult();
    }
    @Test void eightConcurrentExpiredLeaseClaimsChargeOnceAndFenceOriginalWorker() throws Exception {
        var f=task();accept(f);enqueue(f);var old=jobs.claimPending(3).orElseThrow();expire(old);
        var claims=concurrent(()->jobs.claimPending(3));assertEquals(1,claims.stream().filter(Optional::isPresent).count());
        var recovered=claims.stream().flatMap(Optional::stream).findFirst().orElseThrow();
        assertEquals(old.jobId(),recovered.jobId());assertEquals(old.sourceId(),recovered.sourceId());
        assertEquals(old.attempts()+1,recovered.attempts());assertEquals(old.ordinaryFailures()+1,recovered.ordinaryFailures());
        assertEquals(old.epoch()+1,recovered.epoch());assertFalse(jobs.renewLease(old));
        assertTrue(jobs.complete(old,patch(old),SkillEvolutionJobStatus.COMPLETED).isEmpty());
        assertTrue(jobs.complete(recovered,patch(recovered),SkillEvolutionJobStatus.COMPLETED).isPresent());
        assertEquals(1,jobs.findPatches(recovered.jobId(),"",10).size());assertTrue(jobs.claimPending(3).isEmpty());
    }
    @Test void eightConcurrentClaimsOfDistinctJobsRetainEverySource() throws Exception {
        for(int i=0;i<8;i++){var f=task();accept(f);enqueue(f);}
        var claims=concurrent(()->jobs.claimPending(3)).stream().flatMap(Optional::stream).toList();
        assertEquals(8,claims.size());assertEquals(8,claims.stream().map(SkillEvolutionJobSnapshot::jobId).distinct().count());
        assertEquals(8,claims.stream().map(SkillEvolutionJobSnapshot::sourceId).distinct().count());
        var next=new AtomicInteger();
        assertEquals(8,concurrent(()->{var claim=claims.get(next.getAndIncrement());
            return jobs.complete(claim,patch(claim),SkillEvolutionJobStatus.COMPLETED);}).stream().filter(Optional::isPresent).count());
        assertEquals(8,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_skill_evolution_patch",Integer.class));
        assertTrue(jobs.claimPending(3).isEmpty());
    }
    SkillEvolutionPatchSnapshot noPattern(SkillEvolutionJobSnapshot claim) {
        return new SkillEvolutionPatchSnapshot(0,"patch-"+UUID.randomUUID(),claim.jobId(),claim.runId(),claim.projectId(),
                "","SKIP_NO_REUSABLE_PATTERN","{}","{}","SKIPPED",null,"SKIP_NO_REUSABLE_PATTERN",null,null);
    }
    @Test void explicitRememberReconsidersSkippedBackgroundOnceWithoutAddingSources() throws Exception {
        var f=task();accept(f);
        var original=worker().enqueue(f.run(),f.session(),project,"fixture-agent","RUN_COMPLETED_BACKGROUND").job();
        var first=jobs.claimPending(3).orElseThrow();jobs.complete(first,noPattern(first),SkillEvolutionJobStatus.SKIPPED);
        var upgrades=concurrent(()->enqueue(f));
        assertEquals(1,upgrades.stream().map(SkillEvolutionJobSnapshot::jobId).distinct().count());
        var second=jobs.claimPending(3).orElseThrow();assertEquals(original.sourceId(),second.sourceId());
        assertEquals("USER_EXPLICIT_REMEMBER",second.triggerReason());
        jobs.complete(second,noPattern(second),SkillEvolutionJobStatus.SKIPPED);
        assertEquals(SkillEvolutionJobStatus.SKIPPED,enqueue(f).status());assertTrue(jobs.claimPending(3).isEmpty());
        assertEquals(2,jobs.findPatches(original.jobId(),"",10).size());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_skill_evolution_source WHERE project_id=?",Integer.class,project));
    }
    @Test void explicitRequestDuringBackgroundAttemptIsNotLostOrCountedAsAnotherSource() {
        var f=task();accept(f);
        worker().enqueue(f.run(),f.session(),project,"fixture-agent","RUN_COMPLETED_BACKGROUND");
        var first=jobs.claimPending(3).orElseThrow();enqueue(f);
        jobs.complete(first,noPattern(first),SkillEvolutionJobStatus.SKIPPED);
        var second=jobs.claimPending(3).orElseThrow();assertEquals("USER_EXPLICIT_REMEMBER",second.triggerReason());
        assertEquals(first.sourceId(),second.sourceId());jobs.complete(second,noPattern(second),SkillEvolutionJobStatus.SKIPPED);
        assertTrue(jobs.claimPending(3).isEmpty());
    }
    @Test void heartbeatExtendsLiveOwnershipButCannotReviveExpiredOrReplacedAttempts() {
        var f=task();accept(f);enqueue(f);var claim=jobs.claimPending(3).orElseThrow();
        jdbc.update("UPDATE ai_ops_skill_evolution_job_state SET lease_until_ms=UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000+10000 WHERE job_id=?",claim.jobId());
        var shortened=jobs.findJob(claim.jobId()).orElseThrow();
        assertTrue(jobs.renewLease(claim));
        var renewed=jobs.findJob(claim.jobId()).orElseThrow();
        assertTrue(renewed.leaseUntilMillis()>shortened.leaseUntilMillis()+250000);
        assertEquals(shortened.epoch(),renewed.epoch());assertEquals(shortened.attempts(),renewed.attempts());
        assertEquals(shortened.sourceId(),renewed.sourceId());assertTrue(jobs.claimPending(3).isEmpty());
        expire(claim);assertFalse(jobs.renewLease(claim));
        var replacement=jobs.claimPending(3).orElseThrow();
        assertFalse(jobs.renewLease(claim));assertTrue(jobs.renewLease(replacement));
        assertTrue(jobs.complete(claim,patch(claim),SkillEvolutionJobStatus.SKIPPED).isEmpty());
        assertTrue(jobs.complete(replacement,patch(replacement),SkillEvolutionJobStatus.SKIPPED).isPresent());
        assertFalse(jobs.renewLease(replacement));
    }
    @Test void expiredWorkerCannotCompleteRetryOrCreateCandidateAfterNewClaim() {
        var f=task();accept(f);enqueue(f);var old=jobs.claimPending(3).orElseThrow();String sourceHash=sources.load(old).sourceHash();expire(old);
        jobs=proxy(new JdbcSkillEvolutionJobRepository(beans.getBeanProvider(JdbcTemplate.class)));var replacement=jobs.claimPending(3).orElseThrow();
        assertTrue(replacement.epoch()>old.epoch());assertNotEquals(old.leaseToken(),replacement.leaseToken());assertEquals(old.sourceId(),replacement.sourceId());
        assertTrue(jobs.complete(old,patch(old),SkillEvolutionJobStatus.SKIPPED).isEmpty());
        assertFalse(jobs.rescheduleOrFail(old,new SkillEvolutionRetryTransition(SkillEvolutionJobStatus.FAILED,1,null),"late failure"));
        assertThrows(IllegalStateException.class,()->new SkillPatchCandidateApplicationService(candidates).createEvolution(candidateRequest(f),old,sourceHash));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_skill_patch_candidate WHERE project_id=?",Integer.class,project));
        assertTrue(jobs.complete(replacement,patch(replacement),SkillEvolutionJobStatus.SKIPPED).isPresent());
    }
    @Test void correctionsRevokeFrozenInputAndNewAcceptanceRequeuesOnceOldAttemptIsSettled() {
        var first=task();accept(first);enqueue(first);var old=jobs.claimPending(3).orElseThrow();String sha=sources.load(old).sourceHash();
        var correction=turn(first.session(),3,first.episode(),"核对修正后的目标");
        assertThrows(IllegalStateException.class,()->sources.load(old));
        assertThrows(IllegalStateException.class,()->new SkillPatchCandidateApplicationService(candidates).createEvolution(candidateRequest(first),old,sha));
        assertTrue(jobs.rescheduleOrFail(old,new SkillEvolutionRetryTransition(SkillEvolutionJobStatus.SKIPPED,1,null),"SKILL_EVOLUTION_SOURCE_REVOKED"));
        accept(correction);worker().enqueueUnobservedCompletedRuns(100);
        var current=jobs.claimPending(3).orElseThrow();assertEquals(correction.run(),current.runId());assertNotEquals(old.sourceId(),current.sourceId());
        assertTrue(sources.load(current).episodeJson().contains("核对修正后的目标"));
        jobs.complete(current,patch(current),SkillEvolutionJobStatus.SKIPPED);
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_skill_evolution_source WHERE project_id=?",Integer.class,project));
    }
    @Test void leaseRecoveryConsumesFiniteAttemptsAndNewAcceptanceHasItsOwnRetryBudget() {
        var f=task();accept(f);var enqueued=enqueue(f);
        for(int i=0;i<3;i++){var c=jobs.claimPending(3).orElseThrow();assertEquals(i,c.attempts());expire(c);}
        assertTrue(jobs.claimPending(3).isEmpty());assertEquals(SkillEvolutionJobStatus.FAILED,jobs.findJob(enqueued.jobId()).orElseThrow().status());
        assertEquals(SkillEvolutionJobStatus.FAILED,enqueue(f).status());
        accept(f);var requeued=enqueue(f);assertEquals(0,requeued.attempts());assertEquals(SkillEvolutionJobStatus.PENDING,requeued.status());
        var last=jobs.claimPending(3).orElseThrow();jobs.complete(last,patch(last),SkillEvolutionJobStatus.SKIPPED);
    }
    @Test void immutableSourceHashAndCanonicalJobIdentityCannotBeOverwritten() {
        var f=task();accept(f);var first=enqueue(f);
        assertThrows(IllegalArgumentException.class,()->worker().enqueue(f.run(),f.session(),"other-project","fixture-agent","RUN_COMPLETED"));
        assertEquals(project,jobs.findJob(first.jobId()).orElseThrow().projectId());
        var claim=jobs.claimPending(3).orElseThrow();jdbc.update("UPDATE ai_ops_skill_evolution_source SET input_json='{}' WHERE source_id=?",claim.sourceId());
        assertThrows(IllegalStateException.class,()->sources.load(claim));
        assertThrows(IllegalStateException.class,()->jobs.complete(claim,patch(claim),SkillEvolutionJobStatus.SKIPPED));
        assertTrue(jobs.findPatches(claim.jobId(),"",10).isEmpty());jobs.rescheduleOrFail(claim,new SkillEvolutionRetryTransition(SkillEvolutionJobStatus.FAILED,1,null),"corrupted fixture");
    }
    @Test void candidateCommitIsFencedAndRetryReturnsTheSameImmutableCandidate() throws Exception {
        var f=task();accept(f);enqueue(f);var claim=jobs.claimPending(3).orElseThrow();var input=sources.load(claim);
        var service=new SkillPatchCandidateApplicationService(candidates);
        var extra=List.of(task("other-service"),task());for(var other:extra){accept(other);enqueue(other);}worker().runBatch(2,3);
        var proposal=prepareProposal(claim,List.of(f,extra.get(0),extra.get(1)));
        var request=proposalRequest(f,proposal);
        var ids=concurrent(()->service.createEvolution(request,claim,input.sourceHash()).get("candidate_id"));
        assertEquals(1,ids.stream().distinct().count());assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_skill_patch_candidate WHERE project_id=?",Integer.class,project));
        jobs.complete(claim,patch(claim),SkillEvolutionJobStatus.SKIPPED);
        assertThrows(IllegalStateException.class,()->service.createEvolution(candidateRequest(f),claim,input.sourceHash()));
    }
    @Test void patchFailureRollsBackWithoutCompletingOrLosingTheClaim() {
        var f=task();accept(f);enqueue(f);var claim=jobs.claimPending(3).orElseThrow();var valid=patch(claim);
        var invalid=new SkillEvolutionPatchSnapshot(0,valid.patchId(),claim.jobId(),claim.runId(),claim.projectId(),"","X".repeat(200),"{}","{}","SKIPPED",null,"",null,null);
        assertThrows(RuntimeException.class,()->jobs.complete(claim,invalid,SkillEvolutionJobStatus.SKIPPED));
        assertEquals(SkillEvolutionJobStatus.RUNNING,jobs.findJob(claim.jobId()).orElseThrow().status());
        assertTrue(jobs.findPatches(claim.jobId(),"",10).isEmpty());assertTrue(jobs.complete(claim,valid,SkillEvolutionJobStatus.SKIPPED).isPresent());
    }
    @Test void oversizedSourceFailsClosedWhileOtherAcceptedTasksContinue() {
        var large=task();accept(large);jdbc.update("UPDATE ai_ops_chat_message SET content=REPEAT('x',16000001) WHERE turn_id=? AND role='assistant'",large.run());
        var other=task();accept(other);var results=worker().enqueueUnobservedCompletedRuns(100);
        assertTrue(results.stream().anyMatch(r->!r.queued()));assertTrue(results.stream().anyMatch(r->r.queued() && r.job().runId().equals(other.run())));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_skill_evolution_source WHERE project_id=?",Integer.class,project));
        worker().runBatch(1,3);assertEquals(1,decisions.get());
    }

    @Test void relatedWholeSourcesRemainReadableWithoutClaimingTheirCompletedJobsButRevalidateAllReceipts() {
        var first=task();var last=turn(first.session(),3,first.episode(),"SYNTHETIC corrected version query");
        accept(last);var job=enqueue(last);worker().runBatch(1,3);
        var before=jobs.findJob(job.jobId()).orElseThrow();
        var input=sources.loadAccepted(project,last.run(),job.sourceId());
        assertEquals(4,input.messages().size());assertTrue(input.episodeJson().contains(first.run()));
        assertEquals(before,jobs.findJob(job.jobId()).orElseThrow(),"Reading a related source cannot claim or alter its job");
        String raw=jdbc.queryForObject("SELECT full_output FROM ai_ops_tool_result WHERE result_id=?",String.class,first.result()).replace("fixture-1","fixture-2");
        jdbc.update("UPDATE ai_ops_tool_result SET full_output=?,output_hash=? WHERE result_id=?",raw,hash(raw),first.result());
        assertThrows(IllegalStateException.class,()->sources.loadAccepted(project,last.run(),job.sourceId()));
        assertThrows(IllegalStateException.class,()->sources.loadAccepted("other-project",last.run(),job.sourceId()));
    }

    @Test void unavailableModelWaitsWithoutExhaustionThenSameJobResumesOnceConfigured() {
        var f=task();accept(f);var job=enqueue(f);var ready=new java.util.concurrent.atomic.AtomicBoolean(false);
        var worker=new SkillEvolutionJobApplicationService(jobs,new SkillEvolutionJobPolicy(),new SkillEvolutionInputPolicy(),sources,
                request->{if(!ready.get())throw new IllegalStateException("SKILL_EVOLUTION_MODEL_UNAVAILABLE");
                    return new SkillEvolutionPipelineDecision("SKIPPED","SKIP_SYNTHETIC_PROTOCOL_ONLY","","","{}","SKIPPED");},
                new SkillEvolutionPatchJsonEncoder(),()->"patch-"+UUID.randomUUID(),null,Clock.systemUTC());
        for(int i=0;i<4;i++) {
            assertEquals(1,worker.runBatch(1,3).size(),"The deliberately due fixture must be claimed");
            var waiting=jobs.findJob(job.jobId()).orElseThrow();
            assertEquals(SkillEvolutionJobStatus.PENDING,waiting.status());assertEquals(0,waiting.attempts());
            assertTrue(waiting.nextRunAt().isAfter(Instant.now().plusSeconds(45)),()->"Next retry: "+waiting.nextRunAt());assertEquals(0,waiting.leaseUntilMillis());
            assertTrue(jobs.findPatches(job.jobId(),"",20).isEmpty());
            assertTrue(worker.runBatch(1,3).isEmpty(),"Waiting for configuration cannot immediately reclaim the job");
            // Advance only this disposable fixture's scheduled time, not business acceptance state.
            // TIMESTAMP(0) can round fractional milliseconds into the next second; use an unambiguous past instant.
            jdbc.update("UPDATE ai_ops_skill_evolution_job SET next_run_at=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP) WHERE job_id=?",job.jobId());
        }
        ready.set(true);worker.runBatch(1,3);var done=jobs.findJob(job.jobId()).orElseThrow();
        assertEquals(SkillEvolutionJobStatus.SKIPPED,done.status());assertEquals(1,done.attempts());
        assertEquals(job.sourceId(),done.sourceId());assertEquals(1,jobs.findPatches(job.jobId(),"",20).size());
    }
    SkillExperienceApplicationService experienceService() {
        return new SkillExperienceApplicationService(new JdbcSkillExperienceAdapter(jdbc),null,
                new SkillTransactionPort(){public<T>T required(java.util.function.Supplier<T> body){return new org.springframework.transaction.support.TransactionTemplate(tx).execute(s->body.get());}},
                new SkillExperienceObservationPolicy(),acceptance);
    }
    @Test void backgroundContentReviewDefersPastAttemptLimitWithExponentialBackoffThenRecovers() {
        var f=task();accept(f);var initial=enqueue(f);var ready=new java.util.concurrent.atomic.AtomicBoolean();
        var worker=new SkillEvolutionJobApplicationService(jobs,new SkillEvolutionJobPolicy(),new SkillEvolutionInputPolicy(),sources,
                request->{if(!ready.get())throw new SkillContentReviewUnavailableException(new RuntimeException("SYNTHETIC_NETWORK_TIMEOUT"));
                    return new SkillEvolutionPipelineDecision("SKIPPED","SKIP_SYNTHETIC_PROTOCOL_ONLY","","","{}","SKIPPED");},
                new SkillEvolutionPatchJsonEncoder(),()->"patch-"+UUID.randomUUID(),null,Clock.systemUTC());
        for(int i=0;i<7;i++) {
            assertEquals(1,worker.runBatch(1,3).size());
            var waiting=jobs.findJob(initial.jobId()).orElseThrow();
            assertEquals(SkillEvolutionJobStatus.PENDING,waiting.status());assertEquals(i+1,waiting.attempts());
            long delay=Math.min(3600,60L<<Math.min(6,i));
            assertTrue(waiting.nextRunAt().isAfter(Instant.now().plusSeconds(delay-5)));
            assertTrue(worker.runBatch(1,3).isEmpty());
            jdbc.update("UPDATE ai_ops_skill_evolution_job SET next_run_at=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP) WHERE job_id=?",initial.jobId());
        }
        ready.set(true);assertEquals(1,worker.runBatch(1,3).size());
        assertEquals(SkillEvolutionJobStatus.SKIPPED,jobs.findJob(initial.jobId()).orElseThrow().status());
        assertEquals(1,jobs.findPatches(initial.jobId(),"",20).size());
    }
    String observe(Fixture f) {
        return experienceService().recordObservation(new SkillExperienceInput(project,"fixture-agent",f.run(),f.session(),
                "SUCCESSFUL_DIAGNOSTIC_PATTERN","VERIFY_VERSION","核验隔离目标版本",List.of("read target"),
                List.of(new SkillExperienceEvidenceReference("",f.result(),f.hash(),"MCP")),true,"SYNTHETIC fixture, not a real model result",2)).observation().clusterKey();
    }
    SkillEvolutionProposalSnapshot prepareProposal(SkillEvolutionJobSnapshot claim,List<Fixture> fixtures) {
        return prepareProposal(claim,fixtures,List.of());
    }
    SkillEvolutionProposalSnapshot prepareProposal(SkillEvolutionJobSnapshot claim,List<Fixture> fixtures,List<Map<String,Object>> related) {
        return prepareProposal(claim,fixtures,related,false);
    }
    SkillEvolutionProposalSnapshot prepareProposal(SkillEvolutionJobSnapshot claim,List<Fixture> fixtures,List<Map<String,Object>> related,boolean onlyRequestedTasks) {
        var plan=freezeProposal(claim,fixtures,related,onlyRequestedTasks,20);
        return proxy(new JdbcSkillEvolutionProposalAdapter(jdbc)).authored(claim,plan,Map.of("patchType",related.isEmpty()?"CREATE":"UPDATE_DIAGNOSTIC_RECIPE",
                "targetSkillId",related.isEmpty()?"":related.get(0).get("skillId"),"riskLevel","LOW","changes",List.of(),"artifacts",List.of(),"authoringSource","SYNTHETIC_TEST"));
    }
    SkillEvolutionProposalSnapshot freezeProposal(SkillEvolutionJobSnapshot claim,List<Fixture> fixtures,List<Map<String,Object>> related,boolean onlyRequestedTasks,int limit) {
        String cluster="";for(var f:fixtures) cluster=observe(f);
        var rows=new JdbcSkillExperienceAdapter(jdbc).consolidationSamples(project,"fixture-agent",cluster,limit,claim.runId());
        var full=rows.stream().filter(f->!onlyRequestedTasks || fixtures.stream().anyMatch(t->t.episode().equals(f.taskEpisodeId()))).map(f->{var m=new LinkedHashMap<String,Object>();
            m.put("observationId",f.observationId());m.put("runId",f.runId());m.put("sessionId",f.sessionId());m.put("outcome",f.outcome());
            m.put("sourceId",f.sourceId());m.put("sourceHash",f.sourceHash());m.put("taskEpisodeId",f.taskEpisodeId());m.put("conditionKey",f.conditionKey());
            m.put("acceptedTaskEpisode",f.episodeJson());return m;}).toList();
        var store=proxy(new JdbcSkillEvolutionProposalAdapter(jdbc));
        var plan=store.freeze(claim,sources.load(claim).sourceHash(),cluster,Map.of("consolidatedExperiences",full,"fixtureProvenance","SYNTHETIC_TEST","relatedSkills",related));
        return plan;
    }
    Map<String,Object> proposalRequest(Fixture f,SkillEvolutionProposalSnapshot plan) {
        var request=new LinkedHashMap<>(candidateRequest(f));request.put("authoringPlanId",plan.planId());request.put("authoringPlanHash",plan.planHash());request.put("patchType",plan.authored().get("patchType"));
        String target=text(plan.authored().get("targetSkillId"));request.put("targetSkillId",target);
        if(!target.isBlank()) {request.put("baseSkillVersion",1);request.put("baseSkillHash","a".repeat(64));}return request;
    }
    record ProposalFixture(List<Fixture> tasks,List<SkillEvolutionJobSnapshot> claims,List<SkillEvolutionProposalSnapshot> plans) { }
    ProposalFixture proposals() {
        var tasks=List.of(task(),task("other-service"),task());for(var task:tasks){accept(task);enqueue(task);}
        var claims=List.of(jobs.claimPending(3).orElseThrow(),jobs.claimPending(3).orElseThrow(),jobs.claimPending(3).orElseThrow());
        return new ProposalFixture(tasks,claims,claims.stream().map(c->prepareProposal(c,tasks)).toList());
    }
    void settle(ProposalFixture f) {
        for(var claim:f.claims()) jobs.rescheduleOrFail(claim,new SkillEvolutionRetryTransition(SkillEvolutionJobStatus.SKIPPED,claim.attempts()+1,null),"SYNTHETIC_TEST_FINISHED");
    }

    Map<String,Object> relatedSkill() {
        String id="skill-"+UUID.randomUUID(),body="SYNTHETIC existing method with input and permission boundaries";
        var pkg=SkillPackageManifest.markdown("PROJECT",project,id,"fixture method","fixture description",1,body);
        var entry=new SkillCatalogEntry(0,id,project,"fixture method","PROJECT","","fixture description",body,1,"ENABLED","fixture",null,null,
                "MANUAL","AUTO",true,true,null,"","",null,"a".repeat(64),1,"a".repeat(64),1,pkg.packageHash(),pkg.manifestJson(),cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringify(pkg.artifactHashes()));
        var repo=new JdbcSkillCatalogRepository(jdbc);assertTrue(repo.insertIfAbsent(entry));
        entry=repo.find("PROJECT",project,id,true).orElseThrow();
        var view=new LinkedHashMap<>(new SkillCatalogViewMapper().toView(entry,true));
        view.put("catalogFence",SkillEvolutionRelatedSkillPolicy.databaseFence(entry));view.put("relatedArtifacts",List.of());
        return view;
    }
    @Test void relatedMetadataOmitsBodyAndFrozenTargetSurvivesRetryButRejectsVersionDrift() {
        var tasks=List.of(task(),task("second-condition"),task());for(var t:tasks) accept(t);
        enqueue(tasks.get(0));var claim=jobs.claimPending(3).orElseThrow();
        for(var other:tasks.subList(1,tasks.size())) enqueue(other);worker().runBatch(2,3);
        try {
            var related=relatedSkill();String id=text(related.get("skillId"));var repo=new JdbcSkillCatalogRepository(jdbc);
            assertEquals("",repo.findEvolutionMetadata("PROJECT",project).get(0).content());
            assertFalse(repo.find("PROJECT",project,id,true).orElseThrow().content().isBlank());
            var plan=prepareProposal(claim,tasks,List.of(related));var store=proxy(new JdbcSkillEvolutionProposalAdapter(jdbc));
            assertEquals(plan,store.existing(claim,sources.load(claim).sourceHash()).orElseThrow());
            var request=new LinkedHashMap<>(proposalRequest(tasks.get(0),plan));request.put("targetSkillId",id);
            request.put("baseSkillVersion",1);request.put("baseSkillHash","a".repeat(64));
            var service=new SkillPatchCandidateApplicationService(candidates);
            var wrong=new LinkedHashMap<>(request);wrong.put("targetSkillId","not-in-frozen-set");
            assertEquals("SKILL_EVOLUTION_AUTHORED_TARGET_CHANGED",assertThrows(IllegalStateException.class,()->service.createEvolution(wrong,claim,sources.load(claim).sourceHash())).getMessage());
            String candidateId=text(service.createEvolution(request,claim,sources.load(claim).sourceHash()).get("candidate_id"));
            assertEquals(candidateId,service.createEvolution(request,claim,sources.load(claim).sourceHash()).get("candidate_id"));
            jdbc.update("UPDATE ai_ops_skill SET current_version=2,version_seq=version_seq+1 WHERE project_id=? AND skill_id=?",project,id);
            assertEquals("SKILL_EVOLUTION_RELATED_SKILL_CHANGED",assertThrows(IllegalStateException.class,()->store.existing(claim,sources.load(claim).sourceHash())).getMessage());
            assertFalse(store.supersedeChangedBaseline(claim,sources.load(claim).sourceHash()));
        } finally {jobs.rescheduleOrFail(claim,new SkillEvolutionRetryTransition(SkillEvolutionJobStatus.SKIPPED,claim.attempts()+1,null),"SYNTHETIC_TEST_FINISHED");}
    }
    @Test void changedUnpublishedBaselineCanBeArchivedAndRereadWithoutChangingAcceptedEvidence() {
        var tasks=List.of(task(),task("second-condition"),task());for(var t:tasks) accept(t);
        enqueue(tasks.get(0));var claim=jobs.claimPending(3).orElseThrow();
        for(var other:tasks.subList(1,tasks.size())) enqueue(other);worker().runBatch(2,3);
        try {
            var related=relatedSkill();String id=text(related.get("skillId"));
            var old=prepareProposal(claim,tasks,List.of(related));var store=proxy(new JdbcSkillEvolutionProposalAdapter(jdbc));
            String sourceHash=sources.load(claim).sourceHash();
            assertFalse(store.supersedeChangedBaseline(claim,sourceHash));
            jdbc.update("UPDATE ai_ops_skill SET current_version=2,version_seq=version_seq+1 WHERE project_id=? AND skill_id=?",project,id);
            assertTrue(store.supersedeChangedBaseline(claim,sourceHash));
            assertTrue(store.existing(claim,sourceHash).isEmpty());
            var archive=jdbc.queryForMap("SELECT * FROM ai_ops_skill_evolution_proposal_history WHERE plan_id=?",old.planId());
            assertEquals(old.planHash(),archive.get("plan_hash"));
            assertEquals(old.inputJson(),archive.get("input_json"));
            assertEquals(old.authoredJson(),text(archive.get("authored_json")));
            assertEquals(sourceHash,sources.load(claim).sourceHash());
            assertThrows(IllegalStateException.class,()->store.authored(claim,old,Map.of("late",true)));
            var entry=new JdbcSkillCatalogRepository(jdbc).find("PROJECT",project,id,true).orElseThrow();
            var refreshed=new LinkedHashMap<>(new SkillCatalogViewMapper().toView(entry,true));
            refreshed.put("catalogFence",SkillEvolutionRelatedSkillPolicy.databaseFence(entry));refreshed.put("relatedArtifacts",List.of());
            var next=prepareProposal(claim,tasks,List.of(refreshed));
            assertNotEquals(old.planId(),next.planId());assertNotEquals(old.planHash(),next.planHash());
            assertEquals(next,store.existing(claim,sourceHash).orElseThrow());
        } finally {jobs.rescheduleOrFail(claim,new SkillEvolutionRetryTransition(SkillEvolutionJobStatus.SKIPPED,claim.attempts()+1,null),"SYNTHETIC_TEST_FINISHED");}
    }

    @Test void relatedBodyAndGovernanceDriftCannotBorrowTheOldProposal() {
        var tasks=List.of(task(),task("second-condition"),task());for(var t:tasks) accept(t);
        enqueue(tasks.get(0));var claim=jobs.claimPending(3).orElseThrow();
        for(var other:tasks.subList(1,tasks.size())) enqueue(other);worker().runBatch(2,3);
        try {
            var related=relatedSkill();String id=text(related.get("skillId"));var plan=prepareProposal(claim,tasks,List.of(related));
            var store=proxy(new JdbcSkillEvolutionProposalAdapter(jdbc));
            jdbc.update("UPDATE ai_ops_skill SET content=CONCAT(content,' corrupt') WHERE project_id=? AND skill_id=?",project,id);
            assertEquals("SKILL_EVOLUTION_RELATED_SKILL_CHANGED",assertThrows(IllegalStateException.class,()->store.existing(claim,sources.load(claim).sourceHash())).getMessage());
            jdbc.update("UPDATE ai_ops_skill SET content=?,auto_update_enabled=0 WHERE project_id=? AND skill_id=?",related.get("content"),project,id);
            assertEquals("SKILL_EVOLUTION_RELATED_SKILL_CHANGED",assertThrows(IllegalStateException.class,()->new SkillPatchCandidateApplicationService(candidates).createEvolution(proposalRequest(tasks.get(0),plan),claim,sources.load(claim).sourceHash())).getMessage());
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_skill_patch_candidate WHERE project_id=?",Integer.class,project));
        } finally {jobs.rescheduleOrFail(claim,new SkillEvolutionRetryTransition(SkillEvolutionJobStatus.SKIPPED,claim.attempts()+1,null),"SYNTHETIC_TEST_FINISHED");}
    }

    @Test void relatedResourceBytesAndConcurrentCatalogUpdatesAreFenced() throws Exception {
        var tasks=List.of(task(),task("second-condition"),task());for(var t:tasks) accept(t);
        enqueue(tasks.get(0));var claim=jobs.claimPending(3).orElseThrow();
        for(var other:tasks.subList(1,tasks.size())) enqueue(other);worker().runBatch(2,3);var executor=Executors.newSingleThreadExecutor();
        try {
            var related=relatedSkill();String id=text(related.get("skillId"));String resource="SYNTHETIC resource rules";
            String png="iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aD1sAAAAASUVORK5CYII=";
            var pkg=SkillPackageManifest.packageOf("PROJECT",project,id,"fixture method","fixture description",1,text(related.get("content")),
                    List.of(Map.of("path","resources/check.txt","role","RESOURCE","content",resource),
                            Map.of("path","assets/fixture.png","role","ASSET","encoding","BASE64","mediaType","image/png","contentBase64",png)),
                    List.of(),List.of(),SkillPackageManifest.Limits.defaults());
            jdbc.update("UPDATE ai_ops_skill SET current_package_hash=?,package_manifest_json=?,artifact_hashes_json=? WHERE project_id=? AND skill_id=?",
                    pkg.packageHash(),pkg.manifestJson(),cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringify(pkg.artifactHashes()),project,id);
            for(var artifact:pkg.artifacts().values()) jdbc.update("INSERT INTO ai_ops_skill_artifact(artifact_id,scope,project_id,skill_id,version,package_hash,artifact_path,artifact_role,media_type,content_encoding,content_hash,size_bytes,content) VALUES (?,'PROJECT',?,?,1,?,?,?,?,?,?,?,?)",
                    "artifact-"+UUID.randomUUID(),project,id,pkg.packageHash(),artifact.path(),artifact.role(),artifact.mediaType(),artifact.encoding(),artifact.contentHash(),artifact.sizeBytes(),artifact.content());
            var entry=new JdbcSkillCatalogRepository(jdbc).find("PROJECT",project,id,true).orElseThrow();
            related=new LinkedHashMap<>(new SkillCatalogViewMapper().toView(entry,true));
            related.put("catalogFence",SkillEvolutionRelatedSkillPolicy.databaseFence(entry));
            related.put("relatedArtifacts",pkg.artifacts().values().stream().filter(a->!"SKILL.md".equals(a.path()))
                    .map(a->Map.of("path",a.path(),"content",a.content(),"contentHash",a.contentHash(),"encoding",a.encoding())).toList());
            var plan=prepareProposal(claim,tasks,List.of(related));var store=proxy(new JdbcSkillEvolutionProposalAdapter(jdbc));
            jdbc.update("UPDATE ai_ops_skill_artifact SET content='tampered' WHERE project_id=? AND skill_id=? AND artifact_path='resources/check.txt'",project,id);
            assertEquals("SKILL_EVOLUTION_RELATED_SKILL_CHANGED",assertThrows(IllegalStateException.class,()->store.existing(claim,sources.load(claim).sourceHash())).getMessage());
            jdbc.update("UPDATE ai_ops_skill_artifact SET content=? WHERE project_id=? AND skill_id=? AND artifact_path='resources/check.txt'",resource,project,id);
            var pending=new java.util.concurrent.atomic.AtomicReference<Future<Integer>>();
            new org.springframework.transaction.support.TransactionTemplate(tx).execute(status->{
                assertEquals(plan,store.existing(claim,sources.load(claim).sourceHash()).orElseThrow());
                var started=new CountDownLatch(1);
                pending.set(executor.submit(()->{started.countDown();return jdbc.update("UPDATE ai_ops_skill SET version_seq=version_seq+1 WHERE project_id=? AND skill_id=?",project,id);}));
                try {assertTrue(started.await(2,TimeUnit.SECONDS));assertThrows(TimeoutException.class,()->pending.get().get(200,TimeUnit.MILLISECONDS));}
                catch(InterruptedException e) {Thread.currentThread().interrupt();throw new RuntimeException(e);}return null;
            });
            assertEquals(1,pending.get().get(3,TimeUnit.SECONDS));
            assertEquals("SKILL_EVOLUTION_RELATED_SKILL_CHANGED",assertThrows(IllegalStateException.class,()->store.existing(claim,sources.load(claim).sourceHash())).getMessage());
        } finally {executor.shutdownNow();jobs.rescheduleOrFail(claim,new SkillEvolutionRetryTransition(SkillEvolutionJobStatus.SKIPPED,claim.attempts()+1,null),"SYNTHETIC_TEST_FINISHED");}
    }

    @Test void frozenAuthorInputAndResultSurviveAdapterRecreationAndIgnoreNewMutableInput() {
        var f=proposals();try {
            var c=f.claims().get(0);var plan=f.plans().get(0);var store=proxy(new JdbcSkillEvolutionProposalAdapter(jdbc));
            var same=store.freeze(c,sources.load(c).sourceHash(),"changed-cluster",Map.of("fakeNewInput",true));
            assertEquals(plan,same);assertFalse(same.input().containsKey("fakeNewInput"));
            assertEquals(plan,store.authored(c,plan,Map.of("patchType","NO_CHANGE","modifiedModelResult",true)));
            assertEquals(hash(plan.inputJson()),plan.planHash());
            jdbc.update("UPDATE ai_ops_skill_evolution_proposal SET input_json='{}' WHERE plan_id=?",plan.planId());
            assertThrows(IllegalStateException.class,()->store.freeze(c,sources.load(c).sourceHash(),"",Map.of()));
        }finally{settle(f);}
    }

    @Test void changedRelatedReceiptAfterAuthoringPreventsTheCandidateCommit() {
        var f=proposals();try {
            var other=f.tasks().get(1);String raw=jdbc.queryForObject("SELECT full_output FROM ai_ops_tool_result WHERE result_id=?",String.class,other.result()).replace("fixture-1","fixture-2");
            jdbc.update("UPDATE ai_ops_tool_result SET full_output=?,output_hash=? WHERE result_id=?",raw,hash(raw),other.result());
            assertThrows(IllegalStateException.class,()->new SkillPatchCandidateApplicationService(candidates).createEvolution(
                    proposalRequest(f.tasks().get(0),f.plans().get(0)),f.claims().get(0),sources.load(f.claims().get(0)).sourceHash()));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_skill_patch_candidate WHERE project_id=?",Integer.class,project));
        }finally{settle(f);}
    }

    @Test void lateIncidentGroupingInvalidatesTheFrozenIndependentSourceSet() {
        var f=proposals();try {
            String incident="i-"+UUID.randomUUID();jdbc.update("INSERT INTO ai_ops_incident VALUES (?,?)",incident,project);
            for(var task:f.tasks())jdbc.update("INSERT INTO ai_ops_incident_run VALUES (?,?)",incident,task.run());
            assertThrows(IllegalStateException.class,()->new SkillPatchCandidateApplicationService(candidates).createEvolution(
                    proposalRequest(f.tasks().get(0),f.plans().get(0)),f.claims().get(0),sources.load(f.claims().get(0)).sourceHash()));
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_skill_patch_candidate WHERE project_id=?",Integer.class,project));
        }finally{settle(f);}
    }

    @Test void eightConcurrentIndependentProposalsRetainOneAssetProposalAndPendingEvidence() throws Exception {
        var f=proposals();try {
            var next=new AtomicInteger();var service=new SkillPatchCandidateApplicationService(candidates);
            var results=concurrent(()->{int i=next.getAndIncrement()%3;
                try{return service.createEvolution(proposalRequest(f.tasks().get(i),f.plans().get(i)),f.claims().get(i),sources.load(f.claims().get(i)).sourceHash()).get("candidate_id");}
                catch(IllegalStateException e){assertEquals("SKILL_EVOLUTION_PROPOSAL_PENDING",e.getMessage());return "WAIT";}});
            assertEquals(1,results.stream().filter(x->!"WAIT".equals(x)).distinct().count());assertTrue(results.contains("WAIT"));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_skill_patch_candidate WHERE project_id=?",Integer.class,project));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_skill_pending_asset_proposal WHERE project_id=?",Integer.class,project));
            assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_skill_evolution_proposal WHERE project_id=? AND authored_json IS NOT NULL",Integer.class,project));
        }finally{settle(f);}
    }

    @Test void publicationRechecksSourcesAfterAuthoringJobLeaseHasEnded() {
        var f=proposals();try {
            var service=new SkillPatchCandidateApplicationService(candidates);
            String id=text(service.createEvolution(proposalRequest(f.tasks().get(0),f.plans().get(0)),f.claims().get(0),
                    sources.load(f.claims().get(0)).sourceHash()).get("candidate_id"));
            jobs.complete(f.claims().get(0),patch(f.claims().get(0)),SkillEvolutionJobStatus.SKIPPED);
            var publication=new JdbcSkillEvolutionProposalAdapter(jdbc);
            var template=new org.springframework.transaction.support.TransactionTemplate(tx);
            assertEquals(f.plans().get(0).input(),template.execute(s -> publication.publicationInput(service.getCandidate(id))));
            // A later user correction invalidates the same publication, even though its candidate was already authored.
            var original=f.tasks().get(1);turn(original.session(),3,original.episode(),"复核原验收结论");
            assertThrows(IllegalStateException.class,() -> template.execute(s -> publication.publicationInput(service.getCandidate(id))));
        } finally {settle(f);}
    }

    @Test void automaticPublicationRollsBackPartialWriteAndEightWorkersPublishOneImmutableVersion() throws Exception {
        Path root=Path.of("").toAbsolutePath();while(root!=null&&!Files.exists(root.resolve("db/migrations/sql")))root=root.getParent();
        create(root.resolve("db/migrations/sql/ops-intent-memory-skill-channel.sql"),"ai_ops_skill_release");
        var candidateService=new SkillPatchCandidateApplicationService(candidates);
        var routing=Map.of("category","DATA","subcategory","QUERY_DIAGNOSIS","whenToUse",List.of("定位慢 SQL 和查询延迟"),
                "whenNotToUse",List.of("生成演示文稿或开发前端页面"),"keywords",List.of("slow sql","explain"));
        var candidate=candidateService.createCandidate(Map.of("projectId",project,"patchType","CREATE_SKILL",
                "changes",List.of(Map.of("section","routingProfile","operation","upsert","key","runtime","value",routing)),
                "evidenceRefs",List.of(Map.of("id","SYNTHETIC_PUBLICATION_CONTROL_TEST"))));
        var catalog=new JdbcSkillCatalogRepository(jdbc);
        var packages=new JdbcSkillPackageRepository(beans.getBeanProvider(JdbcTemplate.class));
        var transactions=new SkillTransactionPort(){public<T>T required(java.util.function.Supplier<T> work){return new org.springframework.transaction.support.TransactionTemplate(tx).execute(s->work.get());}};
        var mutations=new SkillCatalogMutationUseCase(catalog,packages,transactions,p->{assertEquals(project,p);},SkillPackageManifest.Limits.defaults());
        var query=org.mockito.Mockito.mock(SkillCatalogQueryService.class);
        var management=new SkillManagementUseCase(org.mockito.Mockito.mock(SkillCatalogPort.class),mutations,
                org.mockito.Mockito.mock(SkillRollbackUseCase.class),new SkillEvolutionPublishUseCase(catalog,packages,transactions,SkillPackageManifest.Limits.defaults()),
                org.mockito.Mockito.mock(SkillPackageQueryService.class));
        var release=new JdbcSkillReleaseAdapter(jdbc);
        // This case isolates publication transactions; accepted-source integrity has separate real MySQL cases above.
        var checks=org.mockito.Mockito.mock(SkillAutomaticPublicationCheckPort.class);
        org.mockito.Mockito.when(checks.requireCurrentSources(org.mockito.ArgumentMatchers.any())).thenReturn(new SkillPublicationSources(Map.of()));
        var validator=new SkillPatchValidationApplicationService(candidateService,org.mockito.Mockito.mock(SkillPatchRegressionEvaluationPort.class),
                org.mockito.Mockito.mock(SkillPatchValidationResultPort.class),new SkillPatchValidationPolicy());
        var audit=org.mockito.Mockito.mock(SkillReleaseAuditPort.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("SYNTHETIC_FAILURE_AFTER_PACKAGE_WRITE")).when(audit)
                .recordStateChanged(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyMap());
        var service=new SkillAutomaticPublicationService(release,candidateService,validator,checks,(c,input)->List.of(),
                new SkillReleasePackageAssembler(query),management,transactions,audit,new SkillReleaseSettings(true,20,20));
        assertThrows(IllegalStateException.class,()->service.start(candidate.candidateId()));
        assertEquals(SkillReleaseStatus.READY,release.findByCandidate(candidate.candidateId()).orElseThrow().status());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_skill_version WHERE project_id=?",Integer.class,project));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_skill WHERE project_id=?",Integer.class,project));
        org.mockito.Mockito.reset(audit);
        concurrent(()->service.start(candidate.candidateId()));
        assertEquals(SkillReleaseStatus.PENDING_INDEX,release.findByCandidate(candidate.candidateId()).orElseThrow().status());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_skill_version WHERE project_id=?",Integer.class,project));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_skill WHERE project_id=?",Integer.class,project));
        var pending=release.findByCandidate(candidate.candidateId()).orElseThrow();
        release.markReconciliationRequired(pending.releaseId(),"SYNTHETIC_INDEX_UNAVAILABLE");
        assertFalse(release.listEvaluable(100).stream().anyMatch(r->r.releaseId().equals(pending.releaseId())));
        var retry=release.findByCandidate(candidate.candidateId()).orElseThrow();
        assertEquals(1,((Number)retry.metadata().get("retryAttempts")).intValue());
        assertEquals(SkillReleaseStatus.PENDING_INDEX,retry.status());
    }

    @Test void diagnosedStaleReadyReleaseIsRecheckedWithoutBypassingTransportOrIndexBackoff() {
        var releases=new JdbcSkillReleaseAdapter(jdbc);
        var ids=new java.util.ArrayList<String>();
        String[] statuses={"READY","READY","PENDING_INDEX","READY"};
        String[] reasons={"RECONCILIATION_REQUIRED:SKILL_ATOMIC_SOURCE_NOT_ACTIVE",
                "RECONCILIATION_REQUIRED:DATABASE_UNAVAILABLE",
                "RECONCILIATION_REQUIRED:SKILL_ATOMIC_SOURCE_NOT_ACTIVE",
                "RECONCILIATION_REQUIRED:SKILL_ATOMIC_SOURCE_NOT_ACTIVE"};
        for(int i=0;i<statuses.length;i++) {
            String id="retained-"+UUID.randomUUID();ids.add(id);
            jdbc.update("""
                INSERT INTO ai_ops_skill_release(release_id,candidate_id,project_id,target_skill_id,status,reason_code,metadata_json)
                VALUES (?,?,?,'',?,?,?)
                """,id,id,project,statuses[i],reasons[i],cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringify(Map.of(
                    "publicationPolicy",i==3?"legacy-canary":"source-qualified-auto-v1",
                    "retryAfterMs",System.currentTimeMillis()+3_600_000)));
        }
        var eligible=releases.listEvaluable(100).stream().map(SkillReleaseSnapshot::releaseId).filter(ids::contains).toList();
        assertEquals(List.of(ids.get(0)),eligible);
        assertEquals("READY",jdbc.queryForObject("SELECT status FROM ai_ops_skill_release WHERE release_id=?",String.class,ids.get(0)));
    }

    @Test void publicationRejectsChangedAuthoredBodyAndForgedPlan() {
        var f=proposals();try {
            var service=new SkillPatchCandidateApplicationService(candidates);
            String id=text(service.createEvolution(proposalRequest(f.tasks().get(0),f.plans().get(0)),f.claims().get(0),
                    sources.load(f.claims().get(0)).sourceHash()).get("candidate_id"));
            var template=new org.springframework.transaction.support.TransactionTemplate(tx);
            var publication=new JdbcSkillEvolutionProposalAdapter(jdbc);
            jdbc.update("UPDATE ai_ops_skill_patch_candidate SET changes_json='[{\"key\":\"tampered\"}]' WHERE candidate_id=?",id);
            assertEquals("SKILL_EVOLUTION_AUTHORED_CONTENT_CHANGED",assertThrows(IllegalStateException.class,
                    () -> template.execute(s -> publication.publicationInput(service.getCandidate(id)))).getMessage());
            jdbc.update("UPDATE ai_ops_skill_patch_candidate SET changes_json='[]' WHERE candidate_id=?",id);
            jdbc.update("UPDATE ai_ops_skill_evolution_proposal SET input_json='{}' WHERE candidate_id=?",id);
            assertEquals("SKILL_EVOLUTION_PROPOSAL_HASH_MISMATCH",assertThrows(IllegalStateException.class,
                    () -> template.execute(s -> publication.publicationInput(service.getCandidate(id)))).getMessage());
        } finally {settle(f);}
    }

    @Test void proposalBudgetNeedsBothElapsedDayAndResolvedPriorCandidate() {
        var f=proposals();try {
            var service=new SkillPatchCandidateApplicationService(candidates);String sha=sources.load(f.claims().get(1)).sourceHash();
            String id=String.valueOf(service.createEvolution(proposalRequest(f.tasks().get(0),f.plans().get(0)),f.claims().get(0),sources.load(f.claims().get(0)).sourceHash()).get("candidate_id"));
            jdbc.update("UPDATE ai_ops_skill_pending_asset_proposal SET next_proposal_at_ms=1 WHERE project_id=?",project);
            assertThrows(IllegalStateException.class,()->service.createEvolution(proposalRequest(f.tasks().get(1),f.plans().get(1)),f.claims().get(1),sha));
            candidates.updateStatus(id,SkillPatchCandidateStatus.REJECTED,"SYNTHETIC review rejection");
            // Advance only this disposable fixture's budget clock; business state still uses repository transitions.
            jdbc.update("UPDATE ai_ops_skill_pending_asset_proposal SET next_proposal_at_ms=UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3))*1000+86400000 WHERE project_id=?",project);
            assertThrows(IllegalStateException.class,()->service.createEvolution(proposalRequest(f.tasks().get(1),f.plans().get(1)),f.claims().get(1),sha));
            jdbc.update("UPDATE ai_ops_skill_pending_asset_proposal SET next_proposal_at_ms=1 WHERE project_id=?",project);
            assertNotEquals(id,service.createEvolution(proposalRequest(f.tasks().get(1),f.plans().get(1)),f.claims().get(1),sha).get("candidate_id"));
        }finally{settle(f);}
    }

    @Test void boundedSourceSelectionIncludesOlderConditionAndRequiredCurrentTask() {
        var older=task("rare-condition");accept(older);enqueue(older);worker().runBatch(1,3);String cluster=observe(older);
        Fixture current=null;
        for(int i=0;i<5;i++){current=task();accept(current);enqueue(current);worker().runBatch(1,3);observe(current);}
        var selected=new JdbcSkillExperienceAdapter(jdbc).consolidationSamples(project,"fixture-agent",cluster,3,current.run());
        assertEquals(3,selected.size());String required=current.run();
        assertTrue(selected.stream().anyMatch(s->required.equals(s.runId())));assertTrue(selected.stream().anyMatch(s->older.run().equals(s.runId())));
        assertEquals(2,selected.stream().map(SkillExperienceConsolidationSample::conditionKey).distinct().count());
    }

    @Test void changedCandidateBodyCannotBorrowTheOriginalProposalSourceProof() {
        var f=proposals();try {
            var request=new LinkedHashMap<>(proposalRequest(f.tasks().get(0),f.plans().get(0)));
            request.put("changes",List.of(Map.of("section","procedure","value","modified after author returned")));
            var error=assertThrows(IllegalStateException.class,()->new SkillPatchCandidateApplicationService(candidates).createEvolution(request,f.claims().get(0),sources.load(f.claims().get(0)).sourceHash()));
            assertEquals("SKILL_EVOLUTION_AUTHORED_CONTENT_CHANGED",error.getMessage());
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_skill_pending_asset_proposal WHERE project_id=?",Integer.class,project));
        }finally{settle(f);}
    }

    record NovelFixture(List<Fixture> tasks, SkillEvolutionJobSnapshot claim, SkillEvolutionProposalSnapshot plan) { }
    NovelFixture novelFixture(Map<String,Object> related, List<Fixture> tasks) {
        for (var t:tasks) accept(t);
        enqueue(tasks.get(0));var claim=jobs.claimPending(3).orElseThrow();
        for (var t:tasks.subList(1,tasks.size())) enqueue(t);
        worker().runBatch(tasks.size()-1,3);
        return new NovelFixture(tasks,claim,prepareProposal(claim,tasks,List.of(related),true));
    }
    void settleNovel(NovelFixture f) {
        jobs.rescheduleOrFail(f.claim(),new SkillEvolutionRetryTransition(SkillEvolutionJobStatus.SKIPPED,f.claim().attempts()+1,null),"SYNTHETIC_TEST_FINISHED");
    }
    void publishedSourceFixture(Map<String,Object> related, NovelFixture old) {
        publishedSourceFixture(related,old,1);
    }
    void publishedSourceFixture(Map<String,Object> related, NovelFixture old,int version) {
        // Synthetic publication history only in disposable MySQL. This is NOT proof of release acceptance.
        jdbc.update("INSERT INTO ai_ops_skill_version(skill_id,project_id,scope,version,content,skill_hash,package_hash,source_type,evolution_job_id,change_summary) VALUES (?,?,'PROJECT',?,?,?,?,'SKILL_EVOLVER',?,'SYNTHETIC_PUBLISHED_SOURCE_FIXTURE')",
                related.get("skillId"),project,version,related.get("content"),related.get("currentSkillHash"),related.get("currentPackageHash"),old.claim().jobId());
    }
    @Test void publishedPortfolioAddsWholeHistoricTasksWithoutClaimingThemOrCountingThemAsNewPatchSources() {
        var related=relatedSkill();var old=novelFixture(related,List.of(task(),task("old-condition"),task()));
        publishedSourceFixture(related,old);settleNovel(old);
        var previous=jobs.findJob(old.claim().jobId()).orElseThrow();
        var fresh=novelFixture(related,List.of(task(),task("new-condition"),task()));
        try {
            var input=fresh.plan().input();
            var whole=(List<Map<String,Object>>)input.get("consolidatedExperiences");
            assertEquals(6,whole.size());
            var primary=JdbcSkillEvolutionSourcePortfolio.primaryIds(input);assertEquals(3,primary.size());
            var groups=(List<?>)input.get("relatedSkillSourceGroups");assertEquals(1,groups.size());
            assertEquals(3,((List<?>)((Map<?,?>)groups.get(0)).get("sourceIds")).size());
            assertEquals(previous,jobs.findJob(old.claim().jobId()).orElseThrow());
            assertEquals(fresh.plan(),proxy(new JdbcSkillEvolutionProposalAdapter(jdbc)).existing(fresh.claim(),sources.load(fresh.claim()).sourceHash()).orElseThrow());
            String id=text(createNovel(fresh).get("candidate_id"));
            // Two new tasks plus an old historic task do not satisfy three new PATCH sources.
            var forged=new LinkedHashMap<>(input);var claimed=new ArrayList<>(primary.stream().limit(2).toList());
            claimed.add(text(whole.stream().filter(s->!primary.contains(text(s.get("sourceId")))).findFirst().orElseThrow().get("sourceId")));
            forged.put("primarySourceIds",claimed);
            var plan=new SkillEvolutionProposalSnapshot("forged",hash(json(forged)),json(forged),fresh.plan().authoredJson());
            var candidate=new SkillPatchCandidateApplicationService(candidates).getCandidate(id);
            assertEquals("SKILL_EVOLUTION_INSUFFICIENT_NEW_SOURCES",assertThrows(IllegalStateException.class,
                    ()->new JdbcSkillNovelSourceGuard(jdbc).requireNovel(plan,candidate)).getMessage());
        } finally {settleNovel(fresh);}
    }
    @Test void portfolioNeverSilentlyTruncatesHistoricConditionsBeyondTwentyWholeSources() {
        var input=Map.<String,Object>of("relatedSkills",List.of(),"consolidatedExperiences",
                java.util.stream.IntStream.range(0,SkillSourceBatchPolicy.ARCHIVE_LIMIT+1).mapToObj(i->Map.<String,Object>of("sourceId","s"+i)).toList());
        assertEquals("SKILL_EVOLUTION_SOURCE_PORTFOLIO_BUDGET",assertThrows(IllegalStateException.class,
                ()->new JdbcSkillEvolutionSourcePortfolio(jdbc).enrich(project,input)).getMessage());
    }
    @Test void sourceBatchesPersistUnderTheSameProposalAndFenceMissingChangedAndLateReviews() {
        var tasks=java.util.stream.IntStream.range(0,21).mapToObj(i->task(i%2==0?"fixture-service":"alternate-condition")).toList();
        tasks.forEach(this::accept);enqueue(tasks.get(0));var claim=jobs.claimPending(3).orElseThrow();
        tasks.subList(1,tasks.size()).forEach(this::enqueue);worker().runBatch(20,3);
        var store=proxy(new JdbcSkillEvolutionProposalAdapter(jdbc));var policy=new SkillSourceBatchPolicy();
        var plan=freezeProposal(claim,tasks,List.of(),true,SkillSourceBatchPolicy.ARCHIVE_LIMIT);
        var pages=policy.pages(plan.input());assertEquals(2,pages.size());
        var reviews=new ArrayList<Map<String,Object>>();
        try {
            assertEquals(21,((List<?>)plan.input().get("consolidatedExperiences")).size());
            assertThrows(IllegalStateException.class,()->store.authored(claim,plan,Map.of("patchType","NO_CHANGE")));
            for(int index=0;index<pages.size();index++) {
                var page=pages.get(index);String digest=cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher.sha256(page);
                var rows=((List<Map<String,Object>>)page.get("consolidatedExperiences")).stream().map(s->Map.<String,Object>of("sourceId",s.get("sourceId"),"sourceHash",s.get("sourceHash"),
                        "conditions",List.of("SYNTHETIC isolated condition"),"effectiveSteps",List.of("read version"),"acceptance",List.of("compare receipt"),"conflicts",List.of(),"limitations",List.of("SYNTHETIC fixture"),"methodRelation","same synthetic method")).toList();
                var review=Map.<String,Object>of("evidenceSufficient",true,"sourceReviews",rows);
                assertTrue(store.batchReview(claim,plan,index,digest).isEmpty());
                assertEquals(review,store.saveBatchReview(claim,plan,index,digest,review));
                assertEquals(review,store.batchReview(claim,plan,index,digest).orElseThrow());reviews.add(review);
            }
            assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_skill_evolution_batch_review WHERE plan_id=?",Integer.class,plan.planId()));
            var authored=store.authored(claim,plan,Map.of("patchType","NO_CHANGE","sourceBatchReviewAudit",policy.audit(plan.input(),reviews)));
            assertEquals(authored,store.existing(claim,sources.load(claim).sourceHash()).orElseThrow());
            int first=0;String digest=cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher.sha256(pages.get(first));
            var changed=new LinkedHashMap<>(reviews.get(first));changed.put("extra","different completion");
            assertThrows(IllegalStateException.class,()->store.saveBatchReview(claim,plan,first,digest,changed));
            assertThrows(IllegalStateException.class,()->store.batchReview(claim,plan,first,"f".repeat(64)));
            jdbc.update("UPDATE ai_ops_skill_evolution_batch_review SET review_json=CONCAT(review_json,' ') WHERE plan_id=? AND batch_index=0",plan.planId());
            assertThrows(IllegalStateException.class,()->store.batchReview(claim,plan,first,digest));
            jobs.rescheduleOrFail(claim,new SkillEvolutionRetryTransition(SkillEvolutionJobStatus.SKIPPED,claim.attempts()+1,null),"SYNTHETIC_TEST_FINISHED");
            assertThrows(IllegalStateException.class,()->store.saveBatchReview(claim,plan,first,digest,reviews.get(first)));
        } finally {
            jobs.rescheduleOrFail(claim,new SkillEvolutionRetryTransition(SkillEvolutionJobStatus.SKIPPED,claim.attempts()+1,null),"SYNTHETIC_TEST_FINISHED");
        }
    }
    @Test void rollingBackCurrentPointerDoesNotMakeLaterPublishedPatchSourcesNewAgain() {
        var related=relatedSkill();var old=novelFixture(related,List.of(task(),task("old-condition"),task()));
        publishedSourceFixture(related,old);settleNovel(old);
        var fresh=novelFixture(related,List.of(task(),task("new-condition"),task()));
        try {
            // Disposable publication history: v2 exists, while the active pointer is v1 after a rollback.
            publishedSourceFixture(related,fresh,2);
            assertEquals(1,jdbc.queryForObject("SELECT current_version FROM ai_ops_skill WHERE project_id=? AND skill_id=?",Integer.class,project,related.get("skillId")));
            assertEquals("SKILL_EVOLUTION_INSUFFICIENT_NEW_SOURCES",assertThrows(IllegalStateException.class,()->createNovel(fresh)).getMessage());
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_skill_patch_candidate WHERE project_id=?",Integer.class,project));
        } finally {settleNovel(fresh);}
    }
    @Test void withdrawnHistoricTaskIsExcludedOnNewFreezeButInvalidatesAnAlreadyFrozenPortfolio() {
        var related=relatedSkill();var old=novelFixture(related,List.of(task(),task("old-condition"),task()));
        publishedSourceFixture(related,old);settleNovel(old);
        var fresh=novelFixture(related,List.of(task(),task("new-condition"),task()));
        try {
            var previous=old.tasks().get(0);
            turn(previous.session(),3,previous.episode(),"SYNTHETIC late correction of the historic outcome");
            assertThrows(IllegalStateException.class,()->proxy(new JdbcSkillEvolutionProposalAdapter(jdbc)).existing(fresh.claim(),sources.load(fresh.claim()).sourceHash()));
            var primary=new LinkedHashMap<>(fresh.plan().input());var ids=JdbcSkillEvolutionSourcePortfolio.primaryIds(primary);
            primary.put("consolidatedExperiences",((List<Map<String,Object>>)primary.get("consolidatedExperiences")).stream().filter(s->ids.contains(text(s.get("sourceId")))).toList());
            var refreshed=new JdbcSkillEvolutionSourcePortfolio(jdbc).enrich(project,primary);
            assertEquals(5,((List<?>)refreshed.get("consolidatedExperiences")).size());
            assertEquals(1,((List<?>)refreshed.get("excludedRelatedSourceIds")).size());
        } finally {settleNovel(fresh);}
    }
    Map<String,Object> createNovel(NovelFixture f) {
        return new SkillPatchCandidateApplicationService(candidates).createEvolution(proposalRequest(f.tasks().get(0),f.plan()),f.claim(),sources.load(f.claim()).sourceHash());
    }
    @Test void publishedSourceHistoryRequiresThreeAdditionalTasksAndRetryDoesNotConsumePendingSources() {
        var related=relatedSkill();var old=novelFixture(related,List.of(task(),task("old-condition"),task()));
        publishedSourceFixture(related,old);settleNovel(old);
        var fresh=novelFixture(related,List.of(task(),task("new-condition"),task()));
        try {
            var first=createNovel(fresh);assertEquals(first.get("candidate_id"),createNovel(fresh).get("candidate_id"));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_skill_version WHERE project_id=?",Integer.class,project));
        } finally {settleNovel(fresh);}
    }
    @Test void unpublishedProposalIsNotConsumedButSameTasksInPublishedVersionAre() {
        var related=relatedSkill();var current=novelFixture(related,List.of(task(),task("other-condition"),task()));
        try {
            var candidate=new SkillPatchCandidateApplicationService(candidates);
            // An authored proposal alone consumes nothing. Once an immutable published version references it, all three are old.
            var guard=new JdbcSkillNovelSourceGuard(jdbc);
            var request=proposalRequest(current.tasks().get(0),current.plan());
            var created=candidate.createEvolution(request,current.claim(),sources.load(current.claim()).sourceHash());
            publishedSourceFixture(related,current);
            assertEquals("SKILL_EVOLUTION_INSUFFICIENT_NEW_SOURCES",assertThrows(IllegalStateException.class,
                    ()->guard.requireNovel(current.plan(),candidate.getCandidate(text(created.get("candidate_id"))))).getMessage());
        } finally {settleNovel(current);}
    }
    @Test void lateEventLinkToPreviouslyPublishedSourceCannotCountAsAdditionalEvidence() {
        var related=relatedSkill();var old=novelFixture(related,List.of(task(),task("old-condition"),task()));
        publishedSourceFixture(related,old);settleNovel(old);
        var fresh=novelFixture(related,List.of(task(),task("new-condition"),task()));
        try {
            String incident="incident-"+UUID.randomUUID();jdbc.update("INSERT INTO ai_ops_incident VALUES (?,?)",incident,project);
            jdbc.update("INSERT INTO ai_ops_incident_run VALUES (?,?)",incident,old.tasks().get(0).run());
            jdbc.update("INSERT INTO ai_ops_incident_run VALUES (?,?)",incident,fresh.tasks().get(0).run());
            assertEquals("SKILL_EVOLUTION_SOURCE_SET_INVALID",assertThrows(IllegalStateException.class,()->createNovel(fresh)).getMessage(),
                    "A late incident link invalidates the entire cross-method frozen independent source set before PATCH admission");
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_skill_patch_candidate WHERE project_id=?",Integer.class,project));
        } finally {settleNovel(fresh);}
    }
    @Test void missingOrCorruptPublishedProvenanceFailsClosedInsteadOfTreatingHistoryAsEmpty() {
        var related=relatedSkill();var current=novelFixture(related,List.of(task(),task("other-condition"),task()));
        try {
            publishedSourceFixture(related,current);
            jdbc.update("UPDATE ai_ops_skill_version SET evolution_job_id='missing-history' WHERE project_id=?",project);
            assertEquals("SKILL_EVOLUTION_PUBLISHED_SOURCE_HISTORY_REQUIRED",assertThrows(IllegalStateException.class,()->createNovel(current)).getMessage());
            jdbc.update("UPDATE ai_ops_skill_version SET evolution_job_id=? WHERE project_id=?",current.claim().jobId(),project);
            jdbc.update("UPDATE ai_ops_skill_evolution_proposal SET plan_hash='tampered' WHERE plan_id=?",current.plan().planId());
            assertThrows(IllegalStateException.class,()->createNovel(current));
        } finally {settleNovel(current);}
    }

    @Test void oldConditionCannotSupplyDiversityForThreeSameConditionNewTasks() {
        var related=relatedSkill();var old=novelFixture(related,List.of(task(),task("old-condition"),task()));
        publishedSourceFixture(related,old);settleNovel(old);
        // Include old reference tasks so the overall author input has two conditions; the new subset still must have two.
        var freshTasks=List.of(task(),task(),task());for(var t:freshTasks) accept(t);
        enqueue(freshTasks.get(0));var claim=jobs.claimPending(3).orElseThrow();
        for(var t:freshTasks.subList(1,3)) enqueue(t);worker().runBatch(2,3);
        var fresh=new NovelFixture(freshTasks,claim,prepareProposal(claim,freshTasks,List.of(related)));
        try {assertEquals("SKILL_EVOLUTION_INSUFFICIENT_NEW_CONDITIONS",assertThrows(IllegalStateException.class,()->createNovel(fresh)).getMessage());}
        finally {settleNovel(fresh);}
    }
    @Test void correctedRevisionOfConsumedTaskStillIsNotNewEvidence() {
        var related=relatedSkill();var old=novelFixture(related,List.of(task(),task("old-condition"),task()));
        publishedSourceFixture(related,old);settleNovel(old);
        var revised=turn(old.tasks().get(0).session(),3,old.tasks().get(0).episode(),"SYNTHETIC revision of previously consumed task");
        var fresh=novelFixture(related,List.of(revised,task("new-condition"),task()));
        try {assertEquals("SKILL_EVOLUTION_INSUFFICIENT_NEW_SOURCES",assertThrows(IllegalStateException.class,()->createNovel(fresh)).getMessage());}
        finally {settleNovel(fresh);}
    }

    private SkillMethodMemory.Method method(String condition) {
        return new SkillMethodMemory.Method("只读核验目标版本",List.of(condition),List.of("通过只读工具取得实际版本"),List.of("对照目标标识和实际版本"),List.of("资源版本查询"));
    }
    @Test void observationRetryKeepsPersistedIdentityAcrossClassifierChangesAndSemanticAssignment() {
        var f=task();accept(f);
        var input=new SkillExperienceInput(project,"fixture-agent",f.run(),f.session(),"SUCCESSFUL_DIAGNOSTIC_PATTERN",
                "INSPECT","数据库查询",List.of("read target"),List.of(new SkillExperienceEvidenceReference("",f.result(),f.hash(),"MCP")),true,"SYNTHETIC retry",2);
        var original=experienceService().recordObservation(input);
        var changed=new SkillExperienceInput(project,"fixture-agent",f.run(),f.session(),input.observationType(),
                input.primaryIntent(),"网络不可用",input.toolEvidence(),input.evidenceReferences(),true,input.finalOutput(),2);
        var retry=experienceService().recordObservation(changed);
        assertEquals(original.observation().clusterKey(),retry.observation().clusterKey());
        assertFalse(retry.newObservation());assertEquals(1,retry.cluster().successfulCount());
        enqueue(f);var claim=jobs.claimPending(3).orElseThrow();var source=sources.load(claim);
        var store=proxy(new JdbcSkillExperienceGroupingStore(jdbc));
        store.saveFact(claim,source.sourceHash(),new SkillMethodMemory.Extraction(method("only-read"),"{}"));
        var group=store.commit(claim,source.sourceHash(),retry,new SkillMethodMemory.Decision("CREATE","","synthetic","{}"),List.of());
        var afterRestart=experienceService().recordObservation(input);
        assertEquals(group.groupId(),afterRestart.observation().clusterKey());
        assertEquals(1,afterRestart.cluster().successfulCount());assertEquals(1,afterRestart.cluster().version());
        jobs.complete(claim,patch(claim),SkillEvolutionJobStatus.SKIPPED);
    }
    private record GroupFixture(Fixture task,SkillEvolutionJobSnapshot claim,String hash,SkillExperienceRecordResult observed) { }
    private GroupFixture groupingFixture(String condition,JdbcSkillExperienceGroupingStore store) {
        var f=task(condition);accept(f);enqueue(f);var c=jobs.claimPending(3).orElseThrow();var input=sources.load(c);
        var observed=experienceService().recordObservation(new SkillExperienceInput(project,"fixture-agent",f.run(),f.session(),
                "SUCCESSFUL_DIAGNOSTIC_PATTERN","VERIFY_VERSION","核验隔离目标版本",List.of("read target"),
                List.of(new SkillExperienceEvidenceReference("",f.result(),f.hash(),"MCP")),true,"SYNTHETIC grouping fixture",2));
        store.saveFact(c,input.sourceHash(),new SkillMethodMemory.Extraction(method(condition),"{\"source\":\"SYNTHETIC_MODEL_FIXTURE\"}"));
        return new GroupFixture(f,c,input.sourceHash(),observed);
    }
    private SkillMethodMemory.Group createGroup(GroupFixture f,JdbcSkillExperienceGroupingStore store) {
        return store.commit(f.claim(),f.hash(),f.observed(),new SkillMethodMemory.Decision("CREATE","","synthetic isolated method","{}"),List.of());
    }
    private SkillMethodMemory.Group appendGroup(GroupFixture f,SkillMethodMemory.Group group,JdbcSkillExperienceGroupingStore store) {
        return store.commit(f.claim(),f.hash(),f.observed(),new SkillMethodMemory.Decision("APPEND",group.groupId(),"synthetic compatible method","{}"),List.of(group));
    }
    private void settleGroup(GroupFixture f) {assertTrue(jobs.complete(f.claim(),patch(f.claim()),SkillEvolutionJobStatus.SKIPPED).isPresent());}
    @Test void semanticGroupingIsIdempotentAcrossEightConcurrentCommitsAndKeepsOnlyOneIndependentSource() throws Exception {
        var store=proxy(new JdbcSkillExperienceGroupingStore(jdbc));var f=groupingFixture("service-one",store);
        try {
            var results=concurrent(()->createGroup(f,store));
            assertEquals(1,results.stream().map(SkillMethodMemory.Group::groupId).distinct().count());
            assertTrue(results.stream().allMatch(g->g.version()==1 && g.sources().size()==1));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_skill_method_group_version WHERE group_id=?",Integer.class,results.get(0).groupId()));
            assertEquals(1,new JdbcSkillExperienceAdapter(jdbc).cluster(project,"fixture-agent",results.get(0).groupId()).successfulCount());
        } finally {settleGroup(f);}
    }
    @Test void semanticGroupCasRejectsStaleModelDecisionThenRetractionRebuildsOnlyCurrentSources() {
        var store=proxy(new JdbcSkillExperienceGroupingStore(jdbc));var first=groupingFixture("first-condition",store);var original=createGroup(first,store);settleGroup(first);
        var second=groupingFixture("second-condition",store);var third=groupingFixture("third-condition",store);
        try {
            var changed=appendGroup(third,original,store);
            assertEquals("SKILL_GROUPING_STALE_GROUP",assertThrows(IllegalStateException.class,()->appendGroup(second,original,store)).getMessage());
            assertTrue(store.assigned(second.claim(),second.hash()).isEmpty());
            var complete=appendGroup(second,changed,store);assertEquals(3,complete.sources().size());
            assertEquals(Set.of("first-condition","second-condition","third-condition"),complete.sources().stream().flatMap(f->f.method().conditions().stream()).collect(java.util.stream.Collectors.toSet()));
            turn(first.task().session(),3,first.task().episode(),"用户纠正此前核验目标");
            var refreshed=store.current(project,original.groupId()).orElseThrow();
            assertEquals(2,refreshed.sources().size());assertTrue(refreshed.sources().stream().noneMatch(f->f.method().conditions().contains("first-condition")));
            assertTrue(refreshed.version()>complete.version());assertNotEquals(refreshed.contentHash(),complete.contentHash());
            assertTrue(store.current("other-project",original.groupId()).isEmpty());
        } finally {settleGroup(second);settleGroup(third);}
    }
    @Test void correctionDuringGroupingRejectsOldResultAndReviewKeepsPrivateFactWithoutGroupOrSkill() {
        var store=proxy(new JdbcSkillExperienceGroupingStore(jdbc));var f=groupingFixture("pending-review",store);
        store.defer(f.claim(),f.hash(),new SkillMethodMemory.Decision("REVIEW","","incomplete compatibility evidence","{}"));
        assertTrue(store.assigned(f.claim(),f.hash()).isEmpty());assertTrue(store.fact(f.claim(),f.hash()).isPresent());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_skill_method_group WHERE project_id=?",Integer.class,project));
        turn(f.task().session(),3,f.task().episode(),"纠正后重做");
        assertEquals("SKILL_EVOLUTION_SOURCE_REVOKED",assertThrows(IllegalStateException.class,()->createGroup(f,store)).getMessage());
        assertTrue(jobs.rescheduleOrFail(f.claim(),new SkillEvolutionRetryTransition(SkillEvolutionJobStatus.SKIPPED,1,null),"SKILL_EVOLUTION_SOURCE_REVOKED"));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"SKILL_GROUPING_DEFERRED","SKILL_EVIDENCE_INPUT_DEFERRED","SKILL_MODEL_TRANSPORT_DEFERRED","SKILL_CONTENT_REVIEW_UNAVAILABLE"})
    void groupingBackgroundFailureRetriesPastOrdinaryAttemptLimit(String failure) {
        var f=task();accept(f);var initial=enqueue(f);var ready=new java.util.concurrent.atomic.AtomicBoolean();
        var worker=new SkillEvolutionJobApplicationService(jobs,new SkillEvolutionJobPolicy(),new SkillEvolutionInputPolicy(),sources,
                request->{if(!ready.get()) {
                    if("SKILL_GROUPING_DEFERRED".equals(failure)) throw new SkillGroupingDeferredException(new RuntimeException("SYNTHETIC_NETWORK_FAILURE"));
                    throw new IllegalStateException(failure);
                }
                    return new SkillEvolutionPipelineDecision("SKIPPED","SKIP_SYNTHETIC_PROTOCOL_ONLY","","","{}","SKIPPED");},
                new SkillEvolutionPatchJsonEncoder(),()->"patch-"+UUID.randomUUID(),null,Clock.systemUTC());
        for(int i=0;i<5;i++) {
            worker.runBatch(1,3);var pending=jobs.findJob(initial.jobId()).orElseThrow();
            assertEquals(SkillEvolutionJobStatus.PENDING,pending.status());assertEquals(failure,pending.lastError());
            assertTrue(pending.nextRunAt().isAfter(Instant.now().plusSeconds((60L<<i)-5)));
            jdbc.update("UPDATE ai_ops_skill_evolution_job SET next_run_at=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP) WHERE job_id=?",initial.jobId());
        }
        ready.set(true);worker.runBatch(1,3);assertEquals(SkillEvolutionJobStatus.SKIPPED,jobs.findJob(initial.jobId()).orElseThrow().status());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"SKIP_INSUFFICIENT_REPEATED_OBSERVATIONS","SKIP_INSUFFICIENT_SOURCE_DIVERSITY","SKIP_NO_REUSABLE_PATTERN"})
    void previouslySkippedUnpublishedAcceptedTasksAreBackfilledThroughNormalQueue(String previousDecision) {
        var f=task();accept(f);var job=worker().enqueue(f.run(),f.session(),project,"fixture-agent","RUN_COMPLETED_BACKGROUND").job();
        var claim=jobs.claimPending(3).orElseThrow();
        var insufficient=new SkillEvolutionPatchSnapshot(0,"patch-"+UUID.randomUUID(),claim.jobId(),claim.runId(),project,"",
                previousDecision,"{}","{}","SKIPPED",null,"",null,null);
        jobs.complete(claim,insufficient,SkillEvolutionJobStatus.SKIPPED);
        var store=proxy(new JdbcSkillExperienceGroupingStore(jdbc));assertTrue(store.ungrouped(20).stream().anyMatch(r->r.runId().equals(f.run())));
        var retry=worker().enqueue(f.run(),f.session(),project,"fixture-agent","EXPERIENCE_GROUPING_BACKFILL").job();
        assertEquals(SkillEvolutionJobStatus.PENDING,retry.status());assertEquals(job.sourceId(),retry.sourceId());
        var failedClaim=jobs.claimPending(3).orElseThrow();
        assertTrue(jobs.rescheduleOrFail(failedClaim,new SkillEvolutionRetryTransition(SkillEvolutionJobStatus.FAILED,failedClaim.attempts()+1,null),"Incorrect result size: expected 1, actual 0"));
        assertTrue(store.ungrouped(20).stream().anyMatch(r->r.runId().equals(f.run())));
        assertEquals(SkillEvolutionJobStatus.PENDING,worker().enqueue(f.run(),f.session(),project,"fixture-agent","EXPERIENCE_GROUPING_BACKFILL").job().status());
        var resumed=jobs.claimPending(3).orElseThrow();var input=sources.load(resumed);store.saveFact(resumed,input.sourceHash(),new SkillMethodMemory.Extraction(method("old-condition"),"{}"));
        jobs.complete(resumed,patch(resumed),SkillEvolutionJobStatus.SKIPPED);
        assertTrue(store.ungrouped(20).stream().noneMatch(r->r.runId().equals(f.run())));
        assertEquals(SkillEvolutionJobStatus.SKIPPED,worker().enqueue(f.run(),f.session(),project,"fixture-agent","EXPERIENCE_GROUPING_BACKFILL").job().status());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"SKILL_MODEL_TRANSPORT_DEFERRED","SKILL_EVOLUTION_MODEL_UNAVAILABLE","SKILL_EVOLUTION_PROPOSAL_PENDING"})
    void deferredLeaseRecoveryAndConfigurationWaitRemainClaimableAfterAttemptLimit(String reason) {
        var f=task(); accept(f); var job=enqueue(f);
        var policy=new SkillEvolutionJobPolicy();
        for(int i=0;i<4;i++) {
            var claim=jobs.claimPending(3).orElseThrow();
            assertTrue(jobs.rescheduleOrFail(claim,policy.failureTransition(claim.attempts(),3,Instant.now(),
                    "SKILL_MODEL_TRANSPORT_DEFERRED"),"SKILL_MODEL_TRANSPORT_DEFERRED"));
            jdbc.update("UPDATE ai_ops_skill_evolution_job SET next_run_at=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP) WHERE job_id=?",job.jobId());
        }
        var beforeWait=jobs.claimPending(3).orElseThrow();
        assertTrue(jobs.rescheduleOrFail(beforeWait,policy.failureTransition(beforeWait.attempts(),3,Instant.now(),reason),reason));
        jdbc.update("UPDATE ai_ops_skill_evolution_job SET next_run_at=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP) WHERE job_id=?",job.jobId());
        var expired=jobs.claimPending(3).orElseThrow(); expire(expired);
        var resumed=jobs.claimPending(3).orElseThrow();
        assertEquals(expired.sourceId(),resumed.sourceId()); assertTrue(resumed.epoch()>expired.epoch());
        assertTrue(jobs.complete(expired,patch(expired),SkillEvolutionJobStatus.COMPLETED).isEmpty());
        assertTrue(jobs.complete(resumed,patch(resumed),SkillEvolutionJobStatus.COMPLETED).isPresent());
    }

    @Test void transportDeferralsDoNotExhaustSubsequentOrdinaryFailures() {
        var f=task(); accept(f); var job=enqueue(f);
        var policy=new SkillEvolutionJobPolicy();
        for(int i=0;i<4;i++) {
            var claim=jobs.claimPending(3).orElseThrow();
            assertTrue(jobs.rescheduleOrFail(claim,policy.failureTransition(claim.attempts(),3,Instant.now(),
                    "SKILL_MODEL_TRANSPORT_DEFERRED"),"SKILL_MODEL_TRANSPORT_DEFERRED"));
            jdbc.update("UPDATE ai_ops_skill_evolution_job SET next_run_at=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP) WHERE job_id=?",job.jobId());
        }
        var sourceHash=jdbc.queryForObject("SELECT input_hash FROM ai_ops_skill_evolution_source WHERE source_id=?",String.class,job.sourceId());
        for(int failed=0;failed<3;failed++) {
            // Recreate the repository to verify that exhaustion is persisted, not process-local.
            jobs=proxy(new JdbcSkillEvolutionJobRepository(beans.getBeanProvider(JdbcTemplate.class)));
            var claim=jobs.claimPending(3).orElseThrow();
            assertEquals(failed,claim.ordinaryFailures());
            assertEquals(4+failed,claim.attempts());
            var transition=policy.failureTransition(claim,3,Instant.now(),"SKILL_AUTHORING_MODEL_INVALID");
            var expected=failed<2?SkillEvolutionJobStatus.PENDING:SkillEvolutionJobStatus.FAILED;
            assertEquals(expected,transition.status(),"Transport deferrals must not exhaust the ordinary failure limit");
            assertTrue(jobs.rescheduleOrFail(claim,transition,"SKILL_AUTHORING_MODEL_INVALID"));
            assertFalse(jobs.rescheduleOrFail(claim,transition,"SKILL_AUTHORING_MODEL_INVALID"),"A late duplicate cannot charge twice");
            var stored=jobs.findJob(job.jobId()).orElseThrow();
            assertEquals(expected,stored.status()); assertEquals(failed+1,stored.ordinaryFailures());
            assertEquals(5+failed,stored.attempts()); assertEquals(job.sourceId(),stored.sourceId());
            jdbc.update("UPDATE ai_ops_skill_evolution_job SET next_run_at=TIMESTAMPADD(SECOND,-1,CURRENT_TIMESTAMP) WHERE job_id=?",job.jobId());
        }
        assertTrue(jobs.claimPending(3).isEmpty());
        assertEquals(sourceHash,jdbc.queryForObject("SELECT input_hash FROM ai_ops_skill_evolution_source WHERE source_id=?",String.class,job.sourceId()));
        var retried=worker().enqueue(f.run(),f.session(),project,"fixture-agent","MANUAL_RETRY").job();
        assertEquals(0,retried.ordinaryFailures()); assertEquals(0,retried.attempts());
        assertEquals(job.sourceId(),retried.sourceId());
        var fresh=jobs.claimPending(3).orElseThrow();
        assertTrue(jobs.complete(fresh,patch(fresh),SkillEvolutionJobStatus.COMPLETED).isPresent());
    }

    @Test void explicitRetryRecoversOnlyFailedUnpublishedAcceptedSourceAndDoesNotResetActiveWork() {
        var f=task(); accept(f); var initial=enqueue(f);
        var claim=jobs.claimPending(3).orElseThrow();
        assertTrue(jobs.rescheduleOrFail(claim,new SkillEvolutionRetryTransition(SkillEvolutionJobStatus.FAILED,claim.attempts()+1,null),"SYNTHETIC_TIMEOUT"));
        assertEquals(SkillEvolutionJobStatus.FAILED,worker().enqueue(f.run(),f.session(),project,"fixture-agent","RUN_COMPLETED_BACKGROUND").job().status());
        var retried=worker().enqueue(f.run(),f.session(),project,"fixture-agent","MANUAL_RETRY").job();
        assertEquals(initial.sourceId(),retried.sourceId());
        assertEquals(SkillEvolutionJobStatus.PENDING,retried.status());
        var active=jobs.claimPending(3).orElseThrow();
        var duplicate=worker().enqueue(f.run(),f.session(),project,"fixture-agent","MANUAL_RETRY").job();
        assertEquals(SkillEvolutionJobStatus.RUNNING,duplicate.status());
        assertEquals(active.leaseToken(),duplicate.leaseToken());
        assertTrue(jobs.complete(active,patch(active),SkillEvolutionJobStatus.COMPLETED).isPresent());
        assertEquals(SkillEvolutionJobStatus.COMPLETED,worker().enqueue(f.run(),f.session(),project,"fixture-agent","MANUAL_RETRY").job().status());
    }

    @Test void longModelRetriesDoNotStarveOlderReadyTasks() {
        var first=task();accept(first);var firstJob=enqueue(first);
        var second=task();accept(second);var secondJob=enqueue(second);
        jdbc.update("UPDATE ai_ops_skill_evolution_job SET next_run_at=TIMESTAMPADD(SECOND,-60,CURRENT_TIMESTAMP),last_error='SKILL_GROUPING_DEFERRED' WHERE job_id=?",firstJob.jobId());
        jdbc.update("UPDATE ai_ops_skill_evolution_job SET next_run_at=TIMESTAMPADD(SECOND,-120,CURRENT_TIMESTAMP) WHERE job_id=?",secondJob.jobId());
        var claim=jobs.claimPending(3).orElseThrow();assertEquals(secondJob.jobId(),claim.jobId());
        jobs.complete(claim,patch(claim),SkillEvolutionJobStatus.SKIPPED);
        assertEquals(firstJob.jobId(),jobs.claimPending(3).orElseThrow().jobId());
    }

}
