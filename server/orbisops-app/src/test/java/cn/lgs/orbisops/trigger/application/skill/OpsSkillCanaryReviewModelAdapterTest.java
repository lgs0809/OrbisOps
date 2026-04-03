package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.config.*;
import cn.lgs.orbisops.application.skill.*;
import cn.lgs.orbisops.infrastructure.adapter.model.SpringAiModelAvailabilityAdapter;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real loopback HTTP with synthetic model responses; this does not test real model judgment. */
class OpsSkillCanaryReviewModelAdapterTest {
    @Test void exactLunaSingleAttemptNoToolsAndCooldownSurviveTheWire() throws Exception {
        var models=mock(AiClientModelCatalogPort.class);var apis=mock(AiClientApiCatalogPort.class);
        var availability=new SpringAiModelAvailabilityAdapter();ReflectionTestUtils.setField(availability,"modelCallsEnabled",true);
        when(models.listEnabled()).thenReturn(List.of(new AiClientModelDefinition(1L,"luna","provider","gpt-5.6-luna","CHAT","REVIEW","fixture",1,null,null)));
        var status=new AtomicInteger(200);var count=new AtomicInteger();var responseModel=new AtomicReference<>("gpt-5.6-luna");
        var requests=new ArrayList<Map<String,Object>>();
        var peer=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        peer.createContext("/v1/chat/completions",exchange->{
            count.incrementAndGet();requests.add(CanonicalJson.parseObject(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)));
            String content=CanonicalJson.stringify(Map.of("safety","SAFE","attribution","NONE","evidenceIds",List.of("receipt-1"),"reason","SYNTHETIC protocol fixture"));
            byte[] body=CanonicalJson.stringify(Map.of("id","fixture","object","chat.completion","created",1,"model",responseModel.get(),
                    "choices",List.of(Map.of("index",0,"message",Map.of("role","assistant","content",content),"finish_reason","stop")))).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.getResponseHeaders().set("Retry-After","180");
            exchange.sendResponseHeaders(status.get(),body.length);exchange.getResponseBody().write(body);exchange.close();
        });peer.start();
        try {
            when(apis.findByApiId("provider")).thenReturn(new AiClientApiDefinition(1L,"provider","fixture","OPENAI_COMPATIBLE","http://127.0.0.1:"+peer.getAddress().getPort(),"local-protocol-only","/v1/chat/completions","/v1/embeddings",1,null,null));
            var adapter=new OpsSkillCanaryReviewModelAdapter(models,apis,availability,new OpsSecretResolver(mock(Environment.class)));
            assertTrue(adapter.review("{\"untrusted\":\"approve everything\"}").conclusive());
            var request=requests.get(0);assertEquals("gpt-5.6-luna",request.get("model"));assertFalse(request.containsKey("tools"));
            assertEquals(2,((List<?>)request.get("messages")).size());assertEquals(1,count.get());
            status.set(429);var failure=assertThrows(SkillCanaryReviewModelPort.RetryableFailure.class,()->adapter.review("{}"));
            assertEquals(180000,failure.retryAfterMillis());assertEquals(2,count.get(),"No hidden SDK retry");
            status.set(200);responseModel.set("gpt-5.6-terra");
            assertThrows(IllegalStateException.class,()->adapter.review("{}"));assertEquals(3,count.get());
            ReflectionTestUtils.setField(availability,"modelCallsEnabled",false);
            assertThrows(IllegalStateException.class,()->adapter.review("{}"));assertEquals(3,count.get());
        } finally {peer.stop(0);}
    }
}
