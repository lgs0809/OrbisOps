package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.trigger.ops.toolset.OpsLocalAdapterSettings;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsDatasourceRuntimeToolProviderTest {

    @Test
    void explicitNoRecentLogsMustHideElasticsearchTool() {
        OpsAgentRunRequestDTO request = OpsAgentRunRequestDTO.builder()
                .includeRecentLogs(false)
                .build();

        assertFalse(OpsDatasourceRuntimeToolProvider.shouldExposeElasticsearch(request));
    }

    @Test
    void recentLogsRequestedMustExposeElasticsearchTool() {
        OpsAgentRunRequestDTO request = OpsAgentRunRequestDTO.builder()
                .includeRecentLogs(true)
                .build();

        assertTrue(OpsDatasourceRuntimeToolProvider.shouldExposeElasticsearch(request));
    }

    @Test
    void unspecifiedRecentLogsKeepsBackwardCompatibleExposure() {
        OpsAgentRunRequestDTO request = OpsAgentRunRequestDTO.builder().build();

        assertTrue(OpsDatasourceRuntimeToolProvider.shouldExposeElasticsearch(request));
    }

    @Test
    void elasticsearchCapabilityRequiresConfiguredDefaultIndex() {
        OpsLocalAdapterSettings unconfigured = OpsLocalAdapterSettings.defaults();
        OpsLocalAdapterSettings configured = new OpsLocalAdapterSettings(
                "http://127.0.0.1:9090",
                "http://127.0.0.1:9200",
                "order-service-log-*",
                "order-service-log-*",
                "./logs",
                "./",
                8,
                200,
                65_536);

        assertFalse(OpsDatasourceRuntimeToolProvider.elasticsearchConfigured(unconfigured));
        assertTrue(OpsDatasourceRuntimeToolProvider.elasticsearchConfigured(configured));
    }

    @Test
    void userBusinessObjectAWithCatalogABOnlyAuthorizesA() {
        String query = "示例参团最近是不是持续失败";
        OpsAgentRunRequestDTO request = request(query);
        OpsRuntimeResourceContext context = resolvedContext(query);

        assertTrue(OpsDatasourceRuntimeToolProvider.resourceIdentityResolvedForInput(
                request,
                context,
                "sum(rate(http_requests_total{uri=\"/api/demo-project/join\"}[5m]))"));
        assertEquals(List.of("/api/demo-project/join"),
                OpsBusinessResourceIdentityProjector.resolvedPaths(context).stream().toList());
    }

    @Test
    void userBusinessObjectAMustBlockDatasourceQueryThatAddsNeighborB() {
        String query = "示例参团最近是不是持续失败";
        OpsAgentRunRequestDTO request = request(query);
        OpsRuntimeResourceContext context = resolvedContext(query);

        assertFalse(OpsDatasourceRuntimeToolProvider.resourceIdentityResolvedForInput(
                request,
                context,
                "sum(rate(http_requests_total{uri=~\"/api/demo-project/join|/api/demo-project/lock\"}[5m]))"));
    }

    @Test
    void userExplicitlyAskingBusinessObjectsAAndBAuthorizesBoth() {
        String query = "对比示例参团和示例锁单最近的错误率";
        OpsAgentRunRequestDTO request = request(query);
        OpsRuntimeResourceContext context = resolvedContext(query);

        assertTrue(OpsDatasourceRuntimeToolProvider.resourceIdentityResolvedForInput(
                request,
                context,
                "sum(rate(http_requests_total{uri=~\"/api/demo-project/join|/api/demo-project/lock\"}[5m]))"));
        assertEquals(2, OpsBusinessResourceIdentityProjector.resolvedPaths(context).size());
    }

    @Test
    void explicitUserApiPathDoesNotRequireOpenApiResolution() {
        String query = "检查 /api/demo-project/lock 最近的延迟";
        OpsAgentRunRequestDTO request = request(query);
        OpsRuntimeResourceContext context = runtimeContext(query);

        assertTrue(OpsDatasourceRuntimeToolProvider.resourceIdentityResolvedForInput(
                request,
                context,
                "histogram_quantile(... uri=\"/api/demo-project/lock\" ...)"));
    }

    @Test
    void businessNameCannotGuessApiPathBeforeOpenApiResolution() {
        String query = "示例锁单最近是不是变慢了";
        OpsAgentRunRequestDTO request = request(query);

        assertFalse(OpsDatasourceRuntimeToolProvider.resourceIdentityResolvedForInput(
                request,
                runtimeContext(query),
                "uri=\"/api/demo-project/lock\""));
    }

    @Test
    void projectWideOpenApiDiscoveryWithoutCurrentBusinessMatchDoesNotAuthorizeCatalogPaths() {
        String query = "看看订单服务是否健康";
        OpsAgentRunRequestDTO request = request(query);
        OpsRuntimeResourceContext context = resolvedContext(query);

        assertTrue(OpsBusinessResourceIdentityProjector.resolvedPaths(context).isEmpty());
        assertFalse(OpsDatasourceRuntimeToolProvider.resourceIdentityResolvedForInput(
                request,
                context,
                "uri=\"/api/demo-project/join\""));
    }

    @Test
    void projectedIdentityCarriesOpenApiProvenance() {
        String query = "示例参团最近的故障情况";
        OpsRuntimeResourceContext context = resolvedContext(query);
        OpsRuntimeEvent event = context.getEvents().stream()
                .filter(item -> OpsBusinessResourceIdentityProjector.RESOLVED_EVENT.equals(item.getEventType()))
                .findFirst()
                .orElseThrow();

        assertEquals("OPENAPI", event.getPayload().get("source"));
        assertEquals("tool-result-openapi-1", event.getPayload().get("resultId"));
        assertEquals("openapi-output-hash-1", event.getPayload().get("outputHash"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> identities = (List<Map<String, Object>>) event.getPayload().get("identities");
        assertEquals(1, identities.size());
        assertEquals("示例参团", identities.get(0).get("businessObject"));
        assertEquals("/api/demo-project/join", identities.get(0).get("endpoint"));
        assertEquals("submitOrder", identities.get(0).get("operationId"));
        assertEquals("POST", identities.get(0).get("method"));
    }

    @Test
    void serviceLevelQueryDoesNotRequireResourceResolution() {
        OpsAgentRunRequestDTO request = request("示例服务是否在线");

        assertTrue(OpsDatasourceRuntimeToolProvider.resourceIdentityResolvedForInput(
                request,
                runtimeContext("示例服务是否在线"),
                "up{service=\"order-service\"}"));
    }

    private OpsAgentRunRequestDTO request(String query) {
        return OpsAgentRunRequestDTO.builder()
                .query(query)
                .question(query)
                .build();
    }

    private OpsRuntimeResourceContext resolvedContext(String query) {
        OpsRuntimeResourceContext context = runtimeContext(query);
        OpsBusinessResourceIdentityProjector.projectOpenApiResult(
                context,
                "project_mcp_demo_openapi_prod_readonly_mcp",
                "{\"toolName\":\"openapi_list_operations\",\"arguments\":{}}",
                openApiToolOutput());
        return context;
    }

    private OpsRuntimeResourceContext runtimeContext(String query) {
        return OpsRuntimeResourceContext.builder()
                .request(OpsAgentChatRequest.builder()
                        .query(query)
                        .metadata(new java.util.LinkedHashMap<>(Map.of(
                                OpsWorkSessionContextMetadataKeys.ORIGINAL_USER_QUERY, query,
                                OpsWorkSessionContextMetadataKeys.REWRITTEN_QUERY, query)))
                        .build())
                .events(new ArrayList<>())
                .build();
    }

    private String openApiToolOutput() {
        Map<String, Object> catalog = Map.of(
                "operations", List.of(
                        Map.of(
                                "method", "POST",
                                "path", "/api/demo-project/join",
                                "summary", "示例参团",
                                "description", "",
                                "operationId", "submitOrder",
                                "tags", List.of("示例交易")),
                        Map.of(
                                "method", "POST",
                                "path", "/api/demo-project/lock",
                                "summary", "示例锁单",
                                "description", "",
                                "operationId", "lockOrder",
                                "tags", List.of("示例交易"))));
        String rawPreview = JSON.toJSONString(List.of(Map.of(
                "type", "text",
                "text", JSON.toJSONString(catalog))));
        return JSON.toJSONString(Map.of(
                "allowed", true,
                "decision", "ALLOWED",
                "resultId", "tool-result-openapi-1",
                "outputHash", "openapi-output-hash-1",
                "rawPreview", rawPreview));
    }
}
