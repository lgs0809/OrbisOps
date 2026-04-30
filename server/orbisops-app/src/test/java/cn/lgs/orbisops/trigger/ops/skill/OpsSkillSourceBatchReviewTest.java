package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.application.skill.SkillAuthoringProgressPort;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.skill.service.SkillSourceBatchPolicy;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic model protocol tests; actual provider/model quality is validated separately. */
class OpsSkillSourceBatchReviewTest {
    Map<String,Object> archive(int count) {
        return Map.of("relatedSkills",List.of(Map.of("skillId","existing","content","SYNTHETIC METHOD")),
                "primarySourceIds",List.of("s0","s1","s2"),"consolidatedExperiences",
                java.util.stream.IntStream.range(0,count).mapToObj(i->Map.<String,Object>of("sourceId","s"+i,"sourceHash","h"+i,
                        "acceptedTaskEpisode","SYNTHETIC FULL SOURCE "+i)).toList());
    }
    JSONObject review(Map<String,Object> page) {
        return new JSONObject(Map.of("evidenceSufficient",true,"sourceReviews",((List<Map<String,Object>>)page.get("consolidatedExperiences"))
                .stream().map(s->Map.of("sourceId",s.get("sourceId"),"sourceHash",s.get("sourceHash"),"conditions",List.of("isolated read only"),
                        "effectiveSteps",List.of("read authoritative receipt"),"acceptance",List.of("compare actual value"),
                        "conflicts",s.get("sourceId").equals("s20")?List.of("last-page condition contradicts the existing method"):List.of(),
                        "limitations",List.of("SYNTHETIC protocol fixture"),"methodRelation","compare with existing method")).toList()));
    }
    static class Progress implements SkillAuthoringProgressPort {
        final Map<Integer,Map<String,Object>> saved=new HashMap<>();
        final Map<Integer,String> hashes=new HashMap<>();
        public Optional<Map<String,Object>> read(int index,String hash) {
            if(hashes.containsKey(index)) assertEquals(hashes.get(index),hash);return Optional.ofNullable(saved.get(index));
        }
        public Map<String,Object> save(int index,String hash,Map<String,Object> value) {hashes.put(index,hash);saved.put(index,value);return value;}
    }
    @Test void laterPageConflictSurvivesTheSingleFinalDecisionWithoutSendingTwentyOneWholeTasks() {
        var progress=new Progress();var calls=new AtomicInteger();
        var result=OpsSkillSourceBatchReview.generate("AUTHOR",archive(41),progress,(instruction,raw)->{
            var input=CanonicalJson.parseObject(raw);calls.incrementAndGet();
            if(input.containsKey("reviewedSourceBatches")) {
                assertEquals(3,((List<?>)input.get("reviewedSourceBatches")).size());
                assertTrue(raw.contains("last-page condition contradicts"));
                assertFalse(raw.contains("SYNTHETIC FULL SOURCE"));
                return new JSONObject(Map.of("patchType","NO_CHANGE","reason","conflicting condition retained"));
            }
            assertTrue(((List<?>)input.get("consolidatedExperiences")).size()<=20);return review(input);
        });
        assertEquals(4,calls.get());assertEquals(3,progress.saved.size());
        assertEquals(3,((List<?>)result.get("sourceBatchReviewAudit")).size());
        assertEquals("NO_CHANGE",result.get("patchType"));
    }
    @Test void retryUsesTheCommittedPageAndCannotDeclareSuccessBeforeAllPagesAreReviewed() {
        var progress=new Progress();var calls=new AtomicInteger();
        assertThrows(IllegalStateException.class,()->OpsSkillSourceBatchReview.generate("AUTHOR",archive(21),progress,(i,raw)->{
            if(calls.incrementAndGet()==2) throw new IllegalStateException("SYNTHETIC transport interruption");
            return review(CanonicalJson.parseObject(raw));
        }));
        assertEquals(1,progress.saved.size());
        var retries=new AtomicInteger();
        OpsSkillSourceBatchReview.generate("AUTHOR",archive(21),progress,(i,raw)->{
            retries.incrementAndGet();var input=CanonicalJson.parseObject(raw);
            if(input.containsKey("reviewedSourceBatches")) return new JSONObject(Map.of("patchType","NO_CHANGE","reason","all pages compared"));
            assertEquals(1,((Number)input.get("sourceBatchIndex")).intValue());return review(input);
        });
        assertEquals(2,retries.get());assertEquals(2,progress.saved.size());
    }
    @Test void omittedDuplicateOrChangedSourceReviewIsRejectedAndNeverCached() {
        var page=new SkillSourceBatchPolicy().pages(archive(21)).get(0);var policy=new SkillSourceBatchPolicy();
        for(String mode:List.of("omit","duplicate","changedHash")) {
            var reply=new JSONObject(new LinkedHashMap<>(review(page)));var rows=new ArrayList<>((List<Map<String,Object>>)reply.get("sourceReviews"));
            if(mode.equals("omit")) rows.remove(0);
            if(mode.equals("duplicate")) rows.set(1,rows.get(0));
            if(mode.equals("changedHash")) {var row=new LinkedHashMap<>(rows.get(0));row.put("sourceHash","other");rows.set(0,row);}
            reply.put("sourceReviews",rows);assertThrows(IllegalStateException.class,()->policy.validateReview(page,reply));
        }
        var progress=new Progress();
        assertThrows(IllegalStateException.class,()->OpsSkillSourceBatchReview.generate("AUTHOR",archive(21),progress,
                (i,r)->new JSONObject(Map.of("evidenceSufficient",false))));
        assertTrue(progress.saved.isEmpty());
    }
    @Test void exactTwentyRetainsExistingSingleCallAndDoesNotRequireProgressStore() {
        var calls=new AtomicInteger();OpsSkillSourceBatchReview.generate("AUTHOR",archive(20),null,(i,raw)->{
            calls.incrementAndGet();assertTrue(raw.contains("SYNTHETIC FULL SOURCE 19"));return new JSONObject(Map.of("patchType","NO_CHANGE"));
        });assertEquals(1,calls.get());
    }
}
