package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.SkillRerankPort;
import cn.lgs.orbisops.domain.skill.model.SkillRuntimeCandidate;
import cn.lgs.orbisops.trigger.ops.OpsAgentLlmClient;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** LLM reranker that sees only bounded routing metadata, never Skill bodies. */
@Slf4j
@Component
public final class OpsSkillLlmRerankAdapter implements SkillRerankPort {

    private static final String SYSTEM_PROMPT = """
            你是 Skill 路由精排器。根据用户目标和候选 Skill 的轻量路由画像进行排序。
            必须遵守：
            1. whenNotToUse 命中时降低该 Skill 排名；
            2. 不得返回候选列表之外的 skillId；
            3. 不得根据 Skill 执行权限做放行判断；
            4. 只返回 JSON：{"ranking":[{"skillId":"...","score":0.0}]}；
            5. score 范围为 0 到 1，越匹配越高。
            """;

    private final ObjectProvider<OpsAgentLlmClient> llmClientProvider;

    public OpsSkillLlmRerankAdapter(ObjectProvider<OpsAgentLlmClient> llmClientProvider) {
        this.llmClientProvider = llmClientProvider;
    }

    @Override
    public Map<String, Double> scores(String query, List<SkillRuntimeCandidate> candidates) {
        if (query == null || query.isBlank() || candidates == null || candidates.size() < 2) return Map.of();
        OpsAgentLlmClient client = llmClientProvider.getIfAvailable();
        if (client == null || !client.available()) return Map.of();
        List<SkillRuntimeCandidate> bounded = candidates.stream()
                .filter(candidate -> candidate != null && candidate.activeAtUse())
                .limit(20)
                .toList();
        if (bounded.size() < 2) return Map.of();
        try {
            JSONObject response = client.chatJsonObject(
                    "skill-routing-reranker",
                    SYSTEM_PROMPT,
                    request(query, bounded));
            return parse(response, bounded);
        } catch (RuntimeException exception) {
            log.warn("Skill LLM 精排不可用，保留 embedding/词法排序 reason={}", exception.getMessage());
            return Map.of();
        }
    }

    private String request(String query, List<SkillRuntimeCandidate> candidates) {
        JSONArray items = new JSONArray();
        for (SkillRuntimeCandidate candidate : candidates) {
            JSONObject item = new JSONObject(true);
            item.put("skillId", candidate.skillId());
            item.put("name", candidate.name());
            item.put("category", candidate.routingProfile().category());
            item.put("subcategory", candidate.routingProfile().subcategory());
            item.put("description", candidate.routingProfile().searchDescription());
            item.put("whenToUse", candidate.routingProfile().useCases());
            item.put("whenNotToUse", candidate.routingProfile().exclusions());
            item.put("keywords", candidate.routingProfile().keywords());
            items.add(item);
        }
        JSONObject payload = new JSONObject(true);
        payload.put("userGoal", query);
        payload.put("candidates", items);
        return payload.toJSONString();
    }

    private Map<String, Double> parse(JSONObject response,
                                      List<SkillRuntimeCandidate> candidates) {
        if (response == null) return Map.of();
        JSONArray ranking = response.getJSONArray("ranking");
        if (ranking == null || ranking.isEmpty()) return Map.of();
        Set<String> allowed = candidates.stream()
                .map(SkillRuntimeCandidate::skillId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<String, Double> scores = new LinkedHashMap<>();
        for (Object value : ranking) {
            if (!(value instanceof JSONObject item)) continue;
            String skillId = item.getString("skillId");
            Double score = item.getDouble("score");
            if (skillId == null || !allowed.contains(skillId)
                    || score == null || score.isNaN() || score.isInfinite()) {
                continue;
            }
            scores.putIfAbsent(skillId, Math.max(0D, Math.min(1D, score)));
        }
        return Map.copyOf(scores);
    }
}
