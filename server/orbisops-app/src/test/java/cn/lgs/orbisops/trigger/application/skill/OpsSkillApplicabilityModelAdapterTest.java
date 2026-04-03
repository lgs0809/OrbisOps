package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.config.*;
import cn.lgs.orbisops.infrastructure.adapter.model.SpringAiModelAvailabilityAdapter;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import cn.lgs.orbisops.domain.skill.model.*;
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

/** Synthetic HTTP protocol checks; real model decisions are exercised by the standalone catalog probe. */
class OpsSkillApplicabilityModelAdapterTest {
    final AiClientModelCatalogPort models=mock(AiClientModelCatalogPort.class);
    final AiClientApiCatalogPort apis=mock(AiClientApiCatalogPort.class);
    final SpringAiModelAvailabilityAdapter availability=new SpringAiModelAvailabilityAdapter();
    final OpsSecretResolver secrets=new OpsSecretResolver(mock(Environment.class));
    final SkillRuntimeCandidate skill=new SkillRuntimeCandidate("pool","p","PROJECT","连接池","说明",1,"h","package","m",Map.of(),"SKILL.md","ACTIVE","AUTO",10,
            new SkillRoutingProfile("GENERAL","","说明",List.of("排查连接等待"),List.of("删除数据"),List.of("Redis")));
    OpsSkillApplicabilityModelAdapter adapter(){return new OpsSkillApplicabilityModelAdapter(models,apis,availability,secrets);}
    void configure(int port) {
        ReflectionTestUtils.setField(availability,"modelCallsEnabled",true);
        when(models.listEnabled()).thenReturn(List.of(new AiClientModelDefinition(1L,"local-model","local-api","gpt-5.6-luna","CHAT","FIXTURE","protocol",1,null,null)));
        when(apis.findByApiId("local-api")).thenReturn(new AiClientApiDefinition(1L,"local-api","fixture","OPENAI_COMPATIBLE","http://127.0.0.1:"+port,"local-protocol-only","/v1/chat/completions","/v1/embeddings",1,null,null));
    }
    Map<String,Object> decision(){return Map.of("skillId","pool","verdict","MATCH","useCaseIndexes",List.of(0),"evidenceQuote","借不到连接");}
    @Test void retriesTemporaryFailuresWithoutToolsAndBindsTheCompleteCatalog() throws Exception {
        var peer=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);var calls=new AtomicInteger();
        peer.createContext("/v1/chat/completions",x->{
            var request=JSON.parseObject(new String(x.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            assertEquals("gpt-5.6-luna",request.get("model"));assertFalse(request.containsKey("tools"));
            var input=JSON.parseObject(request.getJSONArray("messages").getJSONObject(1).getString("content"));
            var content=JSON.toJSONString(Map.of("catalogHash",input.getString("catalogHash"),"decisions",List.of(decision())));
            int n=calls.incrementAndGet();var out=n<2?"temporary unavailable".getBytes(StandardCharsets.UTF_8):JSON.toJSONBytes(Map.of(
                    "id","fixture","object","chat.completion","created",1,"model","gpt-5.6-luna",
                    "choices",List.of(Map.of("index",0,"message",Map.of("role","assistant","content",content),"finish_reason","stop"))));
            x.getResponseHeaders().set("Content-Type","application/json");x.sendResponseHeaders(n<2?503:200,out.length);x.getResponseBody().write(out);x.close();
        });peer.start();
        try {configure(peer.getAddress().getPort());var result=adapter().assess("p","借不到连接",List.of(skill));
            assertEquals(2,calls.get());assertEquals(SkillApplicabilityDecision.Verdict.MATCH,result.get("pool").verdict());
        } finally {peer.stop(0);}
    }
    @Test void missingUnknownDuplicateIdsWrongSnapshotAndAdditionalFieldsAreRejected() {
        String valid=JSON.toJSONString(Map.of("catalogHash","h","decisions",List.of(decision())));
        assertEquals(1,OpsSkillApplicabilityModelAdapter.parse(valid,"h",List.of(skill)).size());
        for(String output:List.of(valid.replace("\"pool\"","\"other\""),valid.replace("\"h\"","\"old\""),
                "{\"catalogHash\":\"h\",\"decisions\":[]}",
                JSON.toJSONString(Map.of("catalogHash","h","decisions",List.of(decision(),decision()))),
                valid.replace("\"MATCH\"","\"APPROVE\""),valid.replace("[0]","[0.2]"),
                valid.replace("\"skillId\"","\"execute\":true,\"skillId\"")))
            assertThrows(RuntimeException.class,()->OpsSkillApplicabilityModelAdapter.parse(output,"h",List.of(skill)));
    }
    @Test void aDisabledOrMissingLunaBindingDoesNotChooseTerraInstead() {
        assertThrows(IllegalStateException.class,()->adapter().assess("p","借不到连接",List.of(skill)));verifyNoInteractions(models,apis);
        configure(1);when(models.listEnabled()).thenReturn(List.of());assertThrows(IllegalStateException.class,()->adapter().assess("p","借不到连接",List.of(skill)));
    }
}
