package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.episode.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static cn.lgs.orbisops.infrastructure.adapter.repository.TaskEpisodeJson.*;

/** Synthetic task turns in disposable real MySQL. No fixture is a verified production success. */
@Testcontainers(disabledWithoutDocker = true)
class TaskEpisodeMySqlTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("task_episodes").withUsername("fixture").withPassword("fixture");
    private JdbcTemplate jdbc;
    private DefaultListableBeanFactory beans;
    private String project, session;
    private final MutableClock clock = new MutableClock(Instant.parse("2026-09-09T15:59:59Z").toEpochMilli());
    @BeforeEach void setup() throws Exception {
        var ds = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(ds); beans = new DefaultListableBeanFactory();
        beans.registerSingleton("mysqlJdbcTemplate", jdbc); beans.registerSingleton("mysqlTransactionManager", new DataSourceTransactionManager(ds));
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("db/migrations/sql/ops-task-episodes.sql"))) root = root.getParent();
        assertNotNull(root);
        try (var connection = ds.getConnection()) { ScriptUtils.executeSqlScript(connection, new FileSystemResource(root.resolve("db/migrations/sql/ops-task-episodes.sql"))); }
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_ops_chat_session(session_id VARCHAR(80) PRIMARY KEY, project_id VARCHAR(128),user_id VARCHAR(80))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_ops_chat_message(id BIGINT AUTO_INCREMENT PRIMARY KEY,project_id VARCHAR(128),session_id VARCHAR(80),"
                + "turn_id VARCHAR(80),message_seq BIGINT,role VARCHAR(32),content TEXT,metadata TEXT,create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, UNIQUE(session_id,message_seq))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_ops_agent_run(run_id VARCHAR(80) PRIMARY KEY,project_id VARCHAR(128),session_id VARCHAR(80),status VARCHAR(40))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_ops_tool_result(id BIGINT AUTO_INCREMENT PRIMARY KEY,project_id VARCHAR(128),run_id VARCHAR(80),result_id VARCHAR(80),tool_name VARCHAR(80),status VARCHAR(40),output_hash VARCHAR(64))");
        jdbc.execute("CREATE TABLE IF NOT EXISTS ai_ops_runtime_context_bundle(id BIGINT AUTO_INCREMENT PRIMARY KEY,project_id VARCHAR(128),session_id VARCHAR(80),run_id VARCHAR(80),bundle_id VARCHAR(80),bundle_hash VARCHAR(64),bundle_json TEXT)");
        project = "p-" + UUID.randomUUID(); session = "s-" + UUID.randomUUID();
        jdbc.update("INSERT INTO ai_ops_chat_session VALUES (?,?,'owner')", session, project);
    }
    private JdbcTaskEpisodeStore store() { return new JdbcTaskEpisodeStore(beans.getBeanProvider(JdbcTemplate.class), beans.getBeanProvider(PlatformTransactionManager.class)); }
    private String turn(long seq, String query, String reply, String status) {
        String run = "r-" + UUID.randomUUID();
        jdbc.update("INSERT INTO ai_ops_agent_run VALUES (?,?,?,?)", run, project, session, status);
        jdbc.update("INSERT INTO ai_ops_chat_message(project_id,session_id,turn_id,message_seq,role,content,metadata) VALUES (?,?,?,?,'user',?,'{}')", project, session, run, seq, query);
        if (reply != null) jdbc.update("INSERT INTO ai_ops_chat_message(project_id,session_id,turn_id,message_seq,role,content,metadata) VALUES (?,?,?,?,'assistant',?,?)",
                project, session, run, seq + 1, reply, "WAITING_APPROVAL".equals(status) ? "{\"status\":\"WAITING_APPROVAL\"}" : "{}");
        store().captureContext(project, session, run, Map.of("originalUserQuery", query, "memoryContext", "original context before turn " + seq));
        store().discover(1000); return run;
    }
    private TaskEpisodeStore.Claim claim() { return store().claimTurn(session, "fixture-worker", clock.millis(), true).orElseThrow(); }
    private String create() {
        var claim = claim(); assertTrue(store().assign(claim, new TaskEpisodeModelPort.Decision("CREATE", "", "用户目标", "synthetic fixture"), clock.millis()));
        return jdbc.queryForObject("SELECT episode_id FROM ai_ops_task_episode_turn WHERE id=?", String.class, claim.id());
    }
    private void continues(String episode) {
        assertTrue(store().assign(claim(), new TaskEpisodeModelPort.Decision("CONTINUE", episode, "", "synthetic continuation"), clock.millis()));
    }
    private Map<String,Object> state() { return jdbc.queryForMap("SELECT * FROM ai_ops_task_episode_session WHERE session_id=?", session); }
    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE episode_id IN (SELECT episode_id FROM ai_ops_task_episode WHERE session_id=?)", Long.class, session); }

    @Test void wholeTurnsContinueSwitchAndLateCorrectionReturnsToOldTaskWithoutChangingActiveTopicOrChat() {
        turn(1, "定位延迟并提出修复方案", "读取到延迟证据", "SUCCEEDED"); String first = create();
        turn(3, "继续提出方案", "提供准备步骤", "SUCCEEDED"); continues(first);
        turn(5, "另一个独立容量问题", "容量调查记录", "SUCCEEDED"); String second = create();
        turn(7, "纠正前面的延迟判断", "纠正仍待验证", "SUCCEEDED");
        String before = json(jdbc.queryForList("SELECT * FROM ai_ops_chat_message WHERE session_id=? ORDER BY message_seq", session));
        continues(first);
        assertEquals(second, state().get("active_episode_id"));
        assertEquals(7L, number(state().get("assigned_through_seq")));
        assertEquals(3L, jdbc.queryForObject("SELECT revision FROM ai_ops_task_episode WHERE episode_id=?", Long.class, first));
        assertEquals(List.of(first, first, second, first), jdbc.queryForList("SELECT episode_id FROM ai_ops_task_episode_turn WHERE session_id=? ORDER BY turn_seq", String.class, session));
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(DISTINCT source_run_ref) FROM ai_ops_task_episode_turn WHERE session_id=?", Integer.class, session));
        assertEquals(before, json(jdbc.queryForList("SELECT * FROM ai_ops_chat_message WHERE session_id=? ORDER BY message_seq", session)));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_task_episode WHERE session_id=? AND (outcome<>'UNKNOWN' OR verified_outcome_ref<>'')", Integer.class, session));
        assertThrows(SecurityException.class, () -> store().view(project, session, "another-user", false, 100));
        assertEquals(4, ((List<?>)store().view(project, session, "owner", false, 100).get("turns")).size());
    }

    @Test void incompleteTurnApprovalOrActiveToolBlocksLaterClassificationAndRestartDoesNotSkipIt() {
        String run = turn(1, "等待审批", "请审批", "WAITING_APPROVAL");
        turn(4, "后到的任务", "已经回复", "SUCCEEDED");
        assertTrue(store().claimTurn(session, "first", clock.millis(), true).isEmpty());
        jdbc.update("UPDATE ai_ops_agent_run SET status='SUCCEEDED' WHERE run_id=?", run);
        clock.advance(6000);
        assertTrue(store().claimTurn(session, "restarted", clock.millis(), true).isEmpty()); // Approval text is not final reply.
        jdbc.update("INSERT INTO ai_ops_chat_message(project_id,session_id,turn_id,message_seq,role,content,metadata) VALUES (?,?,?,3,'assistant','实际执行结果','{}')", project, session, run);
        jdbc.update("INSERT INTO ai_ops_tool_result(project_id,run_id,result_id,tool_name,status,output_hash) VALUES (?,?,?,'probe','RUNNING','')", project, run, "result-" + run);
        clock.advance(6000); assertTrue(store().claimTurn(session, "restarted", clock.millis(), true).isEmpty());
        jdbc.update("UPDATE ai_ops_tool_result SET status='SUCCEEDED' WHERE run_id=?", run);
        clock.advance(6000); var claim = claim();
        var source = object(claim.inputJson()); assertEquals(3, ((List<?>)source.get("turnMessages")).size());
        assertEquals(3L, number(source.get("endSeq")));
        assertFalse(claim.inputJson().contains("后到的任务"));
    }

    @Test void concurrentWorkersCanOnlyClaimTheHeadAndExpiredOwnerCannotCommit() throws Exception {
        turn(1, "same", "reply", "SUCCEEDED"); turn(3, "later", "reply", "SUCCEEDED");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<TaskEpisodeStore.Claim> winners = new CopyOnWriteArrayList<>();
        try {
            var tasks = new ArrayList<Callable<Void>>();
            for (int i=0;i<8;i++) { String worker="w-"+i; tasks.add(() -> { store().claimTurn(session,worker,clock.millis(),true).ifPresent(winners::add); return null; }); }
            for (var future : pool.invokeAll(tasks)) future.get(10, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertEquals(1, winners.size()); var old = winners.get(0);
        clock.advance(61000);
        assertTrue(store().claimTurn(session, "competing-worker", clock.millis(), true).isEmpty(),
                "The full model budget must not expire the fenced worker lease");
        clock.advance(60000); var recovered = claim();
        assertEquals(old.id(), recovered.id()); assertEquals(2, recovered.attempts()); assertTrue(recovered.epoch() > old.epoch());
        var decision = new TaskEpisodeModelPort.Decision("CREATE", "", "goal", "fixture");
        assertFalse(store().assign(old, decision, clock.millis())); assertTrue(store().assign(recovered, decision, clock.millis()));
        assertFalse(store().assign(recovered, decision, clock.millis()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_task_episode WHERE session_id=?", Integer.class, session));
        assertNotEquals(old.id(), claim().id());
    }

    @Test void modelUnavailablePersistsFrozenPendingInputWithoutSpendingAttemptsOrInventingAnEpisode() {
        String run = turn(1, "model missing", "main completed", "SUCCEEDED");
        AtomicInteger calls = new AtomicInteger();
        TaskEpisodeModelPort model = model(false, input -> { calls.incrementAndGet(); return null; });
        try (var service = new TaskEpisodeApplicationService(store(), model, clock)) { service.replay(32); clock.advance(61000); service.replay(32); }
        var row = jdbc.queryForMap("SELECT * FROM ai_ops_task_episode_turn WHERE source_run_ref=?", run);
        assertEquals("WAITING_MODEL", row.get("status")); assertEquals(0L, number(row.get("attempts"))); assertEquals(0, calls.get());
        assertEquals("FROZEN_MAIN_CONTEXT", row.get("context_fidelity")); assertEquals(hash(text(row.get("input_json"))), row.get("input_hash"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_task_episode WHERE session_id=?", Integer.class, session));
    }

    @Test void timeoutRemainsPendingBeyondFiveAttemptsWithoutAdvancingPastUnknownClassification() {
        turn(1, "slow model", "main reply", "SUCCEEDED"); turn(3, "later task", "main reply", "SUCCEEDED");
        AtomicInteger calls = new AtomicInteger();
        TaskEpisodeModelPort model = model(true, input -> {
            calls.incrementAndGet(); try { Thread.sleep(10000); } catch (InterruptedException expected) { Thread.currentThread().interrupt(); }
            return new TaskEpisodeModelPort.Decision("CREATE", "", "late", "must not commit");
        });
        // Only this project's head is targeted, avoiding unrelated fixtures in the shared disposable container.
        var filtering = new DelegatingStore(store()) {
            @Override public List<String> pendingSessions(long now,int limit) { return List.of(session); }
            @Override public List<Long> pendingConsolidations(long now,int limit) { return List.of(); }
        };
        try (var service = new TaskEpisodeApplicationService(filtering, model, clock, 50)) {
            for (int attempt=0;attempt<6;attempt++) { service.replay(1); clock.advance(3600000); }
        }
        assertEquals(6, calls.get());
        var states = jdbc.queryForList("SELECT status,attempts FROM ai_ops_task_episode_turn WHERE session_id=? ORDER BY turn_seq", session);
        assertEquals("PENDING_RETRY", states.get(0).get("status")); assertEquals("WAITING_TURN", states.get(1).get("status"));
        assertEquals(0L, number(states.get(1).get("attempts")));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_task_episode WHERE session_id=?", Integer.class, session));
    }

    @Test void midnightRespectsInactivityAndManualBoundaryDeduplicateWithoutLateOverwrite() {
        turn(1,"first task","progress","SUCCEEDED"); String episode=create();
        clock.advance(1000);
        assertEquals(0, Instant.ofEpochMilli(clock.millis()).atZone(ZoneId.of("Asia/Shanghai")).getHour());
        assertEquals(0,store().sweep(project,"MIDNIGHT",clock.millis(),100));
        assertEquals(1,store().sweep(project,"MANUAL",clock.millis(),100));
        long id=jdbc.queryForObject("SELECT id FROM ai_ops_episode_consolidation_job WHERE episode_id=?",Long.class,episode);
        var old=store().claimConsolidation(id,"artifact-owner",clock.millis(),true).orElseThrow();
        turn(3,"continue","new observation","SUCCEEDED"); continues(episode);
        assertTrue(store().complete(old,"Synthetic progress v1; outcome remains unknown",clock.millis()));
        assertEquals(0L,jdbc.queryForObject("SELECT last_consolidated_revision FROM ai_ops_task_episode WHERE episode_id=?",Long.class,episode));
        assertFalse(store().complete(old,"duplicate",clock.millis()));
        turn(5,"new task","new topic","SUCCEEDED"); create();
        assertEquals(2,count("ai_ops_episode_consolidation_job")); // v1 explicit analysis; v2 update + boundary share identity.
        var v2=jdbc.queryForMap("SELECT * FROM ai_ops_episode_consolidation_job WHERE episode_id=? AND revision=2",episode);
        assertTrue(text(v2.get("trigger_reasons")).contains("NEW_REVISION")); assertTrue(text(v2.get("trigger_reasons")).contains("BOUNDARY"));
        var current=store().claimConsolidation(number(v2.get("id")),"artifact-owner",clock.millis(),true).orElseThrow();
        assertTrue(store().complete(current,"Synthetic progress v2; no verified success",clock.millis()));
        assertEquals(2L,jdbc.queryForObject("SELECT last_consolidated_revision FROM ai_ops_task_episode WHERE episode_id=?",Long.class,episode));
        assertEquals(2,count("ai_ops_episode_artifact"));
    }

    @Test void expiredFifthAttemptPersistsCooldownBeforeRestartCanDispatchAgain() {
        turn(1,"crash repeatedly","reply","SUCCEEDED");
        for (int i=0;i<5;i++) { claim(); clock.advance(121000); }
        assertTrue(store().claimTurn(session,"third",clock.millis(),true).isEmpty());
        assertEquals("PENDING_RETRY",jdbc.queryForObject("SELECT status FROM ai_ops_task_episode_turn WHERE session_id=?",String.class,session));
        assertEquals(5,jdbc.queryForObject("SELECT attempts FROM ai_ops_task_episode_turn WHERE session_id=?",Integer.class,session));
        clock.advance(479999);
        assertTrue(store().claimTurn(session,"restart",clock.millis(),true).isEmpty());
        clock.advance(1);
        assertEquals(6, claim().attempts());
    }

    @Test void temporaryFailureResumesAfterLongOutageWithFrozenInputAndCappedBackoff() {
        turn(1,"temporary outage","reply","SUCCEEDED");
        var first = claim();
        store().fail(first,"EPISODE_MODEL_TIMEOUT",false,clock.millis());
        // Legacy terminal state from the previous retry policy, in this disposable fixture only.
        jdbc.update("UPDATE ai_ops_task_episode_turn SET status='RETRY_EXHAUSTED',attempts=5 WHERE id=?",first.id());
        clock.advance(480000);
        assertTrue(store().pendingSessions(clock.millis(),1000).contains(session));
        var resumed=claim(); assertEquals(6,resumed.attempts()); assertEquals(first.inputHash(),resumed.inputHash());
        for(int attempt=6;attempt<=10;attempt++) {
            assertEquals(attempt,resumed.attempts());
            long delay=Math.min(3600000L,30000L << Math.min(7,attempt-1));
            store().fail(resumed,"EPISODE_MODEL_RATE_LIMITED",false,clock.millis());
            assertEquals(clock.millis()+delay,jdbc.queryForObject("SELECT next_attempt_ms FROM ai_ops_task_episode_turn WHERE id=?",Long.class,first.id()));
            clock.advance(delay-1);assertTrue(store().claimTurn(session,"too-early",clock.millis(),true).isEmpty());
            clock.advance(1);resumed=claim();
        }
        assertEquals(11,resumed.attempts());
        assertTrue(store().assign(resumed,new TaskEpisodeModelPort.Decision("CREATE","","recovered","fixture"),clock.millis()));
        assertFalse(store().assign(first,new TaskEpisodeModelPort.Decision("CREATE","","late","fixture"),clock.millis()));
    }

    @Test void invalidResultsStillStopAtFiveRatherThanRetryingAContractViolationForever() {
        turn(1,"invalid result","reply","SUCCEEDED");
        for(int i=0;i<5;i++) {var c=claim();store().fail(c,"EPISODE_MODEL_FAILED",false,clock.millis());clock.advance(3600000);}
        assertFalse(store().pendingSessions(clock.millis(),1000).contains(session));
        assertTrue(store().claimTurn(session,"restart",clock.millis(),true).isEmpty());
        assertEquals("RETRY_EXHAUSTED",jdbc.queryForObject("SELECT status FROM ai_ops_task_episode_turn WHERE session_id=?",String.class,session));
    }

    @Test void consolidationAlsoKeepsTransientFailuresDurableAfterFiveAttempts() {
        turn(1,"progress","reply","SUCCEEDED");String episode=create();
        store().sweep(project,"MANUAL",clock.millis(),100);
        long id=jdbc.queryForObject("SELECT id FROM ai_ops_episode_consolidation_job WHERE episode_id=?",Long.class,episode);
        TaskEpisodeStore.Claim previous=null;
        for(int i=0;i<6;i++) {
            var current=store().claimConsolidation(id,"worker-"+i,clock.millis(),true).orElseThrow();
            if(previous!=null) assertEquals(previous.inputHash(),current.inputHash());
            previous=current; store().fail(current,"EPISODE_MODEL_TIMEOUT",false,clock.millis());
            clock.advance(3600000);assertTrue(store().pendingConsolidations(clock.millis(),1000).contains(id));
        }
        var recovered=store().claimConsolidation(id,"restarted",clock.millis(),true).orElseThrow();
        assertEquals(7,recovered.attempts());
        assertTrue(store().complete(recovered,"Synthetic progress; outcome remains unknown",clock.millis()));
        assertEquals(1,count("ai_ops_episode_artifact"));
    }

    @Test void failedCrossScopeClassificationRollsBackAndSnapshotRemainsTheOriginalOnResume() {
        String run=turn(1,"original request","reply","SUCCEEDED");
        store().captureContext(project,session,run,Map.of("memoryContext","future content that must not replace initial context"));
        var claim=claim(); assertFalse(claim.inputJson().contains("future content"));
        assertThrows(IllegalArgumentException.class,()->store().assign(claim,new TaskEpisodeModelPort.Decision("CONTINUE","other-project-episode","","invalid"),clock.millis()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_task_episode WHERE session_id=?",Integer.class,session));
        assertEquals(0L,number(state().get("assigned_through_seq")));
        assertTrue(store().assign(claim,new TaskEpisodeModelPort.Decision("CREATE","","valid","fixture"),clock.millis()));
    }

    @Test void legacyHistoryIsMarkedReconstructedAndCannotIncludeFutureTurns() {
        String run=turn(1,"legacy query","legacy reply","SUCCEEDED");
        // This deletes only the new capture fixture in disposable MySQL to emulate a pre-migration run.
        jdbc.update("DELETE FROM ai_ops_task_episode_context WHERE run_id=?",run);
        turn(3,"future secret topic","future reply","SUCCEEDED"); var claim=claim();
        assertEquals("RECONSTRUCTED_RETAINED_CONTEXT",object(claim.inputJson()).get("contextFidelity"));
        assertFalse(claim.inputJson().contains("future secret topic"));
    }

    @Test void changedFrozenInputIsQuarantinedInsteadOfBeingSubmittedToAModel() {
        String run=turn(1,"immutable question","reply","SUCCEEDED");
        var pending=store().claimTurn(session,"offline",clock.millis(),false).orElseThrow();
        store().fail(pending,"EPISODE_MODEL_UNAVAILABLE",true,clock.millis());
        jdbc.update("UPDATE ai_ops_task_episode_turn SET input_json='{}' WHERE source_run_ref=?",run);
        clock.advance(61000); assertTrue(store().claimTurn(session,"after-corruption",clock.millis(),true).isEmpty());
        var row=jdbc.queryForMap("SELECT status,last_error,attempts FROM ai_ops_task_episode_turn WHERE source_run_ref=?",run);
        assertEquals("SOURCE_BLOCKED",row.get("status"));assertEquals("EPISODE_INPUT_HASH_MISMATCH",row.get("last_error"));assertEquals(0L,number(row.get("attempts")));
    }

    @Test void concurrentDiscoveryKeepsExactlyOneTurnPerDurableSource() throws Exception {
        for(int n=1;n<=21;n+=2) {
            String run="r-"+UUID.randomUUID();
            jdbc.update("INSERT INTO ai_ops_agent_run VALUES (?,?,?,'SUCCEEDED')",run,project,session);
            jdbc.update("INSERT INTO ai_ops_chat_message(project_id,session_id,turn_id,message_seq,role,content,metadata) VALUES (?,?,?,?,'user','scanner fixture','{}')",project,session,run,n);
        }
        ExecutorService pool=Executors.newFixedThreadPool(4);
        try {
            List<Callable<Integer>> work=new ArrayList<>();for(int n=0;n<4;n++)work.add(()->store().discover(1000));
            for(var future:pool.invokeAll(work)) future.get(10,TimeUnit.SECONDS);
        } finally {pool.shutdownNow();}
        assertEquals(11,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_task_episode_turn WHERE session_id=?",Integer.class,session));
    }

    @Test void processFailureAfterAssignmentWritesRollsBackEpisodeTurnAndCursorTogether() {
        turn(1,"atomic assignment","reply","SUCCEEDED");var claim=claim();AtomicBoolean fail=new AtomicBoolean(true);
        JdbcTemplate faulty=new JdbcTemplate(Objects.requireNonNull(jdbc.getDataSource())) {
            @Override public int update(String statement,Object... args) {
                int changed=super.update(statement,args);
                if(statement.startsWith("UPDATE ai_ops_task_episode_session SET assigned_through_seq") && fail.getAndSet(false))
                    throw new IllegalStateException("simulated-process-loss-after-writes-before-commit");
                return changed;
            }
        };
        beans.destroySingleton("mysqlJdbcTemplate");beans.registerSingleton("mysqlJdbcTemplate",faulty);
        var decision=new TaskEpisodeModelPort.Decision("CREATE","","atomic goal","fixture");
        assertThrows(IllegalStateException.class,()->store().assign(claim,decision,clock.millis()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_task_episode WHERE session_id=?",Integer.class,session));
        assertEquals(0L,number(state().get("assigned_through_seq")));assertEquals("",state().get("active_episode_id"));
        assertEquals("RUNNING",jdbc.queryForObject("SELECT status FROM ai_ops_task_episode_turn WHERE id=?",String.class,claim.id()));
        assertTrue(store().assign(claim,decision,clock.millis()));
    }

    @Test void activeEpisodeAndPendingApprovalYieldOnlyProgressOfTheAssignedPrefix() {
        clock.millis.set(Instant.now().toEpochMilli());
        turn(1,"prepare with approval","preparation evidence","SUCCEEDED");String episode=create();
        turn(3,"execute after review","waiting approval","WAITING_APPROVAL");
        store().sweep(project,"MANUAL",clock.millis(),100);
        var job=jdbc.queryForMap("SELECT artifact_type,input_json FROM ai_ops_episode_consolidation_job WHERE episode_id=?",episode);
        var input=object(job.get("input_json"));assertEquals("PROGRESS",job.get("artifact_type"));
        assertEquals(true,input.get("recentlyActive"));assertEquals(1L,number(input.get("sessionPendingTurns")));
        assertEquals(1,((List<?>)input.get("turns")).size());assertEquals("UNKNOWN",input.get("outcome"));
        assertEquals("UNKNOWN",jdbc.queryForObject("SELECT outcome FROM ai_ops_task_episode WHERE episode_id=?",String.class,episode));
    }

    @Test void waitingSessionsCannotStarveANewCompletedSessionAcrossBoundedScans() {
        for(int i=0;i<20;i++) {
            session="aa-wait-"+UUID.randomUUID();jdbc.update("INSERT INTO ai_ops_chat_session VALUES (?,?,'owner')",session,project);
            turn(1,"waiting approval","approval request","WAITING_APPROVAL");
        }
        session="zz-ready-"+UUID.randomUUID();jdbc.update("INSERT INTO ai_ops_chat_session VALUES (?,?,'owner')",session,project);
        turn(1,"completed later session","read receipt","SUCCEEDED");boolean reached=false;
        for(int scan=0;scan<20 && !reached;scan++) {
            for(String pending:store().pendingSessions(clock.millis(),8)) {
                var selected=store().claimTurn(pending,"fair-worker",clock.millis(),false);
                if(selected.isPresent()) {
                    reached |= pending.equals(session);
                    store().fail(selected.get(),"EPISODE_MODEL_UNAVAILABLE",true,clock.millis());
                }
            }
            clock.advance(5000);
        }
        assertTrue(reached,"old approval waits must not monopolize the bounded scan");
    }

    @Test void inactivityRequiresFullDayAndNewMessagesResetTheDeadline() {
        clock.millis.set(Instant.now().toEpochMilli());
        turn(1,"idle task","reply","SUCCEEDED"); String episode=create();
        long activity=jdbc.queryForObject("SELECT last_activity_ms FROM ai_ops_task_episode WHERE episode_id=?",Long.class,episode);
        assertEquals(0,store().sweep(project,"IDLE",activity+86400000L-1,100));
        assertEquals(0,count("ai_ops_episode_consolidation_job"));
        assertEquals(1,store().sweep(project,"IDLE",activity+86400000L,100));
        assertEquals(0,store().sweep(project,"RECOVERY",activity+86400000L,100));
        assertEquals(1,count("ai_ops_episode_consolidation_job"));
        assertEquals("UNKNOWN",jdbc.queryForObject("SELECT outcome FROM ai_ops_task_episode WHERE episode_id=?",String.class,episode));
        clock.millis.set(activity+86400001L);
        turn(3,"continue same task","new reply","SUCCEEDED"); continues(episode);
        assertEquals(2L,jdbc.queryForObject("SELECT revision FROM ai_ops_task_episode WHERE episode_id=?",Long.class,episode));
        assertEquals(2,count("ai_ops_episode_consolidation_job"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_task_episode WHERE session_id=?",Integer.class,session));
    }

    @Test void unclassifiedFollowupPreventsAnOldEpisodeFromLookingIdle() {
        clock.millis.set(Instant.now().toEpochMilli());
        turn(1,"old task","reply","SUCCEEDED");String episode=create();
        long activity=jdbc.queryForObject("SELECT last_activity_ms FROM ai_ops_task_episode WHERE episode_id=?",Long.class,episode);
        String newer=turn(3,"new question not yet finished",null,"RUNNING");
        jdbc.update("UPDATE ai_ops_chat_message SET create_time=? WHERE turn_id=?",new java.sql.Timestamp(activity+86399000L),newer);
        assertEquals(0,store().sweep(project,"IDLE",activity+86400000L,100));
        assertEquals(0,count("ai_ops_episode_consolidation_job"));
    }

    @Test void oldTwoAttemptExhaustionResumesWithoutResettingCountOrFrozenInput() {
        turn(1,"transient model failure","reply","SUCCEEDED");
        var first=claim();store().fail(first,"EPISODE_MODEL_FAILED",false,clock.millis());
        jdbc.update("UPDATE ai_ops_task_episode_turn SET status='RETRY_EXHAUSTED',attempts=2 WHERE id=?",first.id());
        clock.advance(300000);
        assertTrue(store().pendingSessions(clock.millis(),1000).contains(session));
        var resumed=claim();assertEquals(3,resumed.attempts());assertEquals(first.inputHash(),resumed.inputHash());
        store().fail(resumed,"EPISODE_MODEL_FAILED",false,clock.millis());
        assertTrue(store().claimTurn(session,"too-early",clock.millis(),true).isEmpty());
        clock.advance(300000);var fourth=claim();assertEquals(4,fourth.attempts());
        store().fail(fourth,"EPISODE_MODEL_FAILED",false,clock.millis());
        clock.advance(300000);var fifth=claim();assertEquals(5,fifth.attempts());
        assertTrue(store().assign(fifth,new TaskEpisodeModelPort.Decision("CREATE","","recovered","fixture"),clock.millis()));
        assertEquals("ASSIGNED",jdbc.queryForObject("SELECT status FROM ai_ops_task_episode_turn WHERE id=?",String.class,first.id()));
    }

    @Test void exponentialDueTimeAndProviderCooldownPersistAcrossWorkers() {
        turn(1,"backoff","reply","SUCCEEDED");
        for(int attempt=1;attempt<=4;attempt++) {
            var claim=store().claimTurn(session,"worker-"+attempt,clock.millis(),true).orElseThrow();
            long before=clock.millis();long expected=30000L << (attempt-1);
            store().fail(claim,"EPISODE_MODEL_RATE_LIMITED",false,before,attempt==2 ? 180000 : 0);
            if(attempt==2) expected=180000;
            assertEquals(before+expected,jdbc.queryForObject("SELECT next_attempt_ms FROM ai_ops_task_episode_turn WHERE id=?",Long.class,claim.id()));
            clock.advance(expected-1);assertTrue(store().claimTurn(session,"restart",clock.millis(),true).isEmpty());
            clock.advance(1);
        }
        assertEquals(5,claim().attempts());
    }

    private TaskEpisodeModelPort model(boolean available, java.util.function.Function<String,TaskEpisodeModelPort.Decision> classify) {
        return new TaskEpisodeModelPort() {
            public boolean available() { return available; }
            public Decision classify(String input) { return classify.apply(input); }
            public String consolidate(String input) { return "Synthetic progress fixture; unknown outcome"; }
        };
    }
    static class MutableClock extends Clock {
        final AtomicLong millis;
        MutableClock(long value) { millis=new AtomicLong(value); }
        void advance(long value) { millis.addAndGet(value); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(millis()); }
        public long millis() { return millis.get(); }
    }
    static class DelegatingStore implements TaskEpisodeStore {
        final TaskEpisodeStore delegate;
        DelegatingStore(TaskEpisodeStore delegate) { this.delegate=delegate; }
        public void captureContext(String p,String s,String r,Map<String,Object> v){delegate.captureContext(p,s,r,v);}
        public int discover(int limit){return delegate.discover(limit);}
        public List<String> pendingSessions(long now,int limit){return delegate.pendingSessions(now,limit);}
        public Optional<Claim> claimTurn(String s,String o,long n,boolean a){return delegate.claimTurn(s,o,n,a);}
        public boolean assign(Claim c,TaskEpisodeModelPort.Decision d,long n){return delegate.assign(c,d,n);}
        public void fail(Claim c,String r,boolean u,long n){delegate.fail(c,r,u,n);}
        public int sweep(String p,String r,long n,int l){return delegate.sweep(p,r,n,l);}
        public List<Long> pendingConsolidations(long n,int l){return delegate.pendingConsolidations(n,l);}
        public Optional<Claim> claimConsolidation(long id,String o,long n,boolean a){return delegate.claimConsolidation(id,o,n,a);}
        public boolean complete(Claim c,String v,long n){return delegate.complete(c,v,n);}
        public Map<String,Object> view(String p,String s,String u,boolean a,int l){return delegate.view(p,s,u,a,l);}
    }
}
