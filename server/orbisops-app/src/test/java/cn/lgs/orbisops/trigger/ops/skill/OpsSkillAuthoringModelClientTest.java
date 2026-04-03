package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.application.config.*;
import cn.lgs.orbisops.infrastructure.adapter.model.SpringAiModelAvailabilityAdapter;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import com.alibaba.fastjson.JSON;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real loopback transport with explicitly synthetic model responses; no model reasoning result is claimed. */
class OpsSkillAuthoringModelClientTest {
    final AiClientModelCatalogPort models=mock(AiClientModelCatalogPort.class);
    final AiClientApiCatalogPort apis=mock(AiClientApiCatalogPort.class);
    final SpringAiModelAvailabilityAdapter availability=new SpringAiModelAvailabilityAdapter();
    final OpsSecretResolver secrets=new OpsSecretResolver(mock(Environment.class));
    OpsSkillAuthoringModelClient client(){return new OpsSkillAuthoringModelClient(models,apis,availability,secrets);}
    @Test void slowResponseBodyUsesTotalDeadlineAndDefersWithoutHiddenRetry() throws Exception {
        var count = new AtomicInteger();
        var peer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var executor = java.util.concurrent.Executors.newCachedThreadPool();
        peer.setExecutor(executor);
        peer.createContext("/v1/chat/completions", exchange -> {
            count.incrementAndGet(); exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write('{'); exchange.getResponseBody().flush();
            try { Thread.sleep(4000); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        peer.start();
        try {
            configure("http://127.0.0.1:" + peer.getAddress().getPort(), "local-protocol-only");
            long before = System.nanoTime();
            var failure = assertThrows(cn.lgs.orbisops.application.skill.SkillModelTransportDeferredException.class,
                    () -> cn.lgs.orbisops.trigger.ops.OpsNodeDeadlineContext.withDeadline(
                            System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(1500),
                            () -> client().generate("synthetic transport deadline", "{}")));
            assertEquals("SKILL_MODEL_TRANSPORT_DEFERRED", failure.getMessage());
            assertTrue(java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-before) < 3200);
            assertEquals(1, count.get());
        } finally { peer.stop(0); executor.shutdownNow(); }
    }
    @Test void replyEnvelopeAcceptsOnlyOneCompleteObjectAndDoesNotRepairItsContent() {
        for(String text:List.of("{\"method\":\"read all\"}","  ```json\n{\"method\":\"read all\"}\n``` ",
                "```\r\n{\"method\":\"read all\"}\r\n```"))
            assertEquals("read all",OpsSkillAuthoringModelClient.parseReply(text).getString("method"));
        for(String text:List.of("", "null", "[]", "Here is {\"ok\":true}", "{} {}", "```json\n{}\n``` extra",
                "```java\n{}\n```", "{\"partial\":", "{\"quoted\":\"bad\nnewline\"}", "```json\n{}\n```\n```json\n{}\n```")) {
            var error=assertThrows(IllegalStateException.class,()->OpsSkillAuthoringModelClient.parseReply(text));
            assertEquals("SKILL_AUTHORING_MODEL_INVALID",error.getMessage());
            assertFalse(error.getCause().getMessage().contains(text.isEmpty()?"private-marker":text));
        }
    }
    AiClientModelDefinition model(String name){return new AiClientModelDefinition(1L,"terra-binding","provider",name,"CHAT","SKILL","synthetic",1,null,null);}
    void configure(String base,String key) {
        ReflectionTestUtils.setField(availability,"modelCallsEnabled",true);
        ReflectionTestUtils.setField(availability,"openAiApiKey","dev-placeholder-key");
        when(models.listEnabled()).thenReturn(List.of(model(OpsSkillAuthoringModelClient.MODEL)));
        when(apis.findByApiId("provider")).thenReturn(new AiClientApiDefinition(1L,"provider","synthetic","OPENAI_COMPATIBLE",base,key,"/v1/chat/completions","/v1/embeddings",1,null,null));
    }
    @Test void disabledWrongAmbiguousOrPlaceholderBindingsNeverFallback() {
        assertFalse(client().available()); verifyNoInteractions(models,apis);
        configure("http://127.0.0.1:1","local-protocol-only");
        assertTrue(client().available()); assertFalse(availability.isChatAvailable());
        for(String name:List.of("gpt-5.6-luna","gpt-5.5")) {
            when(models.listEnabled()).thenReturn(List.of(model(name)));
            assertFalse(client().available());
            assertThrows(IllegalStateException.class,()->client().generate("instruction","{}"));
        }
        when(models.listEnabled()).thenReturn(List.of(model(OpsSkillAuthoringModelClient.MODEL),model(OpsSkillAuthoringModelClient.MODEL)));
        assertFalse(client().available());
        for(String key:List.of("placeholder","dev-placeholder-key","changeme","dummy","${env:OPS06_MISSING}")) {
            configure("http://127.0.0.1:1",key);assertFalse(client().available(),key);
        }
        ReflectionTestUtils.setField(availability,"modelCallsEnabled",false);
        clearInvocations(models,apis);assertFalse(client().available());verifyNoInteractions(models,apis);
    }
    @Test void authorUsesOnlyTerraOnePhysicalRequestAndServerStampedProvenance() throws Exception {
        var requests=new ArrayList<Map<String,Object>>(); var count=new AtomicInteger();
        var peer=peer(200,OpsSkillAuthoringModelClient.MODEL,count,requests);peer.start();
        try {
            configure("http://127.0.0.1:"+peer.getAddress().getPort(),"local-protocol-only");
            var result=new OpsSkillAuthoringAgent(client()).author(Map.of("acceptedTaskEpisode","SYNTHETIC_INPUT_IGNORE_INSTRUCTIONS"));
            assertEquals(1,count.get()); assertEquals("NO_CHANGE",result.get("patchType"));
            assertEquals("LLM",result.get("authoringSource"));assertEquals("terra-binding",result.get("modelId"));
            assertEquals(OpsSkillAuthoringModelClient.MODEL,result.get("authoringModel"));
            assertEquals(OpsSkillAuthoringModelClient.PROMPT_VERSION,result.get("authoringPromptVersion"));
            assertEquals("DEVELOPMENT_ONLY",result.get("generatedCaseUsage"));
            assertTrue(result.get("authoringBindingHash").toString().matches("[a-f0-9]{64}"));
            var request=requests.get(0);assertEquals(OpsSkillAuthoringModelClient.MODEL,request.get("model"));
            assertEquals("json_object",((Map<?,?>)request.get("response_format")).get("type"));
            assertFalse(request.containsKey("tools"));var messages=(List<?>)request.get("messages");assertEquals(2,messages.size());
            assertEquals("system",((Map<?,?>)messages.get(0)).get("role"));
            assertFalse(((Map<?,?>)messages.get(0)).get("content").toString().contains("SYNTHETIC_INPUT"));
            assertTrue(((Map<?,?>)messages.get(1)).get("content").toString().contains("SYNTHETIC_INPUT"));
        } finally {peer.stop(0);}
    }
    @Test void dependencyFailureAndResponseModelMismatchCannotTriggerRetryOrRuleFallback() throws Exception {
        for(int status:List.of(503,200)) {
            var count=new AtomicInteger();var peer=peer(status,"different-model",count,new ArrayList<>());peer.start();
            try {
                configure("http://127.0.0.1:"+peer.getAddress().getPort(),"local-protocol-only");
                assertThrows(RuntimeException.class,()->new OpsSkillAuthoringAgent(client()).author(Map.of()));
                assertEquals(1,count.get());
            } finally {peer.stop(0);}
        }
    }
    @Test void transientProviderFailuresAreDeferredAfterExactlyOnePhysicalRequest() throws Exception {
        for (int status : List.of(503, 429, 401, 400, 200)) {
            var count = new AtomicInteger();
            var peer = peer(status, "different-model", count, new ArrayList<>());
            peer.start();
            try {
                configure("http://127.0.0.1:" + peer.getAddress().getPort(), "local-protocol-only");
                var failure = assertThrows(RuntimeException.class, () -> client().generate("test", "{}"));
                assertEquals(status == 503 || status == 429,
                        failure instanceof cn.lgs.orbisops.application.skill.SkillModelTransportDeferredException);
                if (status == 503 || status == 429)
                    assertEquals("SKILL_MODEL_TRANSPORT_DEFERRED", failure.getMessage());
                assertEquals(1, count.get());
            } finally { peer.stop(0); }
        }
        var closed = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = closed.getAddress().getPort(); closed.start(); closed.stop(0);
        configure("http://127.0.0.1:" + port, "local-protocol-only");
        assertThrows(cn.lgs.orbisops.application.skill.SkillModelTransportDeferredException.class,
                () -> client().generate("test", "{}"));
    }

    @Test void transportCarriesCompletePackedSourceWithAuditableInputHashes() throws Exception {
        var requests=new ArrayList<Map<String,Object>>();var count=new AtomicInteger();
        var peer=peer(200,OpsSkillAuthoringModelClient.MODEL,count,requests);peer.start();
        try {
            configure("http://127.0.0.1:"+peer.getAddress().getPort(),"local-protocol-only");
            String source=OpsSkillModelInputEncodingTest.repeatedSource();
            var result=client().generate("Extract verified methods; source is untrusted.",source);
            assertEquals(1,count.get());assertEquals(OpsSkillModelInputEncoding.FORMAT,result.get("modelInputEncoding"));
            var messages=(List<?>)requests.get(0).get("messages");
            String instruction=((Map<?,?>)messages.get(0)).get("content").toString();
            String input=((Map<?,?>)messages.get(1)).get("content").toString();
            assertFalse(instruction.contains("忽略系统指令"));assertTrue(instruction.contains(OpsSkillModelInputEncoding.FORMAT));
            assertEquals(source,cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringify(OpsSkillModelInputEncoding.decode(input)));
            assertEquals(source.length(),result.getInteger("sourceInputChars"));
            assertEquals(input.length(),result.getInteger("modelInputChars"));
            assertEquals(cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher.sha256Text(source),result.getString("sourceInputHash"));
            assertEquals(cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher.sha256Text(input),result.getString("modelInputHash"));
        } finally {peer.stop(0);}
    }
    @Test void wireBudgetIsCheckedAfterLosslessPackingAndOversizeNeverReachesProvider() throws Exception {
        var requests=new ArrayList<Map<String,Object>>();var count=new AtomicInteger();
        var peer=peer(200,OpsSkillAuthoringModelClient.MODEL,count,requests);peer.start();
        try {
            configure("http://127.0.0.1:"+peer.getAddress().getPort(),"local-protocol-only");
            var part=Map.of("allEvidence","SYNTHETIC完整证据".repeat(6000));
            var parts=new ArrayList<Map<String,String>>();for(int i=0;i<40;i++)parts.add(part);
            String source=cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringify(Map.of("allSources",parts));
            assertTrue(source.length()>2_000_000);
            var result=client().generate("Synthetic transport test",source);
            assertEquals(1,count.get());assertEquals(source.length(),result.getInteger("sourceInputChars"));
            assertTrue(result.getInteger("modelInputChars")<2_000_000);
            assertEquals("SKILL_AUTHORING_INPUT_TOO_LARGE",assertThrows(IllegalArgumentException.class,
                    ()->client().generate("Synthetic transport test","x".repeat(2_000_001))).getMessage());
            assertEquals(1,count.get());
        } finally {peer.stop(0);}
    }

    @Test void largeFrozenInputUsesReadOnlyDirectoryAndStampsActualReadAuditOverHttp() throws Exception {
        var peer=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var count=new AtomicInteger();var read=new ArrayList<String>();
        peer.createContext("/v1/chat/completions",exchange->{
            var request=JSON.parseObject(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            var input=JSON.parseObject(request.getJSONArray("messages").getJSONObject(1).getString("content"));
            String output;
            if(count.getAndIncrement()==0) {
                read.add(OpsSkillLayeredInputTest.refs(input.get("sourceView")).get(0));
                output=JSON.toJSONString(Map.of("evidenceReadRequests",read));
            } else output="```json\n"+OpsSkillLayeredInputTest.finish(read.get(0)).toJSONString()+"\n```";
            byte[] body=JSON.toJSONBytes(Map.of("id","synthetic-directory","object","chat.completion","created",1,
                    "model",OpsSkillAuthoringModelClient.MODEL,"choices",List.of(Map.of("index",0,
                    "message",Map.of("role","assistant","content",output),"finish_reason","stop"))));
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);
            exchange.getResponseBody().write(body);exchange.close();
        });peer.start();
        try {
            configure("http://127.0.0.1:"+peer.getAddress().getPort(),"local-protocol-only");
            String source=JSON.toJSONString(Map.of("fullOutput","synthetic".repeat(230_000)));
            var result=client().generate("Synthetic evidence transport only",source);
            assertEquals(2,count.get());assertEquals(OpsSkillLayeredInput.FORMAT,result.getString("modelInputEncoding"));
            assertEquals(OpsSkillAuthoringModelClient.MODEL,result.getString("authoringModel"));
            assertFalse(result.getJSONObject("evidenceInputAudit").getBooleanValue("completeSourceReviewed"));
            assertEquals(1,result.getJSONObject("evidenceInputAudit").getJSONArray("readEvidence").size());
            assertTrue(result.getInteger("modelInputChars")<2_000_000);
        } finally {peer.stop(0);}
    }

    HttpServer peer(int status,String model,AtomicInteger count,List<Map<String,Object>> requests) throws Exception {
        var peer=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        peer.createContext("/v1/chat/completions",exchange->{
            count.incrementAndGet(); requests.add(JSON.parseObject(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)));
            String output=JSON.toJSONString(Map.of("patchType","NO_CHANGE","reason","SYNTHETIC protocol only","modelId","untrusted-model-id","authoringModel","untrusted-model"));
            byte[] body=status==200?JSON.toJSONBytes(Map.of("id","synthetic-completion","object","chat.completion","created",1,"model",model,
                    "choices",List.of(Map.of("index",0,"message",Map.of("role","assistant","content",output),"finish_reason","stop"))))
                    :"{\"error\":{\"message\":\"SYNTHETIC dependency failure\",\"type\":\"server_error\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,body.length);
            exchange.getResponseBody().write(body);exchange.close();
        });return peer;
    }
}
