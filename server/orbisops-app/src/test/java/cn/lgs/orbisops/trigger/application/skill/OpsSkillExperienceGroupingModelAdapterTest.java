package cn.lgs.orbisops.trigger.application.skill;
import cn.lgs.orbisops.domain.skill.model.SkillMethodMemory.*;
import cn.lgs.orbisops.trigger.ops.skill.OpsSkillAuthoringModelClient;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class OpsSkillExperienceGroupingModelAdapterTest {
    @Test void extractionRequiresCompleteMethodAndPreservesTheResolvedModelAudit() {
        var model=mock(OpsSkillAuthoringModelClient.class);var adapter=new OpsSkillExperienceGroupingModelAdapter(model,mock(OpsSkillRetrievalHttpClient.class));
        when(model.generate(anyString(),anyString())).thenReturn(JSON.parseObject("""
          {"method":{"goal":"只读核验","conditions":["有采样"],"steps":["检查原始计数器"],"acceptance":["交叉核对"],"toolCategories":["metrics"]},"authoringModel":"gpt-5.6-terra"}
          """));
        var extracted=adapter.extract("SYNTHETIC accepted task");assertEquals("只读核验",extracted.method().goal());assertTrue(extracted.auditJson().contains("gpt-5.6-terra"));
        when(model.generate(anyString(),anyString())).thenReturn(JSON.parseObject("{\"method\":{\"goal\":\"missing evidence\"}}"));
        assertThrows(IllegalArgumentException.class,()->adapter.extract("SYNTHETIC input"));
    }
    @Test void emptyRecallCreatesPrivateGroupWithoutPretendingToHaveModelConfirmation() {
        var model=mock(OpsSkillAuthoringModelClient.class);var adapter=new OpsSkillExperienceGroupingModelAdapter(model,mock(OpsSkillRetrievalHttpClient.class));
        var method=new Method("g",List.of("c"),List.of("s"),List.of("a"),List.of("t"));
        var decision=adapter.decide(new Fact("s","h","e",1,method),List.of());
        assertEquals("CREATE",decision.action());assertTrue(decision.auditJson().contains("NO_CURRENT_GROUP_RECALLED"));verifyNoInteractions(model);
    }
    @Test void largeGroupKeepsEveryMethodBoundaryWithoutTheSingleFactArrayLimit() {
        var facts=java.util.stream.IntStream.range(0,250).mapToObj(i -> new Fact("source-"+i,"h-"+i,"e-"+i,1,
            new Method("goal-"+i,List.of("condition-"+i),List.of("step-"+i),List.of("accept-"+i),List.of("tool-"+i)))).toList();
        var representative=cn.lgs.orbisops.domain.skill.model.SkillMethodMemory.representative(facts);
        var group=new Group("g","p",1,"hash",representative,facts);
        assertEquals(facts.size(),((List<?>)group.view().get("sources")).size());
        assertTrue(group.document().contains("condition-249"));
        var model=mock(OpsSkillAuthoringModelClient.class);
        when(model.generate(anyString(),anyString())).thenAnswer(invocation -> {
            var input=JSON.parseObject(invocation.getArgument(1,String.class));
            var candidate=input.getJSONArray("candidateGroups").getJSONObject(0);
            assertEquals(250,candidate.getJSONArray("sources").size());
            assertEquals("condition-249",candidate.getJSONArray("sources").getJSONObject(249)
                    .getJSONObject("method").getJSONArray("conditions").getString(0));
            return JSON.parseObject("{\"action\":\"CREATE\",\"groupId\":\"\",\"reason\":\"last source has incompatible condition\"}");
        });
        var adapter=new OpsSkillExperienceGroupingModelAdapter(model,mock(OpsSkillRetrievalHttpClient.class));
        assertEquals("CREATE",adapter.decide(facts.get(0),List.of(group)).action());
        var reversed=new java.util.ArrayList<>(facts);java.util.Collections.reverse(reversed);
        assertEquals(representative,cn.lgs.orbisops.domain.skill.model.SkillMethodMemory.representative(reversed));
    }

}
