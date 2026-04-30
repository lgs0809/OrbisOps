package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.skill.model.SkillExperienceTaskTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SkillExperienceClusteringPolicyTest {

    private final SkillExperienceClusteringPolicy policy =
            new SkillExperienceClusteringPolicy();

    @Test void overlappingTermsFollowDocumentedPriorityInsteadOfHashMapIterationOrder() {
        assertEquals("AVAILABILITY",policy.problemFamily("数据库不可用并报错，延迟升高"));
        assertEquals("ERROR_DIAGNOSIS",policy.problemFamily("数据库错误率和整体延迟"));
        assertEquals("PERFORMANCE",policy.problemFamily("数据库慢查询"));
    }

    @Test
    void groupsRephrasedFailuresButSeparatesPerformanceCases() {
        Map<String, Object> first = policy.clusterIdentity(
                template("排查 service-a 最近的请求失败"),
                List.of("QUERY_METRICS", "QUERY_LOGS"));
        Map<String, Object> second = policy.clusterIdentity(
                template("分析 service-b 的异常错误"),
                List.of("QUERY_LOGS", "QUERY_METRICS"));
        Map<String, Object> performance = policy.clusterIdentity(
                template("分析 service-b 接口延迟过高"),
                List.of("QUERY_LOGS", "QUERY_METRICS"));

        assertEquals(first, second);
        assertNotEquals(first, performance);
    }

    private SkillExperienceTaskTemplate template(String pattern) {
        return new SkillExperienceTaskTemplate(
                "OPS_INVESTIGATION",
                pattern,
                "SUCCESSFUL_DIAGNOSTIC_PATTERN",
                List.of("METRICS", "LOGS"),
                "SUCCEEDED");
    }
}
