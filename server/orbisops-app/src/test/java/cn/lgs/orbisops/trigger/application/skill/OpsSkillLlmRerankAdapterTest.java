package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.domain.skill.model.*;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic wire protocol, never a Qwen model-quality claim. */
class OpsSkillLlmRerankAdapterTest {
    private HttpServer server;private ExecutorService executor;
    private final AtomicInteger calls=new AtomicInteger();
    private final AtomicReference<String> upgrade=new AtomicReference<>();
    private final AtomicReference<JSONObject> received=new AtomicReference<>();
    private final AtomicReference<String> reply=new AtomicReference<>();
    private OpsSkillRetrievalHttpClient client;
    private static final String REV="a".repeat(40);
    @BeforeEach void open() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);executor=Executors.newCachedThreadPool();server.setExecutor(executor);
        server.createContext("/",x->{
            upgrade.set(x.getRequestHeaders().getFirst("Upgrade"));calls.incrementAndGet();received.set(JSON.parseObject(new String(x.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)));
            byte[] body=reply.get().getBytes(StandardCharsets.UTF_8);x.sendResponseHeaders(200,body.length);x.getResponseBody().write(body);x.close();
        });server.start();
        client=new OpsSkillRetrievalHttpClient("http://127.0.0.1:"+server.getAddress().getPort(),"",REV,REV);
    }
    @AfterEach void close() { server.stop(0);executor.shutdownNow(); }
    private void response(Object ranking) { reply.set(JSON.toJSONString(Map.of("model",OpsSkillRetrievalHttpClient.RERANKER,"revision",REV,"preprocessing",OpsSkillRetrievalHttpClient.PREPROCESS,"ranking",ranking))); }
    @Test void keepsRawScorerValuesAndUsesOneDedicatedRequest() {
        response(List.of(Map.of("id","one","score",4.5),Map.of("id","two","score",-2.5)));
        var scores=new OpsSkillLlmRerankAdapter(client).scores("query",List.of(skill("one"),skill("two")));
        assertNull(upgrade.get(), "HTTP/1.1 inference servers must not receive an h2c upgrade");
        assertEquals(Map.of("one",4.5,"two",-2.5),scores);assertEquals(1,calls.get());
        assertEquals(OpsSkillRetrievalHttpClient.RERANKER,received.get().getString("model"));
        assertFalse(received.get().containsKey("messages"));assertFalse(received.get().containsKey("embedding"));
    }
    @Test void rejectsUnknownDuplicateMissingAndNonFiniteScoresWithoutRetry() {
        for(Object invalid:List.of(
                List.of(Map.of("id","invented","score",1),Map.of("id","two","score",2)),
                List.of(Map.of("id","one","score",1),Map.of("id","one","score",2)),
                List.of(Map.of("id","one","score",1)),
                List.of(Map.of("id","one","score","NaN"),Map.of("id","two","score",2)))) {
            int before=calls.get();response(invalid);
            assertThrows(IllegalStateException.class,()->new OpsSkillLlmRerankAdapter(client).scores("query",List.of(skill("one"),skill("two"))));assertEquals(before+1,calls.get());
        }
    }
    @Test void validatesExactEmbeddingDimensionNormalizationAndModelIdentity() {
        var vector=new ArrayList<Double>(Collections.nCopies(1024,0D));vector.set(0,1D);
        var result=new LinkedHashMap<String,Object>(Map.of("model",OpsSkillRetrievalHttpClient.EMBEDDING,"revision",REV,"preprocessing",OpsSkillRetrievalHttpClient.PREPROCESS,"embedding",vector));
        reply.set(JSON.toJSONString(result));assertEquals(1024,client.embed("检索问题",true).length);
        assertEquals(1024,received.get().getIntValue("dimensions"));assertEquals("query",received.get().getString("kind"));
        vector.remove(0);reply.set(JSON.toJSONString(result));assertThrows(IllegalStateException.class,()->client.embed("x",true));
        vector.add(0D);reply.set(JSON.toJSONString(result));assertThrows(IllegalStateException.class,()->client.embed("x",true));
        vector.set(0,1D);result.put("model","another-model");reply.set(JSON.toJSONString(result));assertThrows(IllegalStateException.class,()->client.embed("x",true));
    }
    @Test void boundedResponseAndMissingConfigurationNeverInvokeAnotherProvider() {
        reply.set(" ".repeat(1_000_001));assertThrows(IllegalStateException.class,()->client.embed("x",true));assertEquals(1,calls.get());
        var unavailable=new OpsSkillRetrievalHttpClient("","","","");
        assertTrue(new OpsSkillLlmRerankAdapter(unavailable).scores("x",List.of(skill("one"))).isEmpty());assertEquals(1,calls.get());
    }
    @Test void enforcesBudgetThroughSlowResponseBody() {
        server.removeContext("/");server.createContext("/",x->{
            calls.incrementAndGet();x.getRequestBody().readAllBytes();x.sendResponseHeaders(200,100);
            try { Thread.sleep(6500);x.getResponseBody().write(" ".repeat(100).getBytes(StandardCharsets.UTF_8)); }
            catch(InterruptedException ignored) { Thread.currentThread().interrupt(); } finally { x.close(); }
        });
        long start=System.nanoTime();assertThrows(IllegalStateException.class,()->client.embed("x",true));
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<5900);assertEquals(1,calls.get());
    }
    private SkillRuntimeCandidate skill(String id) {
        return new SkillRuntimeCandidate(id,"p","PROJECT",id,id,1,"hash-"+id,"package-"+id,"manifest-"+id,Map.of(),"SKILL.md","ACTIVE","AUTO",10,
                new SkillRoutingProfile("GENERAL","",id,List.of("query"),List.of("unrelated"),List.of(id)));
    }
}
