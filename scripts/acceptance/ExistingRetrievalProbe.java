import cn.lgs.orbisops.trigger.application.skill.OpsSkillRetrievalHttpClient;
import cn.lgs.orbisops.application.skill.*;
import cn.lgs.orbisops.domain.skill.model.*;
import cn.lgs.orbisops.domain.skill.service.*;
import java.util.*;
import com.alibaba.fastjson.JSON;

/** Real local reranker timeout through production Java code; synthetic authorized catalog. */
public class ExistingRetrievalProbe {
    public static void main(String[] args) {
        var client=new OpsSkillRetrievalHttpClient("http://127.0.0.1:8110/orbisops",System.getenv("RETRIEVAL_PROBE_KEY"),
            "9f2f7e710d6d81056aa5c0a4f04764fec6bb7bda","4bd860ac4f15ad1897a214615cccc700f8f71818");
        var settings=new SkillRuntimeSelectionSettings(20,3,3,0.9,0.99,0.42,20,0.12,0.65,true,20,0.35);
        var candidates=List.of(candidate("redis","inspect Redis connection failures"),candidate("mysql","inspect MySQL deadlocks"));
        SkillSemanticScorePort semantic=(q,c)->Map.of();
        var fallback=new SkillHybridRetrieval(semantic,(q,c)->Map.of(),settings).select("p","inspect Redis connection failures",candidates,Set.of(),3);
        var errors=new ArrayList<String>();
        int[] calls={0};
        SkillRerankPort actual=(q,c)->{
            calls[0]++;
            try {return client.rerank(q,c.stream().map(v->Map.of("id",v.skillId(),"text",v.description()+". Check connection pool saturation, dependency health, recent deployment changes, timeout metrics and correlated request logs. Compare test and production observations before proposing any change.".repeat(8))).toList());}
            catch(IllegalStateException e) {errors.add(e.getMessage());throw e;}
        };
        long start=System.nanoTime();
        var result=new SkillHybridRetrieval(semantic,actual,settings).select("p","inspect Redis connection failures",candidates,Set.of(),3);
        double seconds=(System.nanoTime()-start)/1e9;
        var ids=result.catalog().stream().map(v->v.candidate().skillId()).toList();
        var expected=fallback.catalog().stream().map(v->v.candidate().skillId()).toList();
        boolean timeout=errors.equals(List.of("SKILL_RETRIEVAL_UNAVAILABLE"));
        boolean pass=timeout && calls[0]==1 && seconds<5.9 && ids.equals(expected) && !ids.isEmpty();
        System.out.println(JSON.toJSONString(Map.of("status",pass?"PASS":"FAIL","seconds",seconds,"calls",calls[0],"errors",errors,"retainedAuthorizedOrder",ids,"expectedOrder",expected,"scope","REAL_MODEL_TIMEOUT_SYNTHETIC_AUTHORIZED_CATALOG")));
        if(!pass) throw new AssertionError("Timeout fallback acceptance failed; inspect result without treating provider success or busy as timeout proof");
    }
    static SkillRuntimeCandidate candidate(String id,String use) {
        return new SkillRuntimeCandidate(id,"p","PROJECT",id,use,1,"h-"+id,"p-"+id,"m-"+id,Map.of(),"SKILL.md","ACTIVE","AUTO",10,
            new SkillRoutingProfile("GENERAL","",use,List.of(use),List.of("delete production"),List.of(id)));
    }
}
