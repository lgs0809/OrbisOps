package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillRoutingProfile;
import cn.lgs.orbisops.domain.skill.model.SkillRuntimeCandidate;
import cn.lgs.orbisops.trigger.ops.OpsAgentLlmClient;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsSkillLlmRerankAdapterTest {

    @Test
    void reranksOnlyAllowedCandidatesAndIgnoresUnknownIds() {
        OpsAgentLlmClient client = mock(OpsAgentLlmClient.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<OpsAgentLlmClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(client);
        when(client.available()).thenReturn(true);
        JSONObject response = JSON.parseObject("""
                {
                  "ranking": [
                    {"skillId": "quarterly-ppt", "score": 0.98},
                    {"skillId": "invented-skill", "score": 1.0},
                    {"skillId": "code-generator", "score": 0.05}
                  ]
                }
                """);
        when(client.chatJsonObject(
                eq("skill-routing-reranker"),
                contains("whenNotToUse"),
                contains("quarterly-ppt"))).thenReturn(response);
        OpsSkillLlmRerankAdapter adapter = new OpsSkillLlmRerankAdapter(provider);

        Map<String, Double> scores = adapter.scores(
                "生成季度汇报 PPT",
                List.of(
                        skill("quarterly-ppt", "DOCUMENT", List.of("后端开发")),
                        skill("code-generator", "DEVELOPMENT", List.of("制作 PPT"))));

        assertEquals(0.98D, scores.get("quarterly-ppt"));
        assertEquals(0.05D, scores.get("code-generator"));
        assertTrue(!scores.containsKey("invented-skill"));
        verify(client).chatJsonObject(
                eq("skill-routing-reranker"),
                contains("whenNotToUse"),
                contains("quarterly-ppt"));
    }

    @Test
    void degradesToEmptyScoresWhenModelUnavailable() {
        @SuppressWarnings("unchecked")
        ObjectProvider<OpsAgentLlmClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        Map<String, Double> scores = new OpsSkillLlmRerankAdapter(provider).scores(
                "query",
                List.of(
                        skill("one", "GENERAL", List.of()),
                        skill("two", "GENERAL", List.of())));

        assertTrue(scores.isEmpty());
    }

    private SkillRuntimeCandidate skill(String id,
                                        String category,
                                        List<String> exclusions) {
        SkillRoutingProfile profile = new SkillRoutingProfile(
                category, "", id, List.of(id), exclusions, List.of(id));
        return new SkillRuntimeCandidate(
                id, "demo-project", "PROJECT", id, id, 1,
                "hash-" + id, "package-" + id, "manifest-" + id,
                Map.of("SKILL.md", "artifact-" + id), "SKILL.md",
                "ACTIVE", "AUTO", 10, profile);
    }
}
