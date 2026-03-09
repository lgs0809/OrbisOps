package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsRagRuntimeToolContributorTest {

    @Test
    void agentScopeMustExposeKnowledgeAsExplicitToolWithoutRetrievingDuringAssembly() {
        OpsNodeRagService service = mock(OpsNodeRagService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<OpsNodeRagService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(service);
        when(service.enhancePrompt(
                eq("示例参团 SOP"),
                eq("示例参团 SOP"),
                eq(true),
                eq("kb-demo-project"),
                any(),
                any(),
                eq(false),
                eq("demo-project"),
                eq("PROJECT")))
                .thenReturn("knowledge-result");
        OpsRagRuntimeToolContributor contributor = new OpsRagRuntimeToolContributor(provider);
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("knowledgeBaseScope", "PROJECT");
        OpsRuntimeResourceContext context = OpsRuntimeResourceContext.builder()
                .agentScope(OpsAgentScopeConfig.builder().agentId("platform-ops-react").build())
                .request(OpsAgentChatRequest.builder()
                        .projectId("demo-project")
                        .runId("run-1")
                        .query("示例参团 SOP")
                        .build())
                .projectId("demo-project")
                .ragEnabled(true)
                .knowledgeBaseId("kb-demo-project")
                .events(new ArrayList<>())
                .metadata(metadata)
                .build();

        contributor.contribute(context);

        assertEquals(1, context.getTools().size());
        ToolCallback tool = context.getTools().get(0);
        assertEquals("knowledge_retrieve", tool.getToolDefinition().name());
        assertTrue(context.getEvents().isEmpty());
        assertEquals("\"knowledge-result\"", tool.call("{\"query\":\"示例参团 SOP\"}"));
        verify(service).enhancePrompt(
                eq("示例参团 SOP"),
                eq("示例参团 SOP"),
                eq(true),
                eq("kb-demo-project"),
                any(),
                any(),
                eq(false),
                eq("demo-project"),
                eq("PROJECT"));
    }

    @Test
    void ordinaryRealtimeDiagnosisCannotUseRagBeforeLiveEvidence() {
        OpsRuntimeResourceContext context = context("示例锁单最近是不是变慢了");

        assertFalse(OpsRagRuntimeToolContributor.knowledgeRetrieveAllowed(
                context,
                "查询示例锁单的 Prometheus 指标口径"));
    }

    @Test
    void explicitSopQuestionCanUseRagWithoutLiveEvidence() {
        OpsRuntimeResourceContext context = context("这类问题按项目标准操作流程应该怎么处理？");

        assertTrue(OpsRagRuntimeToolContributor.knowledgeRetrieveAllowed(
                context,
                "查找最适合当前业务的标准操作流程"));
    }

    @Test
    void concreteKnowledgeGapCanUseRagAfterAuthoritativeLiveEvidence() {
        OpsRuntimeResourceContext context = context("示例锁单最近是不是变慢了");
        context.getEvents().add(authoritativePrometheusEvidence());

        assertTrue(OpsRagRuntimeToolContributor.knowledgeRetrieveAllowed(
                context,
                "解释项目中锁单超时后的重试策略"));
    }

    @Test
    void ragCannotGuessMetricOrLabelSchemaEvenAfterEmptyLiveEvidence() {
        OpsRuntimeResourceContext context = context("示例锁单最近是不是变慢了");
        context.getEvents().add(authoritativePrometheusEvidence());

        assertFalse(OpsRagRuntimeToolContributor.knowledgeRetrieveAllowed(
                context,
                "Prometheus 指标名称和 uri/path 标签写法"));
    }

    private OpsRuntimeResourceContext context(String query) {
        return OpsRuntimeResourceContext.builder()
                .request(OpsAgentChatRequest.builder().query(query).build())
                .events(new ArrayList<>())
                .build();
    }

    private OpsRuntimeEvent authoritativePrometheusEvidence() {
        return OpsRuntimeEvent.builder()
                .eventType("SOURCE_QUERY_FINISHED")
                .status("SUCCEEDED")
                .payload(Map.of(
                        "sourceType", "PROMETHEUS",
                        "verified", true,
                        "resultId", "tool-result-1",
                        "outputHash", "hash-1"))
                .build();
    }
}
