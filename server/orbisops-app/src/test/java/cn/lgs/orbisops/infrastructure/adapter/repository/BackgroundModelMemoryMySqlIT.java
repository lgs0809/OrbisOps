package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.memory.*;
import cn.lgs.orbisops.domain.memory.model.*;
import cn.lgs.orbisops.domain.memory.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Actual MySQL on the existing isolated fixture, with task facts retained. No model endpoint is called. */
@EnabledIfEnvironmentVariable(named="ORBISOPS_DISCOVERY_TEST_JDBC_URL",matches=".+orbisops_mcp_counting_background_.+")
class BackgroundModelMemoryMySqlIT {
    JdbcTemplate jdbc;JdbcConversationMemoryRepository repo;String session,project;
    @BeforeEach void setup() {
        jdbc=new JdbcTemplate(OrbisOpsExternalMySqlFixture.dataSource());
        var beans=new DefaultListableBeanFactory();beans.registerSingleton("mysqlJdbcTemplate",jdbc);
        repo=new JdbcConversationMemoryRepository(beans.getBeanProvider(JdbcTemplate.class));
        session="retry-memory-"+UUID.randomUUID();project="retry-project-"+UUID.randomUUID();
        jdbc.update("INSERT INTO ai_ops_chat_session(session_id,user_id,project_id) VALUES (?,'fixture-owner',?)",session,project);
    }
    private ColdMemoryMessageSnapshot message(int index) {
        return new ColdMemoryMessageSnapshot(session,"fixture-owner","user",index==1?"不要重启，仅允许只读核对":"synthetic input "+index,
                "2026-11-03 00:00:00",Map.of("projectId",project,"turnId","synthetic-turn-"+index));
    }
    private long task(String type) {return jdbc.queryForObject("SELECT id FROM ai_ops_memory_post_processing WHERE session_id=? AND task_type=? ORDER BY id LIMIT 1",Long.class,session,type);}
    private MemoryCompressionApplicationService compression(MemoryModelSummaryPort model) {
        return new MemoryCompressionApplicationService(new MemoryCompressionPolicy(new MemoryContentHashPolicy()),model,repo,()->"now");
    }
    @Test void retriesBeyondEightPreserveNextTimeAndFencePriorWorkerAfterReconstruction() {
        repo.capture(message(1),24);long id=task("EXTRACTION"),now=System.currentTimeMillis();MemoryProcessingJob first=null;
        for(int i=1;i<=12;i++) {
            var job=repo.claim(id,"worker-"+i,now,300000).orElseThrow();if(first==null)first=job;
            assertEquals(i,job.attempts());assertTrue(repo.retry(job,"SYNTHETIC_MODEL_FAILURE",now));
            long due=jdbc.queryForObject("SELECT next_attempt_ms FROM ai_ops_memory_post_processing WHERE id=?",Long.class,id);
            assertTrue(due>now && due-now<=3600000);assertTrue(repo.claim(id,"too-early",due-1,300000).isEmpty());
            var beans=new DefaultListableBeanFactory();beans.registerSingleton("mysqlJdbcTemplate",jdbc);
            repo=new JdbcConversationMemoryRepository(beans.getBeanProvider(JdbcTemplate.class));now=due;
        }
        var current=repo.claim(id,"worker-13",now,300000).orElseThrow();assertEquals(13,current.attempts());
        var effects=new AtomicInteger();assertFalse(repo.complete(first,"LATE",now,effects::incrementAndGet));assertEquals(0,effects.get());
        assertTrue(repo.complete(current,"VERIFIED_NO_FACTS",now,effects::incrementAndGet));assertEquals(1,effects.get());
        assertTrue(repo.pending(session,100,now+86400000).stream().noneMatch(x->x==id));
    }
    @Test void ruleProjectionAndDeferredExtractionCommitAtomicallyAndDoNotBecomeCompleted() {
        repo.capture(message(1),24);long id=task("EXTRACTION"),now=System.currentTimeMillis();var job=repo.claim(id,"one",now,300000).orElseThrow();
        jdbc.execute("CREATE TABLE IF NOT EXISTS background_retry_projection (task_id BIGINT PRIMARY KEY, projections INT NOT NULL)");
        assertThrows(IllegalStateException.class,()->repo.defer(job,"RULE_FALLBACK_MODEL_PENDING","MODEL_DEFERRED",now,()->{
            jdbc.update("INSERT INTO background_retry_projection VALUES (?,1)",id);throw new IllegalStateException("projection failed");
        }));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM background_retry_projection WHERE task_id=?",Integer.class,id));
        assertEquals("RUNNING",jdbc.queryForObject("SELECT status FROM ai_ops_memory_post_processing WHERE id=?",String.class,id));
        assertTrue(repo.defer(job,"RULE_FALLBACK_MODEL_PENDING","MODEL_DEFERRED",now,()->jdbc.update("INSERT INTO background_retry_projection VALUES (?,1)",id)));
        assertEquals("RETRY_WAIT",jdbc.queryForObject("SELECT status FROM ai_ops_memory_post_processing WHERE id=?",String.class,id));
        assertEquals("RULE_FALLBACK_MODEL_PENDING",jdbc.queryForObject("SELECT result_kind FROM ai_ops_memory_post_processing WHERE id=?",String.class,id));
        assertFalse(repo.defer(job,"changed","DUPLICATE",now,()->fail("stale worker must not project")));
    }
    @Test void enabledModelFailureRetainsRuleSummaryAndFrozenSupplementWhileDisabledModeCreatesNone() {
        for(int i=1;i<=4;i++)repo.capture(message(i),24);
        var calls=new AtomicInteger();var model=(MemoryModelSummaryPort)(sources,max)->{calls.incrementAndGet();throw new IllegalStateException("SYNTHETIC_NETWORK_FAILURE");};
        var service=compression(model);
        assertTrue(service.compress(new MemoryCompressionCommand(session,"fixture-owner",List.of(),4,2,true,4000,24)));
        assertEquals("context_compressor_model_pending",jdbc.queryForObject("SELECT source FROM ai_ops_memory_summary_version WHERE session_id=?",String.class,session));
        assertEquals(1,calls.get());long id=task("COMPRESSION_MODEL");long due=jdbc.queryForObject("SELECT next_attempt_ms FROM ai_ops_memory_post_processing WHERE id=?",Long.class,id);
        var stale=repo.claim(id,"old",due,1).orElseThrow();var input=MemorySummaryModelInput.parse(stale.modelInputJson());
        assertEquals(2,input.messages().size());assertTrue(input.messages().get(0).content().contains("不要重启"));
        assertThrows(IllegalStateException.class,()->service.prepareModelRetry(stale,false));assertEquals(1,calls.get());
        assertTrue(repo.claim(id,"recover-expired",due+2,300000).isEmpty());
        long resumedAt=jdbc.queryForObject("SELECT next_attempt_ms FROM ai_ops_memory_post_processing WHERE id=?",Long.class,id);
        assertTrue(resumedAt>due+2);
        var fresh=repo.claim(id,"restarted",resumedAt,300000).orElseThrow();var recovered=compression((sources,max)->"recovered model summary");
        var lateWrites=recovered.prepareModelRetry(stale,true);
        assertFalse(repo.complete(stale,"MODEL_SUMMARIZED",due+2,lateWrites));
        var writes=recovered.prepareModelRetry(fresh,true);assertTrue(repo.complete(fresh,"MODEL_SUMMARIZED",resumedAt,writes));
        assertEquals(2,jdbc.queryForObject("SELECT summary_revision FROM ai_ops_conversation_memory_state WHERE session_id=?",Integer.class,session));
        assertEquals("llm_context_compressor",jdbc.queryForObject("SELECT source FROM ai_ops_memory_summary_version WHERE session_id=? AND revision=2",String.class,session));
        assertTrue(repo.claim(id,"duplicate",due+86400000,300000).isEmpty());
        for(int i=5;i<=7;i++)repo.capture(message(i),24);
        long before=jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_memory_post_processing WHERE session_id=? AND task_type='COMPRESSION_MODEL'",Long.class,session);
        assertTrue(compression(model).compress(new MemoryCompressionCommand(session,"fixture-owner",List.of(),4,2,false,4000,24)));
        assertEquals(before,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_memory_post_processing WHERE session_id=? AND task_type='COMPRESSION_MODEL'",Long.class,session));
        assertEquals(1,calls.get());
    }
    @Test void changedFrozenSupplementIsBlockedBeforeAnyModelCall() {
        for(int i=1;i<=4;i++)repo.capture(message(i),24);
        compression((s,m)->{throw new IllegalStateException("synthetic");}).compress(new MemoryCompressionCommand(session,"fixture-owner",List.of(),4,2,true,4000,24));
        long id=task("COMPRESSION_MODEL");jdbc.update("UPDATE ai_ops_memory_post_processing SET model_input_json='{}' WHERE id=?",id);
        long due=jdbc.queryForObject("SELECT next_attempt_ms FROM ai_ops_memory_post_processing WHERE id=?",Long.class,id);
        assertTrue(repo.claim(id,"no-model",due,300000).isEmpty());
        assertEquals("SOURCE_BLOCKED",jdbc.queryForObject("SELECT status FROM ai_ops_memory_post_processing WHERE id=?",String.class,id));
    }    @Test void initialCompressionProjectionAndSupplementInsertionAreFencedByTheOriginalJobLease() {
        for(int i=1;i<=4;i++)repo.capture(message(i),24);
        long id=task("COMPRESSION"),now=System.currentTimeMillis();var old=repo.claim(id,"old",now,1).orElseThrow();
        var service=compression((sources,max)->{throw new IllegalStateException("SYNTHETIC_MODEL_OUTAGE");});
        var command=new MemoryCompressionCommand(session,"fixture-owner",List.of(),4,2,true,4000,24);
        var oldWrites=service.prepareCompression(command);
        assertEquals(0,jdbc.queryForObject("SELECT summary_revision FROM ai_ops_conversation_memory_state WHERE session_id=?",Integer.class,session));
        assertTrue(repo.claim(id,"expired-scan",now+2,300000).isEmpty());
        long due=jdbc.queryForObject("SELECT next_attempt_ms FROM ai_ops_memory_post_processing WHERE id=?",Long.class,id);
        var current=repo.claim(id,"current",due,300000).orElseThrow();
        assertFalse(repo.complete(old,"CHECKED",due,oldWrites));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_memory_summary_version WHERE session_id=?",Integer.class,session));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_memory_post_processing WHERE session_id=? AND task_type='COMPRESSION_MODEL'",Integer.class,session));
        assertTrue(repo.complete(current,"CHECKED",due,service.prepareCompression(command)));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_memory_summary_version WHERE session_id=?",Integer.class,session));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_memory_post_processing WHERE session_id=? AND task_type='COMPRESSION_MODEL'",Integer.class,session));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"FORMAT","SCOPE"})
    void evenAValidHashCannotMakeFixedInvalidModelInputRetryForeverOrRecover(String change) {
        for(int i=1;i<=4;i++)repo.capture(message(i),24);
        compression((sources,max)->{throw new IllegalStateException("synthetic outage");}).compress(new MemoryCompressionCommand(session,"fixture-owner",List.of(),4,2,true,4000,24));
        long id=task("COMPRESSION_MODEL");
        String prior=jdbc.queryForObject("SELECT model_input_json FROM ai_ops_memory_post_processing WHERE id=?",String.class,id);
        String invalid="FORMAT".equals(change)?"{}":prior.replace(session,"different-session");
        jdbc.update("UPDATE ai_ops_memory_post_processing SET model_input_json=?,model_input_hash=? WHERE id=?",invalid,cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher.sha256Text(invalid),id);
        long due=jdbc.queryForObject("SELECT next_attempt_ms FROM ai_ops_memory_post_processing WHERE id=?",Long.class,id);
        assertTrue(repo.claim(id,"no-model",due,300000).isEmpty());
        assertEquals("SOURCE_BLOCKED",jdbc.queryForObject("SELECT status FROM ai_ops_memory_post_processing WHERE id=?",String.class,id));
        // Recreate an old FAILED fixture, then exercise the normal recovery repository. It must stay stopped.
        jdbc.update("UPDATE ai_ops_memory_post_processing SET status='FAILED',attempts=9 WHERE id=?",id);
        var recovery=new JdbcBackgroundModelRecoveryRepository(jdbc);
        var candidate=recovery.candidates(cn.lgs.orbisops.application.episode.BackgroundModelRecoveryPort.Family.MEMORY,project,String.valueOf(id),"",1).get(0);
        assertFalse(recovery.restore(candidate,due+3600000,"fixture-owner",()->{}));
        assertEquals("FAILED",jdbc.queryForObject("SELECT status FROM ai_ops_memory_post_processing WHERE id=?",String.class,id));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_background_model_recovery_audit WHERE family='MEMORY' AND task_id=?",Integer.class,String.valueOf(id)));
    }

}
