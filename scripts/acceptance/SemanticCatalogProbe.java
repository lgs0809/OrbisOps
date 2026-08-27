import cn.lgs.orbisops.trigger.application.skill.OpsSkillRetrievalHttpClient;
import cn.lgs.orbisops.trigger.application.skill.OpsSkillApplicabilityModelAdapter;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import cn.lgs.orbisops.application.config.*;
import cn.lgs.orbisops.infrastructure.adapter.model.SpringAiModelAvailabilityAdapter;
import org.springframework.core.env.StandardEnvironment;
import java.lang.reflect.Proxy;
import cn.lgs.orbisops.infrastructure.adapter.repository.PgSkillRouteIndexRepository;
import cn.lgs.orbisops.application.skill.*;
import cn.lgs.orbisops.domain.skill.model.*;
import cn.lgs.orbisops.domain.skill.service.*;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.nio.file.*;
import java.util.*;

/** Real Qwen + production pgvector/selection code; explicitly synthetic isolated catalog. */
public class SemanticCatalogProbe {
    static final Map<String,Object> report = new LinkedHashMap<>();
    static Path output;
    static void save() throws Exception { Files.writeString(output, JSON.toJSONString(report,
            SerializerFeature.PrettyFormat,SerializerFeature.WriteMapNullValue,SerializerFeature.DisableCircularReferenceDetect)+"\n"); }
    public static void main(String[] args) throws Exception {
        output=Path.of(args[1]);
        var dataset=JSON.parseObject(Files.readString(Path.of(args[0])));
        String schema=System.getenv("PROBE_SCHEMA");
        if(schema==null || !schema.matches("ops07_semantic_[a-f0-9]{16}")) throw new IllegalArgumentException("ISOLATED_SCHEMA_REQUIRED");
        String url=System.getenv("PROBE_PG_URL"),user=System.getenv("PROBE_PG_USER"),password=System.getenv("PROBE_PG_PASSWORD");
        var admin=new JdbcTemplate(new DriverManagerDataSource(url,user,password));
        admin.execute("CREATE SCHEMA IF NOT EXISTS "+schema);
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(url+"?currentSchema="+schema+",public",user,password));
        var index=new PgSkillRouteIndexRepository(jdbc);index.initialize();
        var client=new OpsSkillRetrievalHttpClient(System.getenv().getOrDefault("PROBE_RETRIEVAL_URL","http://127.0.0.1:8110/orbisops"),System.getenv("RETRIEVAL_PROBE_KEY"),
                "9f2f7e710d6d81056aa5c0a4f04764fec6bb7bda","4bd860ac4f15ad1897a214615cccc700f8f71818");
        List<SkillRuntimeCandidate> catalog=dataset.getJSONArray("catalog").stream().map(x->candidate((JSONObject)x,schema)).toList();
        var documents=new ArrayList<Map<String,Object>>();var queries=new ArrayList<Map<String,Object>>();
        report.putAll(Map.of("status","RUNNING","scope",dataset.getString("scope"),"schema",schema,
                "catalogSize",catalog.size(),"modelIdentity",client.modelIdentity(),"documents",documents,"queries",queries,
                "note","Synthetic routing benchmark, not independent business task history or Skill publication evidence."));save();
        int failedInRow=0;
        for(var c:catalog) {
            if(index.contains(c,client.modelIdentity())) { documents.add(Map.of("id",c.skillId(),"status","REUSED_VERIFIED_GENERATION"));continue; }
            index.stage(c,client.modelIdentity());
            var attempts=new ArrayList<Map<String,Object>>();boolean ready=false;
            for(int n=1;n<=3;n++) {
                long start=System.nanoTime();
                try {
                    float[] vector=client.embed(SkillRouteProjectionIdentity.document(c),false);
                    index.ready(c,client.modelIdentity(),vector);ready=true;
                    attempts.add(Map.of("attempt",n,"status","PASS","seconds",(System.nanoTime()-start)/1e9));break;
                } catch(IllegalStateException unavailable) {
                    attempts.add(Map.of("attempt",n,"status","UNAVAILABLE","reason",unavailable.getMessage(),"seconds",(System.nanoTime()-start)/1e9));
                    if(n<3) Thread.sleep(10000);
                }
            }
            documents.add(Map.of("id",c.skillId(),"status",ready?"READY":"BLOCKED","attempts",attempts));save();
            System.out.println("Document "+documents.size()+"/"+catalog.size()+" "+(ready?"READY":"BLOCKED"));
            failedInRow=ready?0:failedInRow+1;
            if(failedInRow>=3) break;
        }
        int ready=jdbc.queryForObject("SELECT COUNT(*) FROM ops_skill_route_generation WHERE status='READY'",Integer.class);
        report.put("readyGenerations",ready);
        if(ready!=catalog.size()) {report.put("status","BLOCKED_INCOMPLETE_INDEX");save();return;}
        var settings=new SkillRuntimeSelectionSettings(20,3,3,0.9,0.99,0.42,20,0.12,0.65,true,20,0.35);
        SkillApplicabilityPort assessor="true".equals(System.getenv("PROBE_APPLICABILITY")) ? realApplicability() : SkillApplicabilityPort.UNAVAILABLE;
        for(Object x:dataset.getJSONArray("queries")) {
            var q=(JSONObject)x;String text=q.getString("text"),expected=q.getString("expected");
            var record=new LinkedHashMap<String,Object>();record.put("id",q.getString("id"));record.put("expected",expected);
            var events=new ArrayList<Map<String,Object>>();var recalled=new ArrayList<String>();
            SkillSemanticScorePort semantic=(query,allowed)->{
                long started=System.nanoTime();
                try {var result=index.search(schema,allowed,client.modelIdentity(),client.embed(query,true),20);
                    recalled.addAll(result.entrySet().stream().sorted(Map.Entry.<String,Double>comparingByValue().reversed()
                            .thenComparing(Map.Entry::getKey)).map(Map.Entry::getKey).toList());
                    events.add(Map.of("stage","semantic","status","PASS","seconds",(System.nanoTime()-started)/1e9));return result;
                } catch(RuntimeException error) {events.add(Map.of("stage","semantic","status","FALLBACK","reason",error.getMessage(),"seconds",(System.nanoTime()-started)/1e9));throw error;}
            };
            SkillRerankPort rank=(query,allowed)->{
                long started=System.nanoTime();
                try {var result=client.rerank(query,allowed.stream().map(c->Map.of("id",c.skillId(),"text",SkillRouteProjectionIdentity.rerankDocument(c))).toList());
                    events.add(Map.of("stage","rerank","status","PASS","candidates",allowed.size(),"seconds",(System.nanoTime()-started)/1e9));return result;
                } catch(RuntimeException error) {events.add(Map.of("stage","rerank","status","FALLBACK","reason",error.getMessage(),"seconds",(System.nanoTime()-started)/1e9));throw error;}
            };
            SkillApplicabilityPort applicability=(project,query,allowed)->{
                long started=System.nanoTime();
                try {var result=assessor.assess(project,query,allowed);
                    events.add(Map.of("stage","applicability","status",result.isEmpty()?"NOT_ENABLED":"PASS",
                            "model","gpt-5.6-luna","seconds",(System.nanoTime()-started)/1e9,
                            "decisions",result.entrySet().stream().map(e->Map.of("skillId",e.getKey(),"verdict",e.getValue().verdict().name(),
                                    "useCaseIndexes",e.getValue().useCaseIndexes(),"evidenceQuote",e.getValue().evidenceQuote())).toList()));return result;
                } catch(RuntimeException error) {String message=error.getMessage();
                    events.add(Map.of("stage","applicability","status","FALLBACK","errorType",error.getClass().getSimpleName(),
                            "reason",message!=null && message.matches("[A-Z][A-Z0-9_]{1,100}")?message:"REDACTED_PROVIDER_ERROR",
                            "seconds",(System.nanoTime()-started)/1e9));throw error;}
            };
            long started=System.nanoTime();var result=new SkillHybridRetrieval(semantic,rank,settings,applicability).select(schema,text,catalog,Set.of(),3);
            var selected=result.selected().stream().map(v->v.candidate().skillId()).toList();
            boolean selectionPassed=expected==null?selected.isEmpty():selected.contains(expected);
            if(q.getBooleanValue("exclusive")) selectionPassed=selected.equals(List.of(expected));
            var lexical=new SkillHybridRetrieval((t,c)->Map.of(),(t,c)->Map.of(),settings).select(schema,text,catalog,Set.of(),3);
            record.putAll(Map.of("selected",selected,"lexicalSelected",lexical.selected().stream().map(v->v.candidate().skillId()).toList(),
                    "semanticTop20",recalled,"events",events,"seconds",(System.nanoTime()-started)/1e9,
                    "selectionPassed",selectionPassed,
                    "catalogReturned",result.catalog().size(),"suppressionReasons",result.suppressed().stream().map(s->s.reasonCode()).toList()));
            if(result.catalog().size()>20 || selected.size()>3) throw new AssertionError("ROUTING_BUDGET_EXCEEDED");
            queries.add(record);save();System.out.println("Query "+q.getString("id")+" selectionPassed="+record.get("selectionPassed"));
            // Allow an already-dispatched CPU inference to finish; this is outside measured runtime latency.
            if(events.stream().anyMatch(e->"FALLBACK".equals(e.get("status")))) Thread.sleep(10000);
        }
        boolean routingPass=queries.stream().allMatch(q->Boolean.TRUE.equals(q.get("selectionPassed")));
        boolean degraded=queries.stream().anyMatch(q->((List<Map<String,Object>>)q.get("events")).stream()
                .anyMatch(e->"FALLBACK".equals(e.get("status"))));
        report.put("status",!routingPass?"FAIL_ROUTING_CASES":degraded?"PARTIAL_RETRIEVAL_FALLBACK":"PASS");save();
    }
    static SkillApplicabilityPort realApplicability() throws Exception {
        // Read-only configuration ports for this isolated harness. Decisions come from the real adapter and gateway.
        var model=new AiClientModelDefinition(1L,"ops-acceptance-luna","ops-acceptance-authorized-models","gpt-5.6-luna","openai","CHAT","real probe",1,null,null);
        var api=new AiClientApiDefinition(1L,"ops-acceptance-authorized-models","Authorized gateway","openai",
                System.getenv("PROBE_MODEL_BASE_URL"),System.getenv("PROBE_MODEL_API_KEY"),"/v1/chat/completions","/v1/embeddings",1,null,null);
        var models=(AiClientModelCatalogPort)Proxy.newProxyInstance(SemanticCatalogProbe.class.getClassLoader(),new Class<?>[]{AiClientModelCatalogPort.class},
                (proxy,method,args)->{if(method.getName().equals("listEnabled")) return List.of(model);throw new UnsupportedOperationException(method.getName());});
        var apis=(AiClientApiCatalogPort)Proxy.newProxyInstance(SemanticCatalogProbe.class.getClassLoader(),new Class<?>[]{AiClientApiCatalogPort.class},
                (proxy,method,args)->{if(method.getName().equals("findByApiId") && api.apiId().equals(args[0])) return api;throw new UnsupportedOperationException(method.getName());});
        var availability=new SpringAiModelAvailabilityAdapter();
        var enabled=SpringAiModelAvailabilityAdapter.class.getDeclaredField("modelCallsEnabled");enabled.setAccessible(true);enabled.set(availability,true);
        return new OpsSkillApplicabilityModelAdapter(models,apis,availability,new OpsSecretResolver(new StandardEnvironment()));
    }
    static SkillRuntimeCandidate candidate(JSONObject d,String project) {
        String id=d.getString("id"),description=d.getString("description");
        return new SkillRuntimeCandidate(id,project,"PROJECT",d.getString("name"),description,1,"synthetic-"+id,"package-"+id,"manifest-"+id,Map.of(),"SKILL.md","ACTIVE","AUTO",10,
                new SkillRoutingProfile("GENERAL","",description,d.getJSONArray("use").toJavaList(String.class),d.getJSONArray("exclude").toJavaList(String.class),d.getJSONArray("tags").toJavaList(String.class)));
    }
}
