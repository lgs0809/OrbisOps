package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.episode.*;
import cn.lgs.orbisops.application.skill.*;
import cn.lgs.orbisops.domain.skill.model.*;
import cn.lgs.orbisops.domain.skill.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static cn.lgs.orbisops.infrastructure.adapter.repository.TaskEpisodeJson.*;

/** Synthetic task/evidence fixtures in disposable MySQL. Assertions run through the actual acceptance service. */
@Testcontainers(disabledWithoutDocker=true)
class TaskAcceptanceMySqlTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("task_acceptance").withUsername("fixture").withPassword("fixture");
    JdbcTemplate jdbc;DefaultListableBeanFactory beans;String project;
    JdbcTaskEpisodeStore episodes;JdbcTaskAcceptanceStore acceptance;JdbcSkillExperienceAdapter experiences;
    SkillExperienceApplicationService service;
    @BeforeEach void setup() throws Exception {
        var ds=new DriverManagerDataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());
        jdbc=new JdbcTemplate(ds);beans=new DefaultListableBeanFactory();var tx=new DataSourceTransactionManager(ds);
        beans.registerSingleton("mysqlJdbcTemplate",jdbc);beans.registerSingleton("mysqlTransactionManager",tx);
        Path root=Path.of("").toAbsolutePath();while(root!=null&&!Files.exists(root.resolve("db/migrations/sql/ops-task-episodes.sql")))root=root.getParent();assertNotNull(root);
        var jobDefinition=Pattern.compile("CREATE TABLE IF NOT EXISTS `ai_ops_skill_evolution_job` \\(.*?;",Pattern.DOTALL)
                .matcher(Files.readString(root.resolve("db/migrations/sql/ops-skill-evolution.sql")));
        assertTrue(jobDefinition.find());jdbc.execute(jobDefinition.group());
        try(var c=ds.getConnection()) {for(String name:List.of("ops-task-episodes","ops-task-acceptance","ops-skill-experience-layer","ops-alert-incident-correlation","ops-skill-evolution-sources","ops-skill-canary-review"))
            ScriptUtils.executeSqlScript(c,new FileSystemResource(root.resolve("db/migrations/sql/"+name+".sql")));}
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_ops_skill_release(release_id VARCHAR(80) PRIMARY KEY,project_id VARCHAR(128),status VARCHAR(32),target_skill_id VARCHAR(128) DEFAULT '',released_version INT DEFAULT 0,released_skill_hash VARCHAR(128) DEFAULT '')");
        jdbc.update("UPDATE ai_ops_skill_release SET status='ROLLED_BACK'"); // Keep this test's queue separate from earlier fixtures.
        jdbc.update("DELETE FROM ai_ops_skill_canary_review"); // Disposable Testcontainers database only.
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_ops_chat_session(session_id VARCHAR(80) PRIMARY KEY,project_id VARCHAR(128),user_id VARCHAR(80))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_ops_chat_message(id BIGINT AUTO_INCREMENT PRIMARY KEY,project_id VARCHAR(128),session_id VARCHAR(80),turn_id VARCHAR(80),message_seq BIGINT,role VARCHAR(32),content TEXT,metadata TEXT,create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,UNIQUE(session_id,message_seq))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_ops_agent_run(run_id VARCHAR(80) PRIMARY KEY,project_id VARCHAR(128),session_id VARCHAR(80),status VARCHAR(40),agent_id VARCHAR(128) DEFAULT 'fixture-agent',user_id VARCHAR(80) DEFAULT 'owner',agent_version INT DEFAULT 1,agent_definition_hash VARCHAR(64) DEFAULT 'parent-hash',request_json MEDIUMTEXT)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_ops_agent_run_checkpoint(id BIGINT AUTO_INCREMENT PRIMARY KEY,run_id VARCHAR(80),project_id VARCHAR(128),checkpoint_seq BIGINT,checkpoint_type VARCHAR(64),checkpoint_json MEDIUMTEXT,checkpoint_hash VARCHAR(64))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_ops_agent_node_trace(id BIGINT AUTO_INCREMENT PRIMARY KEY,run_id VARCHAR(80),sequence_no BIGINT,event_type VARCHAR(80),status VARCHAR(40),summary TEXT,payload_json MEDIUMTEXT)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_ops_tool_result(id BIGINT AUTO_INCREMENT PRIMARY KEY,project_id VARCHAR(128),run_id VARCHAR(80),result_id VARCHAR(80),tool_name VARCHAR(80),status VARCHAR(40),output_hash VARCHAR(64),full_output MEDIUMTEXT,source VARCHAR(80))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_ops_runtime_context_bundle(id BIGINT AUTO_INCREMENT PRIMARY KEY,project_id VARCHAR(128),session_id VARCHAR(80),run_id VARCHAR(80),bundle_id VARCHAR(80),bundle_hash VARCHAR(64),bundle_json TEXT,used_skill_version_refs_json TEXT,used_skill_refs_hash VARCHAR(64))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_ops_incident(incident_id VARCHAR(80) PRIMARY KEY,project_id VARCHAR(128))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_ops_incident_run(incident_id VARCHAR(80),run_id VARCHAR(80),UNIQUE(incident_id,run_id))");
        episodes=new JdbcTaskEpisodeStore(beans.getBeanProvider(JdbcTemplate.class),beans.getBeanProvider(PlatformTransactionManager.class));
        acceptance=new JdbcTaskAcceptanceStore(beans.getBeanProvider(JdbcTemplate.class),beans.getBeanProvider(PlatformTransactionManager.class));
        experiences=new JdbcSkillExperienceAdapter(jdbc);
        service=new SkillExperienceApplicationService(experiences,null,new SkillTransactionPort(){public<T>T required(Supplier<T> action){return new TransactionTemplate(tx).execute(s->action.get());}},new SkillExperienceObservationPolicy(),acceptance);
        project="p-"+UUID.randomUUID();
    }
    record Fixture(String session,String run,String episode,String result,String hash,long revision) { }
    Fixture task(String serviceId) {
        String session="s-"+UUID.randomUUID();jdbc.update("INSERT INTO ai_ops_chat_session VALUES (?,?,'owner')",session,project);
        return turn(session,1,"",serviceId);
    }
    Fixture turn(String session,long seq,String prior,String serviceId) {
        String run="r-"+UUID.randomUUID(),result="result-"+UUID.randomUUID();
        jdbc.update("INSERT INTO ai_ops_agent_run(run_id,project_id,session_id,status) VALUES (?,?,?,'SUCCEEDED')",run,project,session);
        for(int i=0;i<2;i++)jdbc.update("INSERT INTO ai_ops_chat_message(project_id,session_id,turn_id,message_seq,role,content,metadata) VALUES (?,?,?,?,?,'SYNTHETIC task fixture','{}')",project,session,run,seq+i,i==0?"user":"assistant");
        var normalized=Map.of("scope",Map.of("projectId",project,"environment","synthetic-test","serviceId",serviceId),"resourceIdentity","fixture-resource", "status","AVAILABLE","version","fixture-1","latency",0.25,"provenance","SYNTHETIC_TEST_FIXTURE");
        // Production uses a JSON wire envelope, not Fastjson's shared-object $ref encoding.
        String raw=cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringify(Map.of(
                "normalizedContent",normalized,"structuredContent",normalized,"isError",false,"orbisopsResultVersion",1));
        jdbc.update("INSERT INTO ai_ops_tool_result(project_id,run_id,result_id,tool_name,status,output_hash,full_output,source) VALUES (?,?,?,'fixture_version','SUCCEEDED',?,?,'MCP_REMOTE_TOOL')",project,run,result,hash(raw),raw);
        episodes.captureContext(project,session,run,Map.of("originalUserQuery","synthetic verify target version","memoryContext","synthetic fixture"));
        episodes.discover(1000);
        var claim=episodes.claimTurn(session,"synthetic-classifier",System.currentTimeMillis(),true).orElseThrow();
        assertTrue(episodes.assign(claim,new TaskEpisodeModelPort.Decision(prior.isBlank()?"CREATE":"CONTINUE",prior,prior.isBlank()?"核验隔离目标版本":"","SYNTHETIC decision"),System.currentTimeMillis()));
        String episode=jdbc.queryForObject("SELECT episode_id FROM ai_ops_task_episode_turn WHERE source_run_ref=?",String.class,run);
        long revision=jdbc.queryForObject("SELECT revision FROM ai_ops_task_episode WHERE episode_id=?",Long.class,episode);
        return new Fixture(session,run,episode,result,hash(raw),revision);
    }
    TaskAcceptanceRequest request(Fixture f,Object expected) {return new TaskAcceptanceRequest("request-"+UUID.randomUUID(),f.revision(),"Synthetic test: verify the declared target version",List.of(new TaskAcceptanceRequest.Criterion(f.result(),f.hash(),"/version","EQ",expected)));}
    record Child(String run,String result,String hash) { }
    Child child(Fixture parent) {
        String workflow="child-method",node="investigate";
        String suffix=cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher.sha256(Map.of("parentRunId",parent.run(),"nodeId",node,"workflowId",workflow)).substring(0,16);
        String run=parent.run().substring(0,Math.min(parent.run().length(),59))+"-sub-"+suffix;
        String result="child-result-"+UUID.randomUUID();
        var metadata=Map.of("source","SUB_WORKFLOW","parentRunId",parent.run(),"parentNodeId",node,"parentWorkflowDefinitionHash","parent-hash");
        jdbc.update("INSERT INTO ai_ops_agent_run(run_id,project_id,session_id,status,agent_id,agent_version,agent_definition_hash,request_json) VALUES (?,?,?,'SUCCEEDED',?,2,'child-hash',?)",run,project,"child-session-"+UUID.randomUUID(),workflow,cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringify(Map.of("metadata",metadata)));
        String raw=cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringify(Map.of("normalizedContent",Map.of("version","child-v2"),"isError",false,"orbisopsResultVersion",1));
        jdbc.update("INSERT INTO ai_ops_tool_result(project_id,run_id,result_id,tool_name,status,output_hash,full_output,source) VALUES (?,?,?,'child_version','SUCCEEDED',?,?,'MCP_REMOTE_TOOL')",project,run,result,hash(raw),raw);
        var output=Map.<String,Object>of("childRunId",run,"workflowId",workflow,"workflowVersion",2,"workflowDefinitionHash","child-hash","output","Synthetic child result");
        var now=Instant.now();
        var state=new cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowRunState(parent.run(),project,"plan-hash","parent-hash","context-hash",
                cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowRunStatus.SUCCEEDED,"",
                Map.of(node,new cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowNodeState(node,cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowNodeStatus.SUCCEEDED,1,
                        cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher.sha256(output),"","",now,now)),List.of(),Map.of(),Map.of("nodeOutput:"+node,output),
                cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowWaitState.none(),"","",now);
        var codec=new cn.lgs.orbisops.application.runtime.workflow.DurableWorkflowCheckpointCodec();
        var payload=codec.encode(codec.checkpoint(cn.lgs.orbisops.domain.runtime.workflow.model.DurableWorkflowCheckpointType.RUN_SUCCEEDED,state,now));
        jdbc.update("INSERT INTO ai_ops_agent_run_checkpoint(run_id,project_id,checkpoint_seq,checkpoint_type,checkpoint_json,checkpoint_hash) VALUES (?,?,1,'WORKFLOW_RUN_SUCCEEDED',?,?)",parent.run(),project,
                cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringify(payload),cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher.sha256(payload));
        return new Child(run,result,hash(raw));
    }
    TaskAcceptanceRequest childRequest(Fixture parent,Child child) {
        return new TaskAcceptanceRequest("child-"+UUID.randomUUID(),parent.revision(),"Verify both parent and actual child lookup receipts; do not claim a repair",List.of(
                new TaskAcceptanceRequest.Criterion(parent.result(),parent.hash(),"/version","EQ","fixture-1"),
                new TaskAcceptanceRequest.Criterion(child.result(),child.hash(),"/version","EQ","child-v2")));
    }
    @Test void committedChildReceiptsFlowThroughDraftVerificationSourceFreezeAndIndependentIntegrity() {
        var parent=task("parent");var child=child(parent);
        assertEquals(2,((List<?>)acceptance.draftEvidence(project,parent.episode(),"owner",false).get("receipts")).size());
        assertEquals("SUCCEEDED",verify(parent,childRequest(parent,child)).get("outcome"));
        assertTrue(acceptance.verifiedSuccess(project,parent.run()).matches(project,parent.run()));
        assertFalse(acceptance.verifiedSuccess(project,child.run()).matches(project,child.run()));
        freezeAcceptedSource(parent);
        var source=new JdbcSkillEvolutionSourceReader(jdbc).loadAccepted(project,parent.run(),jdbc.queryForObject("SELECT source_id FROM ai_ops_skill_evolution_source WHERE episode_id=?",String.class,parent.episode()));
        assertTrue(source.episodeJson().contains(child.result()));
        assertEquals(2,((List<?>)cn.lgs.orbisops.domain.shared.json.CanonicalJson.parseObject(source.episodeJson()).get("evidenceRunScope")).size());
        jdbc.update("UPDATE ai_ops_tool_result SET full_output='{}' WHERE result_id=?",child.result());
        assertFalse(acceptance.verifiedSuccess(project,parent.run()).matches(project,parent.run()));
    }
    @Test void forgedParentMetadataAndCrossProjectOrChangedChildCheckpointCannotWidenTaskEvidence() {
        var parent=task("parent");var child=child(parent);
        jdbc.update("DELETE FROM ai_ops_agent_run_checkpoint WHERE run_id=?",parent.run());
        assertEquals(1,((List<?>)acceptance.draftEvidence(project,parent.episode(),"owner",false).get("receipts")).size());
        assertThrows(SecurityException.class,()->verify(parent,childRequest(parent,child)));
        var other=task("second-parent");var actual=child(other);
        jdbc.update("UPDATE ai_ops_agent_run SET project_id='other-project' WHERE run_id=?",actual.run());
        assertThrows(SecurityException.class,()->acceptance.draftEvidence(project,other.episode(),"owner",false));
        var third=task("third-parent");child(third);
        jdbc.update("UPDATE ai_ops_agent_run_checkpoint SET checkpoint_json=JSON_SET(checkpoint_json,'$.state.variables.untrusted',true) WHERE run_id=?",third.run());
        assertThrows(SecurityException.class,()->acceptance.draftEvidence(project,third.episode(),"owner",false));
    }
    @Test void independentlyAcceptedInternalChildStillCannotContributeAnotherTaskSource() {
        var f=task("delegated");
        jdbc.update("UPDATE ai_ops_agent_run SET request_json=? WHERE run_id=?",cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringify(Map.of("metadata",Map.of("source","SUB_WORKFLOW","parentRunId","retained-parent"))),f.run());
        assertEquals("SUCCEEDED",verify(f,request(f,"fixture-1")).get("outcome"));
        assertFalse(acceptance.verifiedSuccess(project,f.run()).matches(project,f.run()));
        assertEquals("",new JdbcSkillEvolutionSourceReader(jdbc).freeze(new SkillEvolutionJobSnapshot(0,"job",f.run(),f.session(),project,"fixture-agent","",SkillEvolutionJobStatus.PENDING,0,Instant.now(),"",null,null)));
    }

    @Test void activeChildToolPreventsAcceptanceAndLateChildOwnershipChangeRevokesSource() {
        var parent=task("parent");var child=child(parent);
        jdbc.update("UPDATE ai_ops_tool_result SET status='RUNNING' WHERE result_id=?",child.result());
        assertThrows(IllegalStateException.class,()->verify(parent,childRequest(parent,child)));
        jdbc.update("UPDATE ai_ops_tool_result SET status='SUCCEEDED' WHERE result_id=?",child.result());
        assertEquals("SUCCEEDED",verify(parent,childRequest(parent,child)).get("outcome"));
        jdbc.update("UPDATE ai_ops_agent_run SET user_id='different-user' WHERE run_id=?",child.run());
        assertFalse(acceptance.verifiedSuccess(project,parent.run()).matches(project,parent.run()));
    }

    @Test void genericMcpReceiptSupportsDraftAcceptanceAndIndependentSourceIntegrityWithoutInventingObservabilityFields() {
        var original=task("generic");
        String raw=cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringify(Map.of("orbisopsResultVersion",1,"isError",false,
                "normalizedContent",Map.of("version","actual-v2","status","HEALTHY")));
        jdbc.update("UPDATE ai_ops_tool_result SET full_output=?,output_hash=? WHERE result_id=?",raw,hash(raw),original.result());
        var f=new Fixture(original.session(),original.run(),original.episode(),original.result(),hash(raw),original.revision());
        assertEquals(1,((List<?>)acceptance.draftEvidence(project,f.episode(),"owner",false).get("receipts")).size());
        var request=new TaskAcceptanceRequest("generic-"+UUID.randomUUID(),f.revision(),"Verify actual service version and health, without claiming a repair",
                List.of(new TaskAcceptanceRequest.Criterion(f.result(),f.hash(),"/version","EQ","actual-v2"),
                        new TaskAcceptanceRequest.Criterion(f.result(),f.hash(),"/status","EQ","HEALTHY")));
        assertEquals("SUCCEEDED",verify(f,request).get("outcome"));
        assertTrue(acceptance.verifiedSuccess(project,f.run()).matches(project,f.run()));
        assertThrows(SecurityException.class,()->acceptance.draftEvidence(project,f.episode(),"different-owner",false));
        jdbc.update("UPDATE ai_ops_tool_result SET full_output='{}' WHERE result_id=?",f.result());
        assertFalse(acceptance.verifiedSuccess(project,f.run()).matches(project,f.run()));
    }

    @Test void naturalDraftEvidenceChecksOwnershipProjectAndOriginalReceiptIntegrity() {
        var f=task("natural-draft");
        var evidence=acceptance.draftEvidence(project,f.episode(),"owner",false);
        assertEquals(1,((List<?>)evidence.get("receipts")).size());
        assertEquals(1,((List<?>)evidence.get("originalUserRequests")).size());
        assertTrue(evidence.get("originalUserRequests").toString().contains("SYNTHETIC task fixture"));
        assertThrows(SecurityException.class,()->acceptance.draftEvidence(project,f.episode(),"other-user",false));
        assertThrows(SecurityException.class,()->acceptance.draftEvidence("other-project",f.episode(),"owner",true));
        jdbc.update("UPDATE ai_ops_tool_result SET full_output='{}' WHERE result_id=?",f.result());
        assertThrows(IllegalStateException.class,()->acceptance.draftEvidence(project,f.episode(),"owner",false));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_task_acceptance WHERE episode_id=?",Integer.class,f.episode()));
    }
    @Test void aNaturalDraftDoesNotWriteAnOutcomeAndTheConfirmedRequestStillUsesDeterministicChecks() {
        var f=task("natural-confirm");
        TaskAcceptanceDraftModelPort model=input->new TaskAcceptanceDraftModelPort.Draft("READY","合成协议测试，不代表真实模型推理质量。",
                "按照合成测试目标核对版本；不得冒充系统健康或发布成功。",List.of(new TaskAcceptanceDraftModelPort.Check("目标版本",f.result(),"/version","EQ","fixture-1")));
        var application=new TaskAcceptanceApplicationService(acceptance,model);
        var result=application.draft(project,f.episode(),new TaskAcceptanceApplicationService.NaturalRequest(f.revision(),"检查目标版本"),"owner",false);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_task_acceptance WHERE episode_id=?",Integer.class,f.episode()));
        var request=(TaskAcceptanceRequest)result.get("request");
        assertEquals("SUCCEEDED",application.verify(project,f.episode(),request,"owner",false).get("outcome"));
        assertEquals("SUCCEEDED",application.verify(project,f.episode(),request,"owner",false).get("outcome"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_task_acceptance WHERE episode_id=?",Integer.class,f.episode()));
    }
    Map<String,Object> verify(Fixture f,TaskAcceptanceRequest r) {return new TaskAcceptanceApplicationService(acceptance).verify(project,f.episode(),r,"owner",false);}
    @Test void derivedMetricAcceptanceRevalidatesOriginalReceiptBeforeAdmittingExperience() {
        var f=task("metrics");
        var normalized=cn.lgs.orbisops.domain.skill.SyntheticAcceptanceMetrics.receipt(project,0);
        String raw=cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringify(Map.of("normalizedContent",normalized,"isError",false,"orbisopsResultVersion",1));
        jdbc.update("UPDATE ai_ops_tool_result SET full_output=?,output_hash=? WHERE result_id=?",raw,hash(raw),f.result());
        var criteria=List.of(new TaskAcceptanceRequest.Criterion(f.result(),hash(raw),"/observedWindowV1/errorRate","LE",.01),
                new TaskAcceptanceRequest.Criterion(f.result(),hash(raw),"/observedWindowV1/p95Seconds","LE",1),
                new TaskAcceptanceRequest.Criterion(f.result(),hash(raw),"/observedWindowV1/sampleCountLowerBound","GE",100),
                new TaskAcceptanceRequest.Criterion(f.result(),hash(raw),"/observedWindowV1/evidenceComplete","EQ",true));
        var request=new TaskAcceptanceRequest("request-"+UUID.randomUUID(),f.revision(),"SYNTHETIC full-window business metrics verification",criteria);
        assertEquals("SUCCEEDED",verify(f,request).get("outcome"));
        assertTrue(acceptance.verifiedSuccess(project,f.run()).matches(project,f.run()));
        jdbc.update("UPDATE ai_ops_tool_result SET full_output='{}' WHERE result_id=?",f.result());
        assertFalse(acceptance.verifiedSuccess(project,f.run()).matches(project,f.run()));
    }
    SkillExperienceInput input(Fixture f) {return new SkillExperienceInput(project,"fixture-agent",f.run(),f.session(),"SUCCESSFUL_DIAGNOSTIC_PATTERN","VERIFY_VERSION","核验目标版本",List.of("read target"),List.of(new SkillExperienceEvidenceReference("",f.result(),f.hash(),"MCP")),true,"SYNTHETIC fixture result, task outcome must be separately verified",2);}
    void freezeAcceptedSource(Fixture f) {
        // Use the production source freezer under the same session/episode lock order as job enqueue.
        var transaction=new TransactionTemplate(beans.getBean(PlatformTransactionManager.class));
        transaction.execute(status->{
            jdbc.queryForList("SELECT session_id FROM ai_ops_task_episode_session WHERE session_id=? AND project_id=? FOR UPDATE",f.session(),project);
            jdbc.queryForList("SELECT episode_id FROM ai_ops_task_episode WHERE episode_id=? AND project_id=? FOR UPDATE",f.episode(),project);
            var job=new SkillEvolutionJobSnapshot(0,"fixture-job-"+UUID.randomUUID(),f.run(),f.session(),project,"fixture-agent",
                    "RUN_COMPLETED_BACKGROUND",SkillEvolutionJobStatus.PENDING,0,Instant.now(),"",null,null);
            assertFalse(new JdbcSkillEvolutionSourceReader(jdbc).freeze(job).isBlank());return null;
        });
    }
    private SkillReleaseSnapshot canaryRelease() {
        jdbc.update("INSERT IGNORE INTO ai_ops_skill_release(release_id,project_id,status) VALUES (?,?,'CANARY')","release-"+project,project);
        return new SkillReleaseSnapshot("release-"+project,"candidate",project,"fixture-agent","skill",
                SkillReleaseStatus.CANARY,10,1,"base","",0,"",Map.of());
    }
    private void exposed(Fixture fixture, SkillReleaseSnapshot release) {
        var refs=List.of(Map.of("releaseId",release.releaseId(),"skillId","skill","version",2,"skillHash","candidate-hash"));
        exposed(fixture,refs);
    }
    private void exposed(Fixture fixture, Object refs) {
        String raw=cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringify(refs);
        var bundle=Map.of("projectId",project,"runId",fixture.run(),"usedSkillVersionRefs",refs,"policy",Map.of("fixture","SYNTHETIC READ_ONLY"),"toolsetBoundaryHash",hash("synthetic-tools"));
        String payload=cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringify(bundle);
        jdbc.update("INSERT INTO ai_ops_runtime_context_bundle(project_id,session_id,run_id,used_skill_version_refs_json,used_skill_refs_hash,bundle_json,bundle_hash) VALUES (?,?,?,?,?,?,?)",
                project,fixture.session(),fixture.run(),raw,hash(raw),payload,hash(payload));
        jdbc.update("INSERT INTO ai_ops_agent_node_trace(run_id,sequence_no,event_type,status,payload_json) VALUES (?,1,'RUNTIME_RESOURCES','SUCCEEDED',?)",
                fixture.run(),cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringify(Map.of("projectId",project,"modelName","gpt-5.6-luna","modelBindingHash",hash("SYNTHETIC MODEL BINDING"))));
    }
    @Test void publishedVersionWithoutCandidateReleaseIdStillEntersIndependentSafetyReview() {
        var canary=canaryRelease();var f=task("one");
        var active=new SkillReleaseSnapshot(canary.releaseId(),"candidate",project,"fixture-agent","skill",SkillReleaseStatus.ACTIVE,10,1,"base","",2,"published-hash",Map.of());
        jdbc.update("UPDATE ai_ops_skill_release SET status='ACTIVE',target_skill_id='skill',released_version=2,released_skill_hash='published-hash' WHERE release_id=?",active.releaseId());
        exposed(f,List.of(Map.of("skillId","skill","version",2,"skillHash","published-hash","scope","PROJECT")));
        var store=new JdbcSkillCanaryReviewStore(jdbc);assertEquals(1,store.discover(100));
        var claim=store.claim(1000).orElseThrow();
        store.complete(claim,new SkillCanaryReviewPort.Decision("VIOLATION","NONE",List.of(f.result()),"SYNTHETIC published-version violation; mechanics only"));
        var evidence=new JdbcSkillCanaryEvidenceReader(jdbc).read(active);
        assertEquals(1,evidence.assignedTasks());assertEquals("CANARY_SAFETY_VIOLATION",evidence.isolationReason());
    }
    private SkillReleaseSnapshot contracted(SkillReleaseSnapshot release,List<Fixture> fixtures) {
        var keys=fixtures.stream().map(f->acceptance.verifiedSuccess(project,f.run()).conditionKey()).distinct().sorted().toList();
        var contract=new LinkedHashMap<String,Object>();
        contract.put("version","skill-canary-contract-v1");contract.put("candidateHash","candidate-hash");
        contract.put("acceptancePolicy","task-receipt-acceptance-v1");contract.put("conditionKeys",keys);
        contract.put("modelBindings",List.of(cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringify(Map.of("modelName","gpt-5.6-luna","bindingHash",hash("SYNTHETIC MODEL BINDING")))));
        contract.put("toolsetBindings",List.of(hash("synthetic-tools")));
        contract.put("contractHash",hash(cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringify(contract)));
        return new SkillReleaseSnapshot(release.releaseId(),release.candidateId(),release.projectId(),release.agentId(),release.targetSkillId(),
                release.status(),release.canaryPercent(),release.baselineVersion(),release.baselineSkillHash(),release.reasonCode(),0,"",Map.of("observationContract",contract));
    }
    @Test void twentyIndependentReviewedAcceptedTasksPassButChangedModelOrMissingCoverageCannot() {
        var release=canaryRelease();var fixtures=new ArrayList<Fixture>();
        for(int i=0;i<20;i++){var f=task("condition-"+(i%2));verify(f,request(f,"fixture-1"));exposed(f,release);fixtures.add(f);}
        var frozen=contracted(release,fixtures);var store=new JdbcSkillCanaryReviewStore(jdbc);store.discover(100);
        Optional<SkillCanaryReviewPort.Claim> pending;
        while((pending=store.claim(1000)).isPresent()) store.complete(pending.get(),new SkillCanaryReviewPort.Decision("SAFE","NONE",List.of(),"SYNTHETIC reviewer result; pipeline mechanics only"));
        var reader=new JdbcSkillCanaryEvidenceReader(jdbc);var evidence=reader.read(frozen);
        assertEquals(20,evidence.safetyReviewedTasks());assertEquals(0,evidence.unresolvedAssignments());assertTrue(evidence.promotable());
        var f=fixtures.get(0);
        jdbc.update("UPDATE ai_ops_agent_node_trace SET payload_json=JSON_SET(payload_json,'$.modelBindingHash',?) WHERE run_id=?",hash("changed provider"),f.run());
        assertFalse(reader.read(frozen).promotable());assertTrue(reader.read(frozen).unresolvedAssignments()>0);
    }
    @Test void publishedIdentityRetainsTheSameEvidenceContractWithoutRequiringCandidateOnlyReferences() {
        var release=canaryRelease();var fixtures=new ArrayList<Fixture>();
        var refs=List.of(Map.of("skillId","skill","version",2,"skillHash","published-hash","scope","PROJECT"));
        jdbc.update("UPDATE ai_ops_skill_release SET status='ACTIVE',target_skill_id='skill',released_version=2,released_skill_hash='published-hash' WHERE release_id=?",release.releaseId());
        for(int i=0;i<20;i++){var f=task("condition-"+(i%2));verify(f,request(f,"fixture-1"));exposed(f,refs);fixtures.add(f);}
        var contract=contracted(release,fixtures);
        var active=new SkillReleaseSnapshot(release.releaseId(),release.candidateId(),project,release.agentId(),"skill",
                SkillReleaseStatus.ACTIVE,10,1,"base","",2,"published-hash",contract.metadata());
        var store=new JdbcSkillCanaryReviewStore(jdbc);assertEquals(20,store.discover(100));
        Optional<SkillCanaryReviewPort.Claim> pending;
        while((pending=store.claim(1000)).isPresent()) store.complete(pending.get(),new SkillCanaryReviewPort.Decision("SAFE","NONE",List.of(),"SYNTHETIC reviewer; validates persistence only"));
        assertTrue(new JdbcSkillCanaryEvidenceReader(jdbc).read(active).promotable());
        var contracts=new JdbcSkillCanaryContractReader(jdbc);
        assertTrue(contracts.candidateMatches(active,cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringify(refs)));
        for(var field:List.of("skillId","version","skillHash","scope")) {
            var changed=new LinkedHashMap<String,Object>(refs.get(0));changed.put(field,"version".equals(field)?3:"changed");
            assertFalse(contracts.candidateMatches(active,cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringify(List.of(changed))),field);
        }
        var tampered=new SkillReleaseSnapshot(active.releaseId(),active.candidateId(),project,active.agentId(),"skill",
                SkillReleaseStatus.ACTIVE,10,1,"base","",2,"published-hash",Map.of());
        assertFalse(contracts.candidateMatches(tampered,cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringify(refs)));
    }
    @Test void retryQuotaAndLeaseFenceSurviveStoreRecreation() {
        var release=canaryRelease();var f=task("one");exposed(f,release);
        var input=new JdbcSkillCanaryReviewInput(jdbc).read(project,f.episode(),release.releaseId()).orElseThrow();String id=hash(input);
        jdbc.update("INSERT INTO ai_ops_skill_canary_review(review_id,project_id,release_id,episode_id,input_hash,input_json) VALUES (?,?,?,?,?,?)",id,project,release.releaseId(),f.episode(),id,input);
        SkillCanaryReviewPort.Claim first=null;
        long now=1000;
        for(int attempt=1;attempt<=5;attempt++) {
            var store=new JdbcSkillCanaryReviewStore(jdbc);var claim=store.claim(now).orElseThrow();if(first==null)first=claim;
            assertEquals(attempt,claim.attempt());store.retry(claim,now+30000,"Synthetic timeout");
            assertTrue(store.claim(now+1).isEmpty());now+=30000;
        }
        var store=new JdbcSkillCanaryReviewStore(jdbc);assertTrue(store.claim(now).isEmpty());
        store.complete(first,new SkillCanaryReviewPort.Decision("SAFE","NONE",List.of(),"Late stale response"));
        assertEquals("EXHAUSTED",jdbc.queryForObject("SELECT status FROM ai_ops_skill_canary_review WHERE review_id=?",String.class,id));
        assertEquals(5,jdbc.queryForObject("SELECT attempt_count FROM ai_ops_skill_canary_review WHERE review_id=?",Integer.class,id));
    }
    @Test void reviewIsDurableAndIdempotentAndCorrectionsInvalidateThePreviousReview() throws Exception {
        var release=canaryRelease();var first=task("condition-one");verify(first,request(first,"fixture-1"));exposed(first,release);
        var store=new JdbcSkillCanaryReviewStore(jdbc);
        store.discover(100);int count=jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_skill_canary_review WHERE project_id=?",Integer.class,project);
        store.discover(100);assertEquals(count,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_skill_canary_review WHERE project_id=?",Integer.class,project));
        var pool=Executors.newFixedThreadPool(8);var claims=new ArrayList<SkillCanaryReviewPort.Claim>();
        try {var jobs=new ArrayList<Callable<Optional<SkillCanaryReviewPort.Claim>>>();for(int i=0;i<8;i++) jobs.add(()->store.claim(1000));
            for(var result:pool.invokeAll(jobs))result.get().ifPresent(claims::add);
        }finally{pool.shutdownNow();}
        var claim=claims.stream().filter(c->c.input().contains(project)).findFirst().orElseThrow();
        assertEquals(1,claims.stream().filter(c->c.input().contains(project)).count());
        assertThrows(IllegalArgumentException.class,()->store.complete(claim,new SkillCanaryReviewPort.Decision("VIOLATION","NONE",List.of("invented"),"Synthetic invalid reference")));
        store.complete(claim,new SkillCanaryReviewPort.Decision("SAFE","NONE",List.of(first.result()),"SYNTHETIC protocol result; not real model evidence"));
        assertEquals(1,new JdbcSkillCanaryEvidenceReader(jdbc).read(release).safetyReviewedTasks());
        var next=turn(first.session(),3,first.episode(),"condition-one");exposed(next,release);
        assertEquals(0,new JdbcSkillCanaryEvidenceReader(jdbc).read(release).safetyReviewedTasks());
        assertEquals(0,new JdbcSkillCanaryEvidenceReader(jdbc).read(release).succeededTasks());
    }
    @Test void firstSafetyViolationIsVisibleBeforeTwentyAndLateIncidentGroupingDeduplicatesTasks() {
        var release=canaryRelease();var a=task("one");var b=task("two");
        for(var f:List.of(a,b)){verify(f,request(f,"fixture-1"));exposed(f,release);}
        var store=new JdbcSkillCanaryReviewStore(jdbc);store.discover(100);
        Optional<SkillCanaryReviewPort.Claim> pending;
        while((pending=store.claim(1000)).isPresent()) {
            var claim=pending.get();boolean own=claim.input().contains(project);
            String id=own && claim.input().contains(a.episode())?a.result():own?b.result():"";
            store.complete(claim,new SkillCanaryReviewPort.Decision(own?"VIOLATION":"UNKNOWN","NONE",id.isBlank()?List.of():List.of(id),"SYNTHETIC safety fixture"));
        }
        var reader=new JdbcSkillCanaryEvidenceReader(jdbc);assertEquals("CANARY_SAFETY_VIOLATION",reader.read(release).isolationReason());
        assertEquals(2,reader.read(release).assignedTasks());
        String incident="i-"+UUID.randomUUID();jdbc.update("INSERT INTO ai_ops_incident VALUES (?,?)",incident,project);
        jdbc.update("INSERT INTO ai_ops_incident_run VALUES (?,?),(?,?)",incident,a.run(),incident,b.run());
        assertEquals(1,reader.read(release).assignedTasks());assertEquals(1,reader.read(release).safetyViolations());
    }
    @Test void failedAcceptanceMustAlsoHaveMatchingIdentityAndRetainedReceipts() {
        var release=canaryRelease();var f=task("one");var accepted=verify(f,request(f,"wrong"));exposed(f,release);
        var reader=new JdbcSkillCanaryEvidenceReader(jdbc);assertEquals(1,reader.read(release).resolvedTasks());
        var forged=new LinkedHashMap<>(accepted);forged.put("sourceRunId","another-run");
        String raw=cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringify(forged);
        jdbc.update("UPDATE ai_ops_task_acceptance SET record_json=?,record_hash=? WHERE acceptance_id=?",raw,hash(raw),accepted.get("acceptanceId"));
        assertEquals(0,reader.read(release).resolvedTasks());
    }
    @Test void canaryExposureIsDeduplicatedAfterEpisodeAssignmentAndCorrectionReopensOutcome() {
        var release=canaryRelease(); var first=task("condition-one"); verify(first,request(first,"fixture-1"));
        for(int i=0;i<20;i++) exposed(first,release);
        var reader=new JdbcSkillCanaryEvidenceReader(jdbc);
        var evidence=reader.read(release);
        assertEquals(1,evidence.assignedTasks()); assertEquals(1,evidence.succeededTasks());
        assertFalse(evidence.promotable());
        var next=turn(first.session(),3,first.episode(),"condition-one"); exposed(next,release);
        assertEquals(1,reader.read(release).assignedTasks());
        assertEquals(0,reader.read(release).succeededTasks());
        verify(next,request(next,"fixture-1"));
        assertEquals(1,reader.read(release).succeededTasks());
    }
    @Test void canaryKeepsFirstTwentyIncludingFailureAndNeverUsesLaterSuccessToReplaceIt() {
        var release=canaryRelease();
        for(int i=0;i<21;i++) {
            var f=task("condition-"+(i%2)); verify(f,request(f,i==0?"wrong-version":"fixture-1")); exposed(f,release);
        }
        var evidence=new JdbcSkillCanaryEvidenceReader(jdbc).read(release);
        assertEquals(20,evidence.assignedTasks()); assertEquals(20,evidence.resolvedTasks());
        assertEquals(19,evidence.succeededTasks()); assertEquals(2,evidence.coveredConditions());
        assertFalse(evidence.promotable(),"Missing independent safety review cannot default to safe");
    }
    @Test void unassignedExposureIsRetainedAndCrossProjectReleaseCannotReadIt() {
        var release=canaryRelease(); exposed(new Fixture("unknown-session","unknown-run","","","",1),release);
        assertEquals(1,new JdbcSkillCanaryEvidenceReader(jdbc).read(release).unresolvedAssignments());
        var other=new SkillReleaseSnapshot(release.releaseId(),"candidate","other-project","fixture-agent","skill",
                SkillReleaseStatus.CANARY,10,1,"base","",0,"",Map.of());
        assertEquals(0,new JdbcSkillCanaryEvidenceReader(jdbc).read(other).assignedTasks());
    }

    @Test void completedAndRealisticReceiptsDoNotBecomeSuccessWithoutAcceptance() {
        var f=task("service-1");var observation=service.recordObservation(input(f));
        assertEquals("UNVERIFIED",observation.observation().outcome());assertEquals(0,observation.cluster().successfulCount());
        assertFalse(observation.observation().abstractTrajectory().contains("SYNTHESIZE_VERIFIED_OUTCOME"));
        assertEquals("SUCCEEDED",verify(f,request(f,"fixture-1")).get("outcome"));
        var accepted=service.recordObservation(input(f));assertEquals("SUCCEEDED",accepted.observation().outcome());
        assertEquals(1,accepted.cluster().successfulCount());assertEquals(1,service.recordObservation(input(f)).cluster().successfulCount());
        assertEquals(accepted.cluster().observationCount(),service.recordObservation(input(f)).cluster().observationCount());
        freezeAcceptedSource(f);
        var samples=service.consolidationSamples(project,"fixture-agent",accepted.observation().clusterKey(),20);
        assertEquals(1,samples.size());assertEquals(hash(samples.get(0).episodeJson()),samples.get(0).sourceHash());
        assertTrue(samples.get(0).episodeJson().contains(f.result()));
    }
    @Test void falseAndMissingChecksAreRetainedWithoutEligibilityAndReceiptStatusCannotBeUsedAsSuccess() {
        var f=task("service-1");assertEquals("FAILED",verify(f,request(f,"wrong-version")).get("outcome"));
        var unknown=new TaskAcceptanceRequest("missing-"+UUID.randomUUID(),f.revision(),"Synthetic missing field check",List.of(new TaskAcceptanceRequest.Criterion(f.result(),f.hash(),"/missing","EQ",true)));
        assertEquals("UNKNOWN",verify(f,unknown).get("outcome"));assertFalse(acceptance.verifiedSuccess(project,f.run()).matches(project,f.run()));
        var metadata=new TaskAcceptanceRequest("status-"+UUID.randomUUID(),f.revision(),"Synthetic receipt status attempt",List.of(new TaskAcceptanceRequest.Criterion(f.result(),f.hash(),"/status","EQ","AVAILABLE")));
        assertThrows(IllegalArgumentException.class,()->verify(f,metadata));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_task_acceptance WHERE episode_id=?",Integer.class,f.episode()));
    }
    @Test void crossTaskUnauthorizedOrChangedReceiptCannotBeAccepted() {
        var a=task("service-1");var b=task("service-2");
        assertThrows(SecurityException.class,()->acceptance.verify(project,a.episode(),request(b,"fixture-1"),"owner",false));
        assertThrows(SecurityException.class,()->acceptance.verify(project,a.episode(),request(a,"fixture-1"),"another-owner",false));
        assertThrows(SecurityException.class,()->acceptance.inspect("other-project",a.episode(),"owner",true));
        jdbc.update("UPDATE ai_ops_tool_result SET full_output='{}' WHERE result_id=?",a.result());
        assertThrows(IllegalStateException.class,()->verify(a,request(a,"fixture-1")));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_task_acceptance WHERE episode_id=?",Integer.class,a.episode()));
    }
    @Test void pendingClassificationApprovalAndUnknownToolPreventAcceptance() {
        var f=task("service-1");
        jdbc.update("UPDATE ai_ops_agent_run SET status='WAITING_APPROVAL' WHERE run_id=?",f.run());
        assertThrows(IllegalStateException.class,()->verify(f,request(f,"fixture-1")));
        jdbc.update("UPDATE ai_ops_agent_run SET status='SUCCEEDED' WHERE run_id=?",f.run());
        jdbc.update("UPDATE ai_ops_tool_result SET status='UNKNOWN' WHERE result_id=?",f.result());
        assertThrows(IllegalStateException.class,()->verify(f,request(f,"fixture-1")));
        jdbc.update("UPDATE ai_ops_tool_result SET status='SUCCEEDED' WHERE result_id=?",f.result());
        jdbc.update("INSERT INTO ai_ops_chat_message(project_id,session_id,turn_id,message_seq,role,content,metadata) VALUES (?,?,?,3,'user','unassigned correction','{}')",project,f.session(),"next-unassigned");
        assertThrows(IllegalStateException.class,()->verify(f,request(f,"fixture-1")));
    }
    @Test void eightConcurrentSubmissionsCreateOneImmutableAcceptanceAndRejectChangedReplay() throws Exception {
        var f=task("service-1");var request=request(f,"fixture-1");var pool=Executors.newFixedThreadPool(8);
        Set<String> ids=new HashSet<>();
        try {var jobs=new ArrayList<Callable<String>>();for(int i=0;i<8;i++)jobs.add(()->text(verify(f,request).get("acceptanceId")));
            for(var result:pool.invokeAll(jobs))ids.add(result.get(10,TimeUnit.SECONDS));}finally{pool.shutdownNow();}
        assertEquals(1,ids.size());
        var changed=new TaskAcceptanceRequest(request.requestId(),f.revision(),request.goalReview(),List.of(new TaskAcceptanceRequest.Criterion(f.result(),f.hash(),"/version","EQ","other")));
        assertThrows(IllegalStateException.class,()->verify(f,changed));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_task_acceptance WHERE episode_id=?",Integer.class,f.episode()));
    }
    @Test void correctionInvalidatesPriorSuccessAndOldRetryCannotOverwriteNewRevision() {
        var first=task("service-1");var request=request(first,"fixture-1");verify(first,request);
        var old=service.recordObservation(input(first));assertEquals(1,old.cluster().successfulCount());
        var corrected=turn(first.session(),3,first.episode(),"service-1");
        assertFalse(acceptance.verifiedSuccess(project,first.run()).matches(project,first.run()));
        assertEquals(0,experiences.cluster(project,"fixture-agent",old.observation().clusterKey()).successfulCount());
        assertEquals(1L,number(verify(first,request).get("revision")));
        assertEquals("UNKNOWN",jdbc.queryForObject("SELECT outcome FROM ai_ops_task_episode WHERE episode_id=?",String.class,first.episode()));
        assertThrows(IllegalStateException.class,()->verify(first,request(first,"fixture-1")));
        var staleEvidence=new TaskAcceptanceRequest("stale-"+UUID.randomUUID(),corrected.revision(),"Synthetic correction requires a fresh receipt",request(first,"fixture-1").criteria());
        assertThrows(IllegalStateException.class,()->verify(corrected,staleEvidence));
        verify(corrected,request(corrected,"fixture-1"));var next=service.recordObservation(input(corrected));
        assertEquals(1,next.cluster().successfulCount());assertEquals(1,experiences.clusterEvidence(project,"fixture-agent",next.observation().clusterKey()).distinctRuns());
    }
    @Test void independentCountsFollowActualTasksAndLateIncidentGroupingInsteadOfRunsOrTriggerTypes() {
        List<Fixture> fixtures=List.of(task("service-1"),task("service-1"),task("service-2"));String cluster="";
        for(var f:fixtures) {verify(f,request(f,"fixture-1"));freezeAcceptedSource(f);cluster=service.recordObservation(input(f)).observation().clusterKey();}
        var initial=experiences.clusterEvidence(project,"fixture-agent",cluster);assertEquals(3,initial.distinctRuns());assertEquals(2,initial.distinctConditions());
        String incident1="i-"+UUID.randomUUID(),incident2="i-"+UUID.randomUUID();
        jdbc.update("INSERT INTO ai_ops_incident VALUES (?,?),(?,?)",incident1,project,incident2,project);
        jdbc.update("INSERT INTO ai_ops_incident_run VALUES (?,?),(?,?),(?,?)",incident1,fixtures.get(0).run(),incident1,fixtures.get(1).run(),incident2,fixtures.get(2).run());
        assertEquals(2,experiences.clusterEvidence(project,"fixture-agent",cluster).distinctRuns());
        String group="g-"+UUID.randomUUID();jdbc.update("INSERT INTO ai_ops_alert_correlation_group(group_id,project_id,environment,anchor_json) VALUES (?,?,'synthetic-test','{}')",group,project);
        for(String incident:List.of(incident1,incident2))jdbc.update("INSERT INTO ai_ops_alert_correlation_member(group_id,incident_id,signal_json,decision_json,last_event_id) VALUES (?,?,'{}','{}',1)",group,incident);
        assertEquals(1,experiences.clusterEvidence(project,"fixture-agent",cluster).distinctRuns());
        assertEquals(1,experiences.consolidationSamples(project,"fixture-agent",cluster,20).size());
    }
    @Test void acceptanceRecordCorruptionDoesNotBecomeAContributingSource() {
        var f=task("service-1");var result=verify(f,request(f,"fixture-1"));var recorded=service.recordObservation(input(f));
        jdbc.update("UPDATE ai_ops_task_acceptance SET record_json='{}' WHERE acceptance_id=?",result.get("acceptanceId"));
        assertFalse(acceptance.verifiedSuccess(project,f.run()).matches(project,f.run()));
        assertEquals(0,experiences.cluster(project,"fixture-agent",recorded.observation().clusterKey()).successfulCount());
    }
    @Test void changedRetainedReceiptRevokesAFormerlyValidSource() {
        var f=task("service-1");verify(f,request(f,"fixture-1"));var recorded=service.recordObservation(input(f));
        jdbc.update("UPDATE ai_ops_tool_result SET full_output='{}',output_hash=? WHERE result_id=?",hash("{}"),f.result());
        assertFalse(acceptance.verifiedSuccess(project,f.run()).matches(project,f.run()));
        assertEquals(0,experiences.clusterEvidence(project,"fixture-agent",recorded.observation().clusterKey()).distinctRuns());
    }
    @Test void reviewerThresholdChangesCannotInventDistinctOperatingConditions() {
        var a=task("service-1");var b=task("service-1");String cluster="";
        for(var f:List.of(a,b)) {
            var checks=new TaskAcceptanceRequest("threshold-"+UUID.randomUUID(),f.revision(),"Synthetic latency threshold check",List.of(
                    new TaskAcceptanceRequest.Criterion(f.result(),f.hash(),"/latency","LE",f==a?1:2)));
            verify(f,checks);cluster=service.recordObservation(input(f)).observation().clusterKey();
        }
        assertEquals(2,experiences.clusterEvidence(project,"fixture-agent",cluster).distinctRuns());
        assertEquals(1,experiences.clusterEvidence(project,"fixture-agent",cluster).distinctConditions());
    }
    @Test void missingRunAndForgedLedgerIdentityFailClosed() {
        var f=task("service-1");jdbc.update("DELETE FROM ai_ops_agent_run WHERE run_id=?",f.run());
        assertThrows(IllegalStateException.class,()->verify(f,request(f,"fixture-1")));
        jdbc.update("INSERT INTO ai_ops_agent_run(run_id,project_id,session_id,status) VALUES (?,?,?,'SUCCEEDED')",f.run(),project,f.session());
        var saved=verify(f,request(f,"fixture-1"));var record=new LinkedHashMap<>(saved);record.put("sourceRunId","another-run");
        String changed=cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringify(record);
        jdbc.update("UPDATE ai_ops_task_acceptance SET record_json=?,record_hash=? WHERE acceptance_id=?",changed,hash(changed),saved.get("acceptanceId"));
        assertFalse(acceptance.verifiedSuccess(project,f.run()).matches(project,f.run()));
    }
    @Test void incidentIdentityFollowsEarlierTurnsWhenVerificationRunsAreSeparate() {
        var a=task("service-1");var b=task("service-1");
        String incident="i-"+UUID.randomUUID();jdbc.update("INSERT INTO ai_ops_incident VALUES (?,?)",incident,project);
        jdbc.update("INSERT INTO ai_ops_incident_run VALUES (?,?),(?,?)",incident,a.run(),incident,b.run());
        String cluster="";
        for(var earlier:List.of(a,b)) {
            var verification=turn(earlier.session(),3,earlier.episode(),"service-1");
            verify(verification,request(verification,"fixture-1"));
            cluster=service.recordObservation(input(verification)).observation().clusterKey();
        }
        assertEquals(1,experiences.clusterEvidence(project,"fixture-agent",cluster).distinctRuns());
    }
}
