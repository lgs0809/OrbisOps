package cn.lgs.orbisops.trigger.ops;

import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsFinalReportLlmProtocolServiceTest {

    @Test
    void generateBuildsOriginalPromptAndReturnsTrimmedMarkdown() {
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        AtomicReference<String> agentName = new AtomicReference<>();
        AtomicReference<String> systemPrompt = new AtomicReference<>();
        AtomicReference<String> userPrompt = new AtomicReference<>();
        AtomicReference<Collection<String>> skillNames = new AtomicReference<>();
        String markdown = validReport("日志显示 ERR_LOCK_001 集中出现。");
        when(llmClient.chatJsonObject(
                anyString(),
                anyString(),
                anyString(),
                anyCollection()))
                .thenAnswer(invocation -> {
                    agentName.set(invocation.getArgument(0));
                    systemPrompt.set(invocation.getArgument(1));
                    userPrompt.set(invocation.getArgument(2));
                    skillNames.set(invocation.getArgument(3));
                    JSONObject json = new JSONObject();
                    json.put("markdownReport", "  " + markdown + "  ");
                    return json;
                });
        OpsFinalReportLlmProtocolService protocol =
                new OpsFinalReportLlmProtocolService(llmClient);

        OpsFinalReportLlmProtocolService.Result result = protocol.generate(input(8_000));

        assertTrue(result.hasReport());
        assertFalse(result.rejected());
        assertEquals(markdown, result.markdownReport());
        assertNull(result.rejectionReason());
        assertEquals("ops-final-report", agentName.get());
        assertTrue(systemPrompt.get().contains("只能基于输入中的真实数据源"));
        assertTrue(systemPrompt.get().contains("不得编造未查询的数据"));
        assertTrue(systemPrompt.get().contains("{\"markdownReport\":\"...\"}"));
        assertTrue(userPrompt.get().contains("分析 ID：analysis-1"));
        assertTrue(userPrompt.get().contains("生成时间：2026-07-27 12:00:00"));
        assertTrue(userPrompt.get().contains("日志窗口：最近 30 分钟"));
        assertTrue(userPrompt.get().contains("PromQL 窗口：5m"));
        assertTrue(userPrompt.get().contains("1. # 运维分析结果"));
        assertTrue(userPrompt.get().contains("6. ## 数据源与缺口"));
        assertTrue(userPrompt.get().contains("真实证据快照：\nsnapshot-body"));
        assertEquals(List.of(), skillNames.get());
    }

    @Test
    void nullJsonReturnsNoOutputWithoutRejection() {
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        when(llmClient.chatJsonObject(
                anyString(),
                anyString(),
                anyString(),
                anyCollection()))
                .thenReturn(null);
        OpsFinalReportLlmProtocolService protocol =
                new OpsFinalReportLlmProtocolService(llmClient);

        OpsFinalReportLlmProtocolService.Result result = protocol.generate(input(8_000));

        assertFalse(result.hasReport());
        assertFalse(result.rejected());
        assertNull(result.markdownReport());
        assertNull(result.rejectionReason());
    }

    @Test
    void missingMarkdownAndInvalidFormatReturnOriginalRejectionReasons() {
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        JSONObject missing = new JSONObject();
        missing.put("markdownReport", "   ");
        JSONObject invalid = new JSONObject();
        invalid.put("markdownReport", "## short");
        when(llmClient.chatJsonObject(
                anyString(),
                anyString(),
                anyString(),
                anyCollection()))
                .thenReturn(missing, invalid);
        OpsFinalReportLlmProtocolService protocol =
                new OpsFinalReportLlmProtocolService(llmClient);

        OpsFinalReportLlmProtocolService.Result missingResult =
                protocol.generate(input(8_000));
        OpsFinalReportLlmProtocolService.Result invalidResult =
                protocol.generate(input(8_000));

        assertTrue(missingResult.rejected());
        assertEquals(
                "最终报告 JSON 缺少 markdownReport",
                missingResult.rejectionReason());
        assertTrue(invalidResult.rejected());
        assertEquals(
                "最终报告输出格式不符合预期，analysisId=analysis-1",
                invalidResult.rejectionReason());
    }

    @Test
    void truncatesBeforeFormatValidationWithOriginalSuffix() {
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        String markdown = validReport("x".repeat(200));
        JSONObject json = new JSONObject();
        json.put("markdownReport", markdown);
        when(llmClient.chatJsonObject(
                anyString(),
                anyString(),
                anyString(),
                anyCollection()))
                .thenReturn(json);
        OpsFinalReportLlmProtocolService protocol =
                new OpsFinalReportLlmProtocolService(llmClient);
        String suffix = "\n\n> 报告过长，已按配置截断。";

        OpsFinalReportLlmProtocolService.Result result = protocol.generate(input(100));

        assertTrue(result.hasReport());
        assertEquals(markdown.substring(0, 100) + suffix, result.markdownReport());
    }

    @Test
    void generateAndRejectPreserveDegradationExceptionIdentity() {
        OpsAgentLlmClient llmClient = mock(OpsAgentLlmClient.class);
        OpsLlmDegradationException generateFailure =
                new OpsLlmDegradationException("ops-final-report", "generation failed");
        when(llmClient.chatJsonObject(
                anyString(),
                anyString(),
                anyString(),
                anyCollection()))
                .thenThrow(generateFailure);
        OpsFinalReportLlmProtocolService protocol =
                new OpsFinalReportLlmProtocolService(llmClient);

        OpsLlmDegradationException generated = assertThrows(
                OpsLlmDegradationException.class,
                () -> protocol.generate(input(8_000)));
        assertSame(generateFailure, generated);

        OpsLlmDegradationException rejectFailure =
                new OpsLlmDegradationException("ops-final-report", "reject failed");
        doThrow(rejectFailure)
                .when(llmClient)
                .rejectDegradation("ops-final-report", "invalid report");
        OpsLlmDegradationException rejected = assertThrows(
                OpsLlmDegradationException.class,
                () -> protocol.rejectDegradation("invalid report"));
        assertSame(rejectFailure, rejected);
        verify(llmClient).rejectDegradation("ops-final-report", "invalid report");
    }

    private OpsFinalReportLlmProtocolService.Input input(int maxChars) {
        return new OpsFinalReportLlmProtocolService.Input(
                "analysis-1",
                "2026-07-27 12:00:00",
                30,
                "5m",
                "snapshot-body",
                maxChars);
    }

    private String validReport(String evidence) {
        return """
                # 运维分析结果

                ## 结论
                需要继续排查。

                ## 关键证据
                %s

                ## 风险与可能原因
                可能存在业务竞争。

                ## 建议动作
                检查业务链路。

                ## 数据源与缺口
                Elasticsearch 已查询；缺口：Prometheus 未查询。
                """.formatted(evidence).trim();
    }
}
