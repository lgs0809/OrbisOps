package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.domain.skill.model.SkillExperienceConsolidationSample;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SkillEvolutionSourceSetPolicyTest {
    final SkillEvolutionSourceSetPolicy policy=new SkillEvolutionSourceSetPolicy();
    SkillExperienceConsolidationSample sample(int id,String project,String condition) {
        String raw=CanonicalJson.stringify(Map.of("format","accepted-task-episode-v1","projectId",project,
                "runId","run-"+id,"sessionId","session-"+id,"sourceId","acceptance-"+id,"episodeId","task-"+id,
                "messages",List.of("SYNTHETIC whole task, including original question and correction")));
        return new SkillExperienceConsolidationSample("observation-"+id,"run-"+id,"session-"+id,"SUCCESS","SUCCEEDED",
                "template","trajectory","summary",1,List.of(),"acceptance-"+id,CanonicalObjectHasher.sha256Text(raw),raw,"task-"+id,condition);
    }
    @Test void fullIndependentSourcesMustIncludeTheCurrentFrozenTaskAndTwoConditions() {
        var a=sample(1,"p","condition-a");var b=sample(2,"p","condition-a");var c=sample(3,"p","condition-b");
        assertDoesNotThrow(()->policy.requireUsable(List.of(a,b,c),"p",c.runId(),c.sourceHash()));
        for(var bad:List.of(List.of(a,b),List.of(a,a,c),List.of(a,b,sample(3,"p","condition-a")),
                List.of(a,b,sample(3,"different-project","condition-b")))) {
            assertThrows(IllegalStateException.class,()->policy.requireUsable(bad,"p",c.runId(),c.sourceHash()));
        }
        assertThrows(IllegalStateException.class,()->policy.requireUsable(List.of(a,b,c),"p",c.runId(),"different-hash"));
    }
    @Test void legacySummariesTamperedFullTextAndOverLimitSetsCannotBecomeAuthorInputs() {
        var a=sample(1,"p","a");var b=sample(2,"p","b");var c=sample(3,"p","a");
        var legacy=new SkillExperienceConsolidationSample("obs","run","s","SUCCESS","SUCCEEDED","t","r","summary",1,List.of());
        var corrupt=new SkillExperienceConsolidationSample(c.observationId(),c.runId(),c.sessionId(),c.observationType(),c.outcome(),
                c.taskTemplateHash(),c.trajectoryHash(),c.summary(),1,List.of(),c.sourceId(),c.sourceHash(),c.episodeJson()+" ",c.taskEpisodeId(),c.conditionKey());
        for(var bad:List.of(List.of(a,b,legacy),List.of(a,b,corrupt)))
            assertThrows(IllegalStateException.class,()->policy.requireUsable(bad,"p",a.runId(),a.sourceHash()));
        var paged=java.util.stream.IntStream.rangeClosed(1,21).mapToObj(i->sample(i,"p","c-"+i)).toList();
        assertDoesNotThrow(()->policy.requireUsable(paged,"p",a.runId(),a.sourceHash()));
        var oversized=java.util.stream.IntStream.rangeClosed(1,SkillSourceBatchPolicy.ARCHIVE_LIMIT+1).mapToObj(i->sample(i,"p","c-"+i)).toList();
        assertThrows(IllegalStateException.class,()->policy.requireUsable(oversized,"p",a.runId(),a.sourceHash()));
    }
}
