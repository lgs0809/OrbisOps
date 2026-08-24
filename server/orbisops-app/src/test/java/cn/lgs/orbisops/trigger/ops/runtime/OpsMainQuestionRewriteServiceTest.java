package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.OpsAgentLlmClient;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsMainQuestionRewriteServiceTest {

    @Test
    void shouldRewriteDeicticQuestionWithMemory() {
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        JSONObject json = new JSONObject();
        json.put("rewrittenQuestion", "继续排查上一轮提到的 /api/demo-project/join 接口 5xx 升高问题，重点查看 Prometheus 指标和 ES 错误日志。");
        json.put("changed", true);
        json.put("reason", "将这个接口解析为上一轮 /api/demo-project/join。");
        JSONArray references = new JSONArray();
        references.add("这个接口=/api/demo-project/join");
        json.put("resolvedReferences", references);
        when(llmClient.chatJsonObject(eq("ops-main-query-rewriter"), contains("问题语义标准化器"), contains("这个接口继续查一下")))
                .thenReturn(json);
        OpsMainQuestionRewriteService service = new OpsMainQuestionRewriteService(llmClient);

        OpsMainQuestionRewriteService.RewriteResult result = service.rewrite(
                "这个接口继续查一下",
                "### 运维对话上下文\n- user: /api/demo-project/join 5xx 有点高\n- assistant: 建议查看 Prometheus 和 ES");

        assertTrue(result.changed());
        assertEquals("继续排查上一轮提到的 /api/demo-project/join 接口 5xx 升高问题，重点查看 Prometheus 指标和 ES 错误日志。", result.rewrittenQuestion());
        assertEquals(1, result.resolvedReferences().size());
        verify(llmClient).chatJsonObject(eq("ops-main-query-rewriter"), contains("问题语义标准化器"), contains("会话记忆"));
    }

    @Test
    void shouldKeepStandaloneQuestionEvenWhenMemoryIsEmpty() {
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        OpsMainQuestionRewriteService service = new OpsMainQuestionRewriteService(llmClient);

        OpsMainQuestionRewriteService.RewriteResult result = service.rewrite("查一下 /api/demo-project/join", "");

        assertFalse(result.changed());
        assertEquals("查一下 /api/demo-project/join", result.rewrittenQuestion());
        verify(llmClient, never()).chatJsonObject(anyString(), anyString(), anyString());
    }

    @Test
    void disabledPolicySkipsModelAndReturnsTrimmedOriginal() {
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        OpsMainQuestionRewriteService service = new OpsMainQuestionRewriteService(
                llmClient,
                new OpsMainQuestionRewriteSettings(false, 5_000, 1_200));

        OpsMainQuestionRewriteService.RewriteResult result =
                service.rewrite("  查日志  ", "previous context");

        assertFalse(result.changed());
        assertEquals("查日志", result.rewrittenQuestion());
        verify(llmClient, never()).chatJsonObject(anyString(), anyString(), anyString());
    }
}
