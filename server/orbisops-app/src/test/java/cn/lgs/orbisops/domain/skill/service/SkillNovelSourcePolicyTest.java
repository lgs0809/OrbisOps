package cn.lgs.orbisops.domain.skill.service;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SkillNovelSourcePolicyTest {
    final SkillNovelSourcePolicy policy = new SkillNovelSourcePolicy();
    SkillNovelSourcePolicy.Source source(String id, String condition, String... aliases) {
        return new SkillNovelSourcePolicy.Source(id, condition, Set.of(aliases));
    }

    @Test void oldEpisodeNeverBecomesNewByChangingConditionOrAddingNewAliases() {
        var proposed = List.of(source("old", "changed", "incident:new"), source("n1", "c1"), source("n2", "c2"));
        assertEquals(2, policy.novel(proposed, List.of(source("old", "old-condition"))).size());
        assertThrows(IllegalStateException.class, () -> policy.requirePatchSources(proposed, List.of(source("old", "old-condition"))));
    }

    @Test void transitiveLateCorrelationRevokesNoveltyAcrossDifferentSymptoms() {
        var proposed = List.of(source("mysql-lock", "c1", "group:event"), source("n1", "c1"), source("n2", "c2"));
        var consumed = List.of(source("redis-down", "old", "incident:upstream"), source("latency", "old", "incident:upstream", "group:event"));
        assertEquals(List.of("n1", "n2"), policy.novel(proposed, consumed).stream().map(SkillNovelSourcePolicy.Source::episodeId).toList());
    }

    @Test void conditionDiversityMustComeFromThreeNewIndependentTasks() {
        var old = List.of(source("old", "rare"));
        var proposed = List.of(source("old", "rare"), source("n1", "same"), source("n2", "same"), source("n3", "same"));
        assertEquals("SKILL_EVOLUTION_INSUFFICIENT_NEW_CONDITIONS", assertThrows(IllegalStateException.class,
                () -> policy.requirePatchSources(proposed, old)).getMessage());
        policy.requirePatchSources(List.of(source("n1", "a"), source("n2", "b"), source("n3", "a")), old);
    }

    @Test void duplicateProposedTasksAndEventMembersCountOnceRegardlessOfInputOrder() {
        var one = source("n1", "a", "group:event");
        var proposed = List.of(one, one, source("n2", "b", "group:event"), source("n3", "b"));
        assertEquals(2, policy.novel(proposed, List.of()).size());
        var reversed = new ArrayList<>(proposed); Collections.reverse(reversed);
        assertEquals(2, policy.novel(reversed, List.of()).size());
    }
}
