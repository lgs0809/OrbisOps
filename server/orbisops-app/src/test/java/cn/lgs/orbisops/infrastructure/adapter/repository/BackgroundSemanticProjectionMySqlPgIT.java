package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.memory.*;
import cn.lgs.orbisops.application.project.*;
import cn.lgs.orbisops.application.security.BackgroundTaskOwnerAccessPolicy;
import cn.lgs.orbisops.domain.memory.model.*;
import cn.lgs.orbisops.domain.security.*;
import cn.lgs.orbisops.infrastructure.dao.IAdminUserDao;
import cn.lgs.orbisops.trigger.application.memory.OpsSemanticVectorRecallAdapter;
import cn.lgs.orbisops.trigger.application.memory.OpsSemanticVectorWriteAdapter;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.*;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

/** Real MySQL, PostgreSQL and Spring AI PgVectorStore; the deterministic embedding is not a real model success. */
@EnabledIfEnvironmentVariable(named="ORBISOPS_DISCOVERY_TEST_JDBC_URL",matches=".+orbisops_mcp_counting_background_.+")
class BackgroundSemanticProjectionMySqlPgIT {
    static final String TABLE="orbisops_retry_semantic_20261103";
    JdbcTemplate jdbc,pg;DefaultListableBeanFactory beans;JdbcConversationMemoryRepository repo;
    BackgroundTaskOwnerAccessPolicy owners;ProjectMemberApplicationService members;AdminUserRepository accounts;
    String session,project,owner;SemanticMemoryWriteApplicationService writer;VectorStore vectors;
    final AtomicBoolean outage=new AtomicBoolean(true);final AtomicInteger modelCalls=new AtomicInteger();
    @BeforeEach void setup() throws Exception {
        assertTrue(System.getenv("ORBISOPS_MEMORY_TEST_PG_URL").startsWith("jdbc:postgresql://127.0.0.1:15462/"));
        var ds=OrbisOpsExternalMySqlFixture.dataSource();jdbc=new JdbcTemplate(ds);
        pg=new JdbcTemplate(new DriverManagerDataSource(System.getenv("ORBISOPS_MEMORY_TEST_PG_URL"),System.getenv("ORBISOPS_MEMORY_TEST_PG_USER"),System.getenv("ORBISOPS_MEMORY_TEST_PG_PASSWORD")));
        beans=new DefaultListableBeanFactory();beans.registerSingleton("mysqlJdbcTemplate",jdbc);beans.registerSingleton("pg",pg);
        owner="semantic-u-"+UUID.randomUUID();session="semantic-s-"+UUID.randomUUID();project="semantic-p-"+UUID.randomUUID();
        var factory=new SqlSessionFactoryBean();factory.setDataSource(ds);
        factory.setMapperLocations(new ClassPathResource("mybatis/mapper/admin_user_mapper.xml"));
        accounts=new AdminUserRepository(new SqlSessionTemplate(factory.getObject()).getMapper(IAdminUserDao.class));
        assertTrue(accounts.insert(new AdminUserAccount(null,owner,owner,"SYNTHETIC_NOT_A_LOGIN_CREDENTIAL",AdminUserRole.USER,1,null,null)));
        jdbc.update("INSERT INTO ai_ops_project(project_id,name,owner) VALUES (?,?,'separate-project-owner')",project,"Synthetic semantic source");
        members=new ProjectMemberApplicationService(new JdbcProjectMemberRepository(provider(jdbc)));
        members.grantIfAbsent(project,owner,owner,"MEMBER","synthetic-fixture");
        var directory=new ProjectAccessPort() {
            public boolean exists(String id){return jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_project WHERE project_id=? AND enabled=1",Integer.class,id)==1;}
            public boolean owner(String id,String name,String user){return jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_project WHERE project_id=? AND owner IN (?,?)",Integer.class,id,name,user)>0;}
            public List<ProjectCatalogEntry> publicCatalog(){return List.of();}
        };
        owners=new BackgroundTaskOwnerAccessPolicy(new AuthorizeProjectAccessUseCase(directory,members),accounts);repo=restarted();
        jdbc.update("INSERT INTO ai_ops_chat_session(session_id,user_id,project_id) VALUES (?,?,?)",session,owner,project);
        EmbeddingModel controlled=new EmbeddingModel() {
            public EmbeddingResponse call(EmbeddingRequest request) {
                modelCalls.incrementAndGet();if(outage.get())throw new IllegalStateException("SYNTHETIC_EMBEDDING_OUTAGE");
                return new EmbeddingResponse(IntStream.range(0,request.getInstructions().size()).mapToObj(index->new Embedding(new float[]{1,0,0},index)).toList());
            }
            public float[] embed(Document document){return call(new EmbeddingRequest(List.of(document.getText()),null)).getResults().get(0).getOutput();}
        };
        var store=PgVectorStore.builder(pg,controlled).dimensions(3).vectorTableName(TABLE).initializeSchema(true).build();
        store.afterPropertiesSet();vectors=store;beans.registerSingleton("vectors",vectors);
        writer=SemanticMemoryWriteApplicationService.withDefaultPolicy(new OpsSemanticVectorWriteAdapter(beans.getBeanProvider(VectorStore.class)),
                new OpsSemanticLexicalWriteAdapter(provider(pg),TABLE),null);
        repo.capture(new ColdMemoryMessageSnapshot(session,owner,"user","durable semantic memory acceptance","now",Map.of("projectId",project,"turnId","initial")),24);
    }
    private org.springframework.beans.factory.ObjectProvider<JdbcTemplate> provider(JdbcTemplate template) {
        var local=new DefaultListableBeanFactory();local.registerSingleton("jdbc",template);return local.getBeanProvider(JdbcTemplate.class);
    }
    private JdbcConversationMemoryRepository restarted() {return new JdbcConversationMemoryRepository(provider(jdbc),owners);}
    private long task(){return jdbc.queryForObject("SELECT id FROM ai_ops_memory_post_processing WHERE session_id=? AND task_type='SEMANTIC'",Long.class,session);}
    private SemanticMemoryWriteResult write(MemoryProcessingJob job,boolean available,boolean configured) {
        var source=job.message();var metadata=new LinkedHashMap<>(source.metadata());metadata.put("semantic_job_id",job.id());metadata.put("semantic_epoch",job.epoch());metadata.put("semantic_source_hash",job.sourceHash());
        return writer.write(new SemanticMemoryWriteCommand(session,owner,source.role(),source.content(),source.createdAt(),metadata,available,configured));
    }
    private List<SemanticProjectionReference> references() {
        return pg.query("SELECT id::text AS id,metadata::text AS metadata FROM "+TABLE+" WHERE metadata->>'session_id'=?",(row,index)->{
            var metadata=JSON.parseObject(row.getString("metadata"));return new SemanticProjectionReference(row.getString("id"),metadata.getLongValue("semantic_job_id"),metadata.getLongValue("semantic_epoch"),metadata.getString("semantic_source_hash"),metadata.getString("semantic_projection"));
        },session);
    }
    private Set<String> visible(){return repo.visibleSemanticProjections(session,project,owner,references());}

    @Test void lexicalRemainsUsableAcrossTwelvePersistedRetriesAndTheNewVectorActivatesOnlyAfterOwnedCompletion() {
        long id=task(),now=System.currentTimeMillis();MemoryProcessingJob old=null;
        for(int attempt=1;attempt<=12;attempt++) {
            var claim=repo.claim(id,"worker-"+attempt,now,300000).orElseThrow();if(old==null)old=claim;
            assertEquals(attempt,claim.attempts());assertTrue(repo.validSemanticSource(claim,now));
            var result=write(claim,true,true);assertTrue(result.written());assertTrue(result.modelPending());assertEquals("lexical",result.storage());
            assertTrue(repo.defer(claim,"LEXICAL_MODEL_PENDING",result.reason(),now,null));
            assertEquals(1,references().size());assertEquals(1,visible().size());
            long due=jdbc.queryForObject("SELECT next_attempt_ms FROM ai_ops_memory_post_processing WHERE id=?",Long.class,id);
            assertTrue(due>now && due-now<=3600000);repo=restarted();assertTrue(repo.claim(id,"early",due-1,300000).isEmpty());now=due;
        }
        var current=repo.claim(id,"configured-recovered",now,300000).orElseThrow();assertEquals(13,current.attempts());outage.set(false);
        assertEquals("vector",write(current,true,true).storage());
        var vector=references().stream().filter(ref->"VECTOR".equals(ref.kind())).findFirst().orElseThrow();
        assertFalse(visible().contains(vector.documentId()),"A PG write alone cannot activate an uncommitted MySQL epoch");
        assertFalse(repo.complete(old,"VECTOR",now,null));assertTrue(repo.complete(current,"VECTOR",now,null));
        assertTrue(visible().contains(vector.documentId()));assertEquals(2,references().size());
        var retrieval=new SemanticMemoryRetrievalApplicationService(new OpsSemanticVectorRecallAdapter(beans.getBeanProvider(VectorStore.class)),
                new OpsSemanticLexicalRecallAdapter(provider(pg),TABLE),null,null,repo);
        assertEquals(1,retrieval.search(new SemanticMemoryRetrievalQuery(session,owner,"semantic",10,10,true,false,6)).size(),"Lexical and vector copies fuse as one original source");
        assertTrue(repo.claim(id,"duplicate",now+86400000,300000).isEmpty());
    }

    @Test void oldWorkersClearedOrChangedSourcesAndCurrentOwnerRevocationCannotActivateHistoricalPhysicalVectors() {
        long id=task(),now=System.currentTimeMillis();outage.set(false);
        var old=repo.claim(id,"old",now,1).orElseThrow();
        assertTrue(repo.claim(id,"recover-expired",now+2,300000).isEmpty());
        long due=jdbc.queryForObject("SELECT next_attempt_ms FROM ai_ops_memory_post_processing WHERE id=?",Long.class,id);
        repo=restarted();var current=repo.claim(id,"current",due,300000).orElseThrow();write(current,true,true);assertTrue(repo.complete(current,"VECTOR",due,null));
        write(old,true,true);assertFalse(repo.complete(old,"VECTOR",due,null));
        assertEquals(2,references().size());assertEquals(1,visible().size(),"The late old physical row cannot replace or activate the current epoch");
        assertTrue(repo.visibleSemanticProjections(session,"different-project",owner,references()).isEmpty());
        String reader="semantic-r-"+UUID.randomUUID();
        accounts.insert(new AdminUserAccount(null,reader,reader,"SYNTHETIC_NOT_A_LOGIN_CREDENTIAL",AdminUserRole.USER,1,null,null));
        members.grantIfAbsent(project,reader,reader,"MEMBER","synthetic-reader");
        assertEquals(1,repo.visibleSemanticProjections(session,project,reader,references()).size());
        jdbc.update("UPDATE admin_user SET status=0 WHERE user_id=?",owner);assertTrue(visible().isEmpty());
        assertTrue(repo.visibleSemanticProjections(session,project,reader,references()).isEmpty(),"A different authorized reader cannot substitute for the disabled original source owner");
        jdbc.update("UPDATE admin_user SET status=1 WHERE user_id=?",owner);assertEquals(1,visible().size());
        members.replace(project,List.of(),"synthetic-revocation");assertTrue(visible().isEmpty());
        members.grantIfAbsent(project,owner,owner,"MEMBER","synthetic-restoration");assertEquals(1,visible().size());
        jdbc.update("UPDATE ai_ops_chat_message SET metadata=JSON_SET(metadata,'$.memory_status','DISABLED') WHERE session_id=?",session);assertTrue(visible().isEmpty());
        jdbc.update("UPDATE ai_ops_chat_message SET metadata=JSON_REMOVE(metadata,'$.memory_status') WHERE session_id=?",session);assertEquals(1,visible().size());
        jdbc.update("UPDATE ai_ops_chat_message SET content='changed retained source' WHERE session_id=?",session);assertTrue(visible().isEmpty());
        new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())).executeWithoutResult(status->new JdbcColdMemoryRepository(provider(jdbc),null).clear(session));
        assertFalse(references().isEmpty(),"This test intentionally retains secondary rows across the independent PostgreSQL boundary");
        assertTrue(visible().isEmpty());assertFalse(repo.complete(current,"VECTOR",due+1,null));
    }

    @Test void intentionalLexicalModeCreatesNoModelSupplementAndTemporaryReadinessFailureStaysPending() {
        long id=task(),now=System.currentTimeMillis();var claim=repo.claim(id,"lexical",now,300000).orElseThrow();
        var temporary=write(claim,false,true);assertTrue(temporary.modelPending());assertEquals(0,modelCalls.get());
        assertTrue(repo.defer(claim,"LEXICAL_MODEL_PENDING",temporary.reason(),now,null));
        long due=jdbc.queryForObject("SELECT next_attempt_ms FROM ai_ops_memory_post_processing WHERE id=?",Long.class,id);
        var resumed=restarted().claim(id,"explicit-lexical",due,300000).orElseThrow();assertEquals(2,resumed.attempts());
        var intentional=write(resumed,false,false);assertFalse(intentional.modelPending());assertEquals("INTENTIONAL_LEXICAL",intentional.reason());
        assertEquals(0,modelCalls.get());assertTrue(repo.complete(resumed,"LEXICAL",due,null));assertEquals(1,visible().size());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_memory_post_processing WHERE session_id=? AND task_type='SEMANTIC'",Integer.class,session));
        assertTrue(repo.claim(id,"no-new-work",now+86400000,300000).isEmpty());
    }    @Test void clearingPendingWorkCancelsItsEpochAndLatePhysicalWritesCannotResurrectTheSource() {
        long id=task(),now=System.currentTimeMillis();var pending=repo.claim(id,"before-clear",now,300000).orElseThrow();outage.set(false);
        assertEquals("vector",write(pending,true,true).storage());assertTrue(visible().isEmpty());
        new TransactionTemplate(new DataSourceTransactionManager(jdbc.getDataSource())).executeWithoutResult(status->new JdbcColdMemoryRepository(provider(jdbc),null).clear(session));
        assertEquals("CANCELED",jdbc.queryForObject("SELECT status FROM ai_ops_memory_post_processing WHERE id=?",String.class,id));
        assertFalse(repo.validSemanticSource(pending,now+1));assertFalse(repo.complete(pending,"VECTOR",now+1,null));assertFalse(repo.retry(pending,"LATE",now+1));
        write(pending,true,true);assertEquals(1,references().size());assertTrue(visible().isEmpty());
        var fresh=repo.capture(new ColdMemoryMessageSnapshot(session,owner,"user","new retained source","now",Map.of("projectId",project,"turnId","after-clear")),24);
        assertEquals(2,ConversationMemoryWindow.sequence(fresh));assertTrue(visible().isEmpty());
    }
    @Test void anAlreadyDisabledRetainedSourceIsBlockedBeforeAnyModelOrProjectionWrite() {
        long id=task(),now=System.currentTimeMillis();
        jdbc.update("UPDATE ai_ops_chat_message SET metadata=JSON_SET(metadata,'$.memory_status','DISABLED') WHERE session_id=?",session);
        var service=new MemoryPostProcessingApplicationService(message->modelCalls.incrementAndGet(),null,null,null,null,()->Runnable::run,null,
                repo,java.time.Clock.fixed(java.time.Instant.ofEpochMilli(now),java.time.ZoneOffset.UTC));
        var source=repo.window(session,2,false).orElseThrow().messages().get(0);
        service.submit(new MemoryPostProcessingCommand(new MemoryMessageView(source.sessionId(),source.userId(),source.role(),source.content(),source.createdAt(),source.metadata()),24,true));
        assertEquals(0,modelCalls.get());assertTrue(references().isEmpty());
        assertEquals("SOURCE_BLOCKED",jdbc.queryForObject("SELECT status FROM ai_ops_memory_post_processing WHERE id=?",String.class,id));
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"EMPTY","NULL"})
    void legitimatePrivateUserMemoryNeedsItsOwnerButNoUnrelatedProjectMembership(String projectStorage) {
        members.replace(project,List.of(),"synthetic-private-scope-check");
        project="";session="private-s-"+UUID.randomUUID();
        jdbc.update("INSERT INTO ai_ops_chat_session(session_id,user_id,project_id) VALUES (?,?,?)",session,owner,"NULL".equals(projectStorage)?null:"");
        repo.capture(new ColdMemoryMessageSnapshot(session,owner,"user","private durable memory","now",Map.of("projectId","","turnId","private")),24);
        long now=System.currentTimeMillis();var claim=repo.claim(task(),"private",now,300000).orElseThrow();
        assertTrue(repo.validSemanticSource(claim,now));
        var result=write(claim,false,false);assertEquals("lexical",result.storage());assertFalse(result.modelPending());
        assertTrue(repo.complete(claim,"LEXICAL",now,null));assertEquals(1,visible().size());assertEquals(0,modelCalls.get());
        assertTrue(repo.visibleSemanticProjections(session,project,"not-the-owner",references()).isEmpty());
    }

}
