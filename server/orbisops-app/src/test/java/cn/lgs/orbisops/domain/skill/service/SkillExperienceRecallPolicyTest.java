package cn.lgs.orbisops.domain.skill.service;
import cn.lgs.orbisops.domain.skill.model.SkillMethodMemory.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
class SkillExperienceRecallPolicyTest {
    private final SkillExperienceRecallPolicy policy=new SkillExperienceRecallPolicy();
    @Test void mixedRanksPreferAgreementDeduplicateAndRespectBothLaneBudgets() {
        var lexical=IntStream.range(0,20).mapToObj(i->new Ref("a-"+i,1,"h")).toList();
        var vector=new ArrayList<Ref>();vector.add(new Ref("b",1,"h"));vector.add(lexical.get(1));vector.add(lexical.get(0));
        var selected=policy.fuse(lexical,vector);assertEquals(5,selected.size());
        // 1/61 + 1/63 is greater than 2/62: reciprocal ranks are not average ranks.
        assertEquals(List.of("a-0","a-1"),selected.stream().limit(2).map(Ref::groupId).toList());
        assertEquals(5,selected.stream().map(Ref::groupId).distinct().count());assertFalse(selected.stream().anyMatch(r->r.groupId().equals("a-19")));
    }
    @Test void chinesePhrasesAndToolNamesProduceSafeSearchTerms() {
        var terms=policy.terms("Redis 只读巡检，错误率与 MySQL 样本：'|!*");
        assertTrue(terms.containsAll(List.of("redis","mysql","巡检","错误","样本")));
        assertTrue(terms.stream().noneMatch(t->t.contains("'") || t.contains("|")));
    }
}
