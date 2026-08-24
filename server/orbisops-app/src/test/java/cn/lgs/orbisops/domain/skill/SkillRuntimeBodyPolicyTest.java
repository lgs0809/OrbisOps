package cn.lgs.orbisops.domain.skill;
import cn.lgs.orbisops.domain.skill.service.SkillRuntimeBodyPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SkillRuntimeBodyPolicyTest {
    private final SkillRuntimeBodyPolicy policy=new SkillRuntimeBodyPolicy();
    @Test void longOrdinaryMarkdownNeverLosesSafetyTail() {
        String body="查询".repeat(1100)+"\n必须经过审批，不得修改生产";
        assertThrows(IllegalStateException.class,()->policy.project(body,"查询",6000));
    }
    @Test void optionalSectionsAreWholeAndRequiredTailSurvives() {
        String body="PRECONDITION\n<!-- orbisops:optional id=logs keywords=日志|logs -->LOGS\n<!-- /orbisops:optional -->"
            +"<!-- orbisops:optional id=sql keywords=SQL -->"+"X".repeat(8000)+"<!-- /orbisops:optional -->\nNEVER WRITE PRODUCTION";
        String selected=policy.project(body,"查看日志",6000);
        assertEquals("PRECONDITION\nLOGS\n\nNEVER WRITE PRODUCTION",selected);
        assertTrue(policy.project(body,"SQL",6000).endsWith("NEVER WRITE PRODUCTION"));
        assertFalse(policy.project(body,"SQL",6000).contains("XXX"));
    }
    @Test void malformedOrNestedModulesFailClosed() {
        for(String body:java.util.List.of("<!-- orbisops:optional bad -->", "<!-- /orbisops:optional -->",
                "<!-- orbisops:optional id=x keywords=x -->open", "<!-- orbisops:optional id=x keywords=x --><!-- orbisops:optional id=y keywords=y -->"))
            assertThrows(IllegalArgumentException.class,()->policy.project(body,"x",6000));
    }
    @Test void budgetUsesUtf8BoundAndNeverSplitsUnicode() {
        assertEquals(6,SkillRuntimeBodyPolicy.units("审批"));
        assertEquals("审批",policy.project("审批","",6));
        assertThrows(IllegalStateException.class,()->policy.project("审批","",5));
        assertThrows(IllegalStateException.class,()->policy.project("x".repeat(6001),"",Integer.MAX_VALUE));
    }
}
