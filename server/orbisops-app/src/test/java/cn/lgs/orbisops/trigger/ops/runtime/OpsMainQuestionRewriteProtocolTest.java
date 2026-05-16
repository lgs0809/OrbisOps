package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsMainQuestionRewriteProtocolTest {

    private final OpsMainQuestionRewriteProtocol protocol =
            new OpsMainQuestionRewriteProtocol();

    @Test
    void projectsCompatibilityAliasesAndResolvedReferences() {
        OpsMainQuestionRewriteService.RewriteResult result = protocol.project(
                "继续查这个",
                JSON.parseObject("""
                        {
                          "standaloneQuestion":"继续查询 payment-service 的 /pay 接口错误率",
                          "changed":true,
                          "reason":"resolved pronoun",
                          "resolvedReferences":["这个=/pay 接口", "  "]
                        }
                        """),
                OpsMainQuestionRewriteSettings.defaults());

        assertTrue(result.changed());
        assertEquals(
                "继续查询 payment-service 的 /pay 接口错误率",
                result.rewrittenQuestion());
        assertEquals("resolved pronoun", result.reason());
        assertEquals(1, result.resolvedReferences().size());
    }

    @Test
    void keepsGeneralConversationAsPlainStandaloneQuestion() {
        OpsMainQuestionRewriteService.RewriteResult result = protocol.project(
                "你是谁？",
                JSON.parseObject("""
                        {
                          "rewrittenQuestion":"你是谁？",
                          "changed":false,
                          "reason":"普通身份询问",
                          "resolvedReferences":[]
                        }
                        """),
                OpsMainQuestionRewriteSettings.defaults());

        assertFalse(result.changed());
        assertEquals("你是谁？", result.rewrittenQuestion());
        assertTrue(protocol.systemPrompt().contains("先别动生产"));
        assertTrue(protocol.systemPrompt().contains("不要改写成已经执行或可以直接执行生产变更"));
    }

    @Test
    void degradesToOriginalWhenModelReturnsNoUsableQuestion() {
        OpsMainQuestionRewriteService.RewriteResult result = protocol.project(
                "查日志",
                JSON.parseObject("{\"rewrittenQuestion\":\"  \"}"),
                OpsMainQuestionRewriteSettings.defaults());

        assertFalse(result.changed());
        assertEquals("查日志", result.rewrittenQuestion());
        assertEquals("query rewrite returned blank question", result.reason());
    }
}
