import cn.lgs.orbisops.application.config.*;
import cn.lgs.orbisops.infrastructure.adapter.model.SpringAiModelAvailabilityAdapter;
import cn.lgs.orbisops.trigger.application.skill.OpsSkillApplicabilityModelAdapter;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import cn.lgs.orbisops.domain.skill.model.*;
import com.alibaba.fastjson.JSON;
import org.springframework.core.env.StandardEnvironment;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;

/** Production adapter, real Luna connection, existing read-only context snapshot. */
public class SkillApplicabilityProbe {
    public static void main(String[] args) throws Exception {
        var bundle=JSON.parseObject(Files.readString(Path.of(args[0])));
        var candidates=new ArrayList<SkillRuntimeCandidate>();
        for(var raw:bundle.getJSONArray("skillCatalogRefs")) {
            var c=JSON.parseObject(JSON.toJSONString(raw));
            candidates.add(new SkillRuntimeCandidate(c.getString("skillId"),c.getString("projectId"),c.getString("scope"),
                c.getString("name"),c.getString("description"),c.getIntValue("version"),c.getString("skillHash"),
                c.getString("packageHash"),c.getString("manifestHash"),Map.of(),c.getString("entrypoint"),c.getString("status"),c.getString("updateMode"),0,
                new SkillRoutingProfile(c.getString("category"),c.getString("subcategory"),c.getString("description"),
                    c.getJSONArray("whenToUse").toJavaList(String.class),c.getJSONArray("whenNotToUse").toJavaList(String.class),c.getJSONArray("keywords").toJavaList(String.class))));
        }
        var model=new AiClientModelDefinition(1L,"ops-acceptance-luna","authorized","gpt-5.6-luna","openai","CHAT","real probe",1,null,null);
        var api=new AiClientApiDefinition(1L,"authorized","Authorized gateway","openai",System.getenv("PROBE_MODEL_BASE_URL"),System.getenv("PROBE_MODEL_API_KEY"),"/v1/chat/completions","/v1/embeddings",1,null,null);
        var models=(AiClientModelCatalogPort)Proxy.newProxyInstance(SkillApplicabilityProbe.class.getClassLoader(),new Class<?>[]{AiClientModelCatalogPort.class},
            (proxy,method,values)->{if(method.getName().equals("listEnabled")) return List.of(model);throw new UnsupportedOperationException();});
        var apis=(AiClientApiCatalogPort)Proxy.newProxyInstance(SkillApplicabilityProbe.class.getClassLoader(),new Class<?>[]{AiClientApiCatalogPort.class},
            (proxy,method,values)->{if(method.getName().equals("findByApiId")) return api;throw new UnsupportedOperationException();});
        var availability=new SpringAiModelAvailabilityAdapter();var enabled=SpringAiModelAvailabilityAdapter.class.getDeclaredField("modelCallsEnabled");enabled.setAccessible(true);enabled.set(availability,true);
        var adapter=new OpsSkillApplicabilityModelAdapter(models,apis,availability,new OpsSecretResolver(new StandardEnvironment()));
        long start=System.nanoTime();var result=new LinkedHashMap<String,Object>();result.put("model","gpt-5.6-luna");
        try {
            var decisions=adapter.assess(bundle.getString("projectId"),bundle.getJSONObject("taskContext").getString("userRequest"),candidates);
            result.put("status","PASS_REAL_MODEL_CALL");result.put("decisions",decisions);
        } catch(RuntimeException error) {
            result.put("status","FAIL");result.put("errorType",error.getClass().getSimpleName());
            String message=error.getMessage();result.put("reason",message!=null && message.matches("[A-Z][A-Z0-9_]{1,100}")?message:"REDACTED_PROVIDER_ERROR");
        }
        result.put("seconds",(System.nanoTime()-start)/1e9);
        String encoded=cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringify(result);
        Files.writeString(Path.of(args[1]),encoded);
        System.out.println(encoded);
    }
}
