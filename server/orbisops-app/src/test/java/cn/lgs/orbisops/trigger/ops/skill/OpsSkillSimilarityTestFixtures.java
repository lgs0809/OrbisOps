package cn.lgs.orbisops.trigger.ops.skill;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class OpsSkillSimilarityTestFixtures {

    private OpsSkillSimilarityTestFixtures() {
    }

    static Map<String, Object> candidate(String category) {
        Map<String, Object> routing = new LinkedHashMap<>();
        routing.put("category", category);
        routing.put("subcategory", "ORDER_RECOVERY");
        routing.put("whenToUse", List.of("订单失败排查", "收集订单证据"));
        routing.put("whenNotToUse", List.of("生产写操作"));
        routing.put("keywords", List.of("order", "failure"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("patchType", "UPDATE_SKILL_CANDIDATE");
        result.put("reason", "订单失败排查并收集证据");
        result.put("changes", List.of(
                Map.of("section", "routingProfile", "value", routing),
                Map.of("section", "diagnosticRecipe", "key", "steps", "value", List.of("query"))));
        return result;
    }

    static Map<String, Object> skill(
            String skillId,
            String category,
            String status) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("skillId", skillId);
        result.put("name", "Order Recovery");
        result.put("description", "订单失败排查并收集证据");
        result.put("category", category);
        result.put("subcategory", "ORDER_RECOVERY");
        result.put("whenToUse", List.of("订单失败排查", "收集订单证据"));
        result.put("whenNotToUse", List.of("生产写操作"));
        result.put("keywords", List.of("order", "failure"));
        result.put("content", "routingRules diagnosticRecipe evidenceCriteria UPDATE_SKILL_CANDIDATE");
        result.put("currentSkillHash", skillId + "-hash");
        result.put("status", status);
        result.put("updateMode", "AUTO");
        return result;
    }
}
