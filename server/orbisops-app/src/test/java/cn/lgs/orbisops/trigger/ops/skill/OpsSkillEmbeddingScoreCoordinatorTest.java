package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.trigger.application.skill.OpsSkillRetrievalHttpClient;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpsSkillEmbeddingScoreCoordinatorTest {
    @Test void usesDistinctQueryInstructionsAndCachesOnlyWithinOneModelRevision() {
        var model=mock(OpsSkillRetrievalHttpClient.class);
        when(model.modelIdentity()).thenReturn("qwen@revision-one");
        when(model.embed("orders",true)).thenReturn(new float[]{1,0});
        when(model.embed("cache",true)).thenReturn(new float[]{0,1});
        when(model.embed("order guide",false)).thenReturn(new float[]{1,0});
        when(model.embed("cache guide",false)).thenReturn(new float[]{0,1});
        var coordinator=new OpsSkillEmbeddingScoreCoordinator(new OpsSkillSemanticSettings(true,2,128));
        var docs=List.of(new OpsSkillSemanticMatcher.SkillDocument("order","o1","order guide"),
                new OpsSkillSemanticMatcher.SkillDocument("cache","c1","cache guide"));
        assertTrue(coordinator.scores(model,"orders",docs).get("order") > .9);
        assertTrue(coordinator.scores(model,"cache",docs).get("cache") > .9);
        verify(model,times(1)).embed("order guide",false);
        verify(model,times(1)).embed("cache guide",false);
        when(model.modelIdentity()).thenReturn("qwen@revision-two");
        coordinator.scores(model,"orders",docs);
        verify(model,times(2)).embed("order guide",false);
        verify(model,times(2)).embed("cache guide",false);
    }
}
