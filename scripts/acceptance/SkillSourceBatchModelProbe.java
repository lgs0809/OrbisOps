package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.application.config.*;
import cn.lgs.orbisops.application.skill.SkillAuthoringProgressPort;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.domain.skill.service.SkillSourceBatchPolicy;
import cn.lgs.orbisops.infrastructure.adapter.model.SpringAiModelAvailabilityAdapter;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Actual Terra transport/coordinator test with explicitly synthetic sources; never publishes a Skill. */
public class SkillSourceBatchModelProbe {
    private static final class Interruption extends RuntimeException {}
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        var input=CanonicalJson.parseObject(new String(System.in.readAllBytes(),StandardCharsets.UTF_8));
        var binding=CanonicalJson.parseObject(System.getenv("OPS_BATCH_PROBE_BINDING"));
        var model=new AiClientModelDefinition(null,(String)binding.get("modelId"),(String)binding.get("apiId"),
                (String)binding.get("modelName"),"CHAT","SKILL_AUTHORING","actual existing catalog binding",1,null,null);
        var api=new AiClientApiDefinition(null,model.apiId(),"existing provider","OPENAI_COMPATIBLE",
                (String)binding.get("baseUrl"),System.getenv("OPS_BATCH_PROBE_API_KEY"),(String)binding.get("path"),null,1,null,null);
        var models=(AiClientModelCatalogPort)Proxy.newProxyInstance(SkillSourceBatchModelProbe.class.getClassLoader(),
                new Class<?>[]{AiClientModelCatalogPort.class},(p,m,a)->{
                    if(m.getName().equals("listEnabled"))return List.of(model);
                    throw new UnsupportedOperationException("READ_ONLY_MODEL_PROBE");});
        var apis=(AiClientApiCatalogPort)Proxy.newProxyInstance(SkillSourceBatchModelProbe.class.getClassLoader(),
                new Class<?>[]{AiClientApiCatalogPort.class},(p,m,a)->{
                    if(m.getName().equals("findByApiId")&&model.apiId().equals(a[0]))return api;
                    throw new UnsupportedOperationException("READ_ONLY_MODEL_PROBE");});
        Path ledger=Path.of(args[0]);boolean stop=Boolean.parseBoolean(args[1]);
        var archive=(Map<String,Object>)input.get("archive");
        if(!Boolean.TRUE.equals(input.get("synthetic"))||((List<?>)archive.get("consolidatedExperiences")).size()!=21)
            throw new IllegalArgumentException("EXPLICIT_SYNTHETIC_21_SOURCE_FIXTURE_REQUIRED");
        String archiveHash=CanonicalObjectHasher.sha256(archive);
        var report=new LinkedHashMap<String,Object>();
        report.put("scope","SYNTHETIC_SOURCE_REAL_TERRA_PROTOCOL_ONLY_NO_BUSINESS_PUBLICATION");
        report.put("archiveHash",archiveHash);report.put("sourceCount",21);
        var calls=new ArrayList<Map<String,Object>>();var hits=new ArrayList<Integer>();
        var stored=new LinkedHashMap<String,Object>();
        if(Files.exists(ledger)) {
            stored.putAll(CanonicalJson.parseObject(Files.readString(ledger)));
            if(!archiveHash.equals(stored.get("archiveHash")))throw new IllegalStateException("PROBE_ARCHIVE_CHANGED");
        } else stored.put("archiveHash",archiveHash);
        SkillAuthoringProgressPort progress=new SkillAuthoringProgressPort(){
            public Optional<Map<String,Object>> read(int index,String hash){
                var row=(Map<String,Object>)stored.get(String.valueOf(index));if(row==null)return Optional.empty();
                var review=(Map<String,Object>)row.get("review");
                if(!hash.equals(row.get("inputHash"))||!CanonicalObjectHasher.sha256(review).equals(row.get("reviewHash")))
                    throw new IllegalStateException("PROBE_PAGE_HASH_MISMATCH");
                hits.add(index);return Optional.of(review);
            }
            public Map<String,Object> save(int index,String hash,Map<String,Object> review){
                if(stored.containsKey(String.valueOf(index)))throw new IllegalStateException("PROBE_IMMUTABLE_PAGE");
                stored.put(String.valueOf(index),Map.of("inputHash",hash,"reviewHash",CanonicalObjectHasher.sha256(review),"review",review));
                try{Files.writeString(ledger,CanonicalJson.stringify(stored),StandardOpenOption.CREATE_NEW);}
                catch(FileAlreadyExistsException exists){
                    try{Path next=ledger.resolveSibling(ledger.getFileName()+".next");Files.writeString(next,CanonicalJson.stringify(stored),StandardOpenOption.CREATE_NEW);
                        Files.move(next,ledger,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
                    catch(Exception failure){throw new IllegalStateException("PROBE_JOURNAL_WRITE_FAILED",failure);}
                }catch(Exception failure){throw new IllegalStateException("PROBE_JOURNAL_WRITE_FAILED",failure);}
                if(stop&&index==0)throw new Interruption();return review;
            }
        };
        try(var context=new AnnotationConfigApplicationContext()){
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("existing-enabled-model-config",
                    Map.of("orbisops.ai.model-calls-enabled",binding.get("enabled"))));
            context.register(SpringAiModelAvailabilityAdapter.class);context.refresh();
            var client=new OpsSkillAuthoringModelClient(models,apis,context.getBean(SpringAiModelAvailabilityAdapter.class),
                    new OpsSecretResolver(context.getEnvironment()));
            try{
                var result=OpsSkillSourceBatchReview.generate("""
                    这是合成来源的模型协议验收，不是生产任务，不创建或发布Skill。比较全部来源与已有方法，输出JSON：
                    {"decision":"NO_PUBLICATION 或 REVIEW_REQUIRED","reason":"证据判断","conflicts":["冲突"],"limitations":["限制"]}。
                    有条件冲突时需说明对应sourceId；保留sampleCountLowerBound、窗口缺口和不得宣称精确请求总量的限制。
                    不执行工具，不编造来源，不宣称这些合成任务已在实际系统验收。
                    """,archive,progress,(instruction,raw)->{
                    var reply=client.generate(instruction,raw);var page=CanonicalJson.parseObject(raw);
                    var call=new LinkedHashMap<String,Object>();call.put("index",page.getOrDefault("sourceBatchIndex","FINAL"));
                    call.put("inputHash",CanonicalObjectHasher.sha256Text(raw));call.put("replyHash",CanonicalObjectHasher.sha256(reply));
                    call.put("actualModel",reply.get("authoringModel"));call.put("modelId",reply.get("modelId"));
                    call.put("bindingHash",reply.get("authoringBindingHash"));call.put("encoding",reply.get("modelInputEncoding"));
                    // This probe contains only synthetic source text. Retain the parsed reply to diagnose protocol failures.
                    call.put("reply",new LinkedHashMap<>(reply));
                    calls.add(call);return reply;
                });
                var reviews=List.of((Map<String,Object>)((Map<?,?>)stored.get("0")).get("review"),
                        (Map<String,Object>)((Map<?,?>)stored.get("1")).get("review"));
                if(!new SkillSourceBatchPolicy().audit(archive,reviews).equals(result.get("sourceBatchReviewAudit")))
                    throw new IllegalStateException("ACTUAL_PAGE_AUDIT_MISMATCH");
                String answer=CanonicalJson.stringify(result);
                if(!Set.of("NO_PUBLICATION","REVIEW_REQUIRED").contains(result.get("decision"))
                        ||!answer.contains("synthetic-batch-20")||!answer.contains("sampleCountLowerBound"))
                    throw new IllegalStateException("LATE_PAGE_LIMITATION_NOT_PRESERVED");
                report.put("status","PASS_ACTUAL_MODEL_BATCH_PROTOCOL");report.put("result",result);
            }catch(Interruption expected){report.put("status","EXPECTED_INTERRUPTION_AFTER_PAGE_COMMIT");}
            catch(RuntimeException failure){report.put("status","FAIL");report.put("failureType",failure.getClass().getSimpleName());
                // Provider messages may contain URLs or keys; retain only the fixed diagnostic code.
                report.put("diagnostic",failure.getMessage()!=null&&failure.getMessage().matches("[A-Z_]+")?failure.getMessage():"MODEL_OR_PROTOCOL_FAILURE");}
        }
        report.put("calls",calls);report.put("cacheHits",hits);report.put("businessWrites",0);
        Files.writeString(Path.of(args[2]),CanonicalJson.stringify(report),StandardOpenOption.CREATE_NEW);
        if("FAIL".equals(report.get("status")))System.exit(1);
    }
}
