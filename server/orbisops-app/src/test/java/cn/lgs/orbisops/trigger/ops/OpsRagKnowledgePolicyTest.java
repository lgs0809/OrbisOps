package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpsRagKnowledgePolicyTest {

    private final OpsRagKnowledgePolicy policy = new OpsRagKnowledgePolicy();

    @Test
    void rotatesRetrievalModesAroundRuntimeFiltersWithoutDuplicates() {
        OpsQuestionContext context = OpsQuestionContext.from("traceId: abcdef 查询错误日志");

        assertEquals("vector", policy.retrievalModeForIteration("vector", 1, context));
        assertEquals("hybrid", policy.retrievalModeForIteration("vector", 2, context));
        assertEquals("bm25", policy.retrievalModeForIteration("vector", 3, context));
        assertEquals("bm25", policy.retrievalModeForIteration("vector", 99, context));
    }

    @Test
    void usesBroadRecallOrderWhenQuestionHasNoRuntimeFilter() {
        OpsQuestionContext context = OpsQuestionContext.from("如何排查数据库连接池问题");

        assertEquals("auto", policy.retrievalModeForIteration(null, 1, context));
        assertEquals("hybrid", policy.retrievalModeForIteration(null, 2, context));
        assertEquals("vector", policy.retrievalModeForIteration(null, 3, context));
        assertEquals("bm25", policy.retrievalModeForIteration(null, 4, context));
    }

    @Test
    void appendsLlmQueryFocusOnlyWhenItAddsInformation() {
        OpsAgentRunRequestDTO request = new OpsAgentRunRequestDTO();
        request.setQuestion("如何排查订单超时");
        OpsSubAgentDecision focused = decision(true, "连接池耗尽");

        assertEquals("如何排查订单超时\n检索焦点：连接池耗尽", policy.buildQuery(request, focused));
        request.setQuestion("如何排查连接池耗尽");
        assertEquals("如何排查连接池耗尽", policy.buildQuery(request, focused));
        assertEquals("如何排查连接池耗尽", policy.buildQuery(request, decision(false, "其他焦点")));
    }

    @Test
    void suppliesStableFallbackQueryForMissingRequest() {
        assertEquals("运维排障 SOP 指标 日志 监控 数据库 常见故障", policy.buildQuery(null, null));
    }

    private OpsSubAgentDecision decision(boolean llmGenerated, String queryFocus) {
        return new OpsSubAgentDecision(
                llmGenerated,
                "test",
                null,
                null,
                null,
                null,
                queryFocus,
                null,
                List.of());
    }
}
