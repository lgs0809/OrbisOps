package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsQueryRewriteResourceIntegrityPolicyTest {

    private final OpsQueryRewriteResourceIntegrityPolicy policy =
            new OpsQueryRewriteResourceIntegrityPolicy();

    @Test
    void rejectsApiPathInventedFromBusinessName() {
        assertTrue(policy.introducesUnknownApiPath(
                "示例锁单最近是不是变慢了？",
                "",
                "请检查示例锁单接口 /api/demo-project/join 最近的延迟。"));
    }

    @Test
    void rejectsApiPathImportedOnlyFromConversationMemory() {
        assertTrue(policy.introducesUnknownApiPath(
                "继续查刚才那个接口",
                "上一轮讨论的是示例锁单 /api/demo-project/lock",
                "继续检查 /api/demo-project/lock 的延迟。"));
    }

    @Test
    void acceptsApiPathAlreadyExplicitInCurrentUserQuery() {
        assertFalse(policy.introducesUnknownApiPath(
                "继续查 /api/demo-project/lock",
                "上一轮讨论的是示例锁单 /api/demo-project/lock",
                "继续检查 /api/demo-project/lock 的延迟。"));
    }

    @Test
    void ordinaryRewriteWithoutApiPathIsUnaffected() {
        assertFalse(policy.introducesUnknownApiPath(
                "它最近是不是变慢了？",
                "上一轮讨论的是示例锁单",
                "示例锁单最近是不是变慢了？"));
    }

    @Test
    void rejectsConfirmedAndOngoingPremisesAddedToUncertainQuestion() {
        assertTrue(policy.introducesUnsupportedOperationalPremise(
                "示例参团：这个故障看起来还在持续，证据够的话帮我正式记录下来",
                "针对已确认且仍在持续的示例参团故障，请正式记录为待跟进问题。"));
    }

    @Test
    void acceptsConfirmedPremiseAlreadyExplicitInCurrentQuestion() {
        assertFalse(policy.introducesUnsupportedOperationalPremise(
                "示例参团：故障已经确认了，给我一个可审核变更方案",
                "示例参团故障已经确认，请生成可审核变更方案。"));
    }

    @Test
    void rejectsRecoveredPremiseAddedToVerificationQuestion() {
        assertTrue(policy.introducesUnsupportedOperationalPremise(
                "示例参团：帮我确认最近几分钟到底恢复了没有",
                "示例参团已经恢复，请核对最近几分钟指标。"));
    }

    @Test
    void ordinaryOperationalParaphraseDoesNotInventPremise() {
        assertFalse(policy.introducesUnsupportedOperationalPremise(
                "示例锁单最近是不是变慢了？",
                "检查示例锁单最近的延迟和请求量变化。"));
    }
}
