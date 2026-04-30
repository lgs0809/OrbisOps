package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.skill.model.SkillRuntimeCandidate;
import cn.lgs.orbisops.domain.skill.model.SkillRuntimeSelection;
import cn.lgs.orbisops.domain.skill.model.SkillRuntimeSelectionRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillRuntimeSelectionPolicyTest {

    private final SkillRuntimeSelectionPolicy policy = new SkillRuntimeSelectionPolicy();

    @Test
    void explicitActiveSkillWinsAndKeepsFrozenVersionIdentity() {
        SkillRuntimeSelection result = policy.select(request(Set.of("mysql-diagnosis"), 2), List.of(
                skill("mysql-diagnosis", "数据库排障", "ACTIVE", "AUTO", 3),
                skill("log-search", "日志检索", "ACTIVE", "AUTO", 4)), Map.of());

        assertEquals(2, result.selected().size());
        assertEquals("mysql-diagnosis", result.selected().get(0).candidate().skillId());
        assertEquals(3, result.selected().get(0).candidate().version());
        assertTrue(result.selected().get(0).explicit());
    }

    @Test
    void requestedFrozenOrInactiveSkillFailsClosed() {
        SkillRuntimeSelectionRequest request = request(Set.of("mysql-diagnosis"), 2);

        IllegalStateException frozen = assertThrows(IllegalStateException.class,
                () -> policy.select(request, List.of(
                        skill("mysql-diagnosis", "数据库排障", "ACTIVE", "FROZEN", 3)), Map.of()));
        IllegalStateException paused = assertThrows(IllegalStateException.class,
                () -> policy.select(request, List.of(
                        skill("mysql-diagnosis", "数据库排障", "PAUSED", "AUTO", 3)), Map.of()));

        assertTrue(frozen.getMessage().contains("REQUESTED_SKILL_NOT_ACTIVE"));
        assertTrue(paused.getMessage().contains("REQUESTED_SKILL_NOT_ACTIVE"));
    }

    @Test
    void similarImplicitSkillIsSuppressedWithinBudget() {
        SkillRuntimeSelectionRequest request = new SkillRuntimeSelectionRequest("demo-project", "agent",
                "mysql slow query", Set.of(), 10, 3, 6, 0D, 0.55D, 0.42D);
        SkillRuntimeSelection result = policy.select(request, List.of(
                skill("mysql-slow-query", "MySQL 慢 SQL 数据库排障流程", "ACTIVE", "AUTO", 1),
                skill("mysql-slow-query-v2", "MySQL 慢 SQL 数据库排障流程", "ACTIVE", "AUTO", 1),
                skill("log-search", "Elasticsearch 日志检索", "ACTIVE", "AUTO", 1)), Map.of());

        assertEquals(2, result.selected().size());
        assertEquals(1, result.suppressed().size());
        assertEquals("SIMILAR_SKILL_SUPPRESSED", result.suppressed().get(0).reasonCode());
    }

    @Test
    void semanticScoreCanRaiseRelevantSkillWithoutChangingExplicitBoundary() {
        SkillRuntimeSelection result = policy.select(request(Set.of(), 1), List.of(
                skill("mysql-diagnosis", "数据库排障", "ACTIVE", "AUTO", 1),
                skill("log-search", "日志检索", "ACTIVE", "AUTO", 1)),
                Map.of("log-search", 1D, "mysql-diagnosis", 0D));

        assertEquals("log-search", result.selected().get(0).candidate().skillId());
    }

    @Test
    void explicitSelectionCountIsBounded() {
        SkillRuntimeSelectionRequest request = new SkillRuntimeSelectionRequest("demo-project", "agent", "query",
                Set.of("a", "b"), 10, 2, 1, 0D, 0.9D, 0.4D);

        assertThrows(IllegalArgumentException.class,
                () -> policy.select(request, List.of(
                        skill("a", "A", "ACTIVE", "AUTO", 1),
                        skill("b", "B", "ACTIVE", "AUTO", 1)), Map.of()));
    }

    private SkillRuntimeSelectionRequest request(Set<String> requested, int selectedLimit) {
        return new SkillRuntimeSelectionRequest("demo-project", "agent", "mysql error log", requested,
                10, selectedLimit, 6, 0D, 0.82D, 0.42D);
    }

    private SkillRuntimeCandidate skill(String id, String description, String status, String updateMode, int version) {
        return new SkillRuntimeCandidate(id, "demo-project", "PROJECT", id, description, version,
                "skill-hash-" + id, "package-hash-" + id, "manifest-hash-" + id,
                Map.of("SKILL.md", "artifact-hash-" + id), "SKILL.md", status, updateMode, description.length());
    }
}
