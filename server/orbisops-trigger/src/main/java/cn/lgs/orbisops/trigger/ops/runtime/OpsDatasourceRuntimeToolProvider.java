package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.trigger.ops.OpsAuthoritativeDatasourceEvidenceProjector;
import cn.lgs.orbisops.trigger.ops.toolset.OpsExternalLocalProviderSettings;
import cn.lgs.orbisops.trigger.ops.toolset.OpsLocalAdapterSettings;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionScope;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import com.alibaba.fastjson.JSON;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bridges governed development/test datasource toolsets into the generic ReAct runtime.
 * Production local providers stay disabled by {@link OpsExternalLocalProviderSettings};
 * production diagnostics must use project-authorized MCP resources instead.
 */
@Service
public final class OpsDatasourceRuntimeToolProvider {

    static final String PROMETHEUS_TOOL = "prometheus_query";
    static final String ELASTICSEARCH_TOOL = "elasticsearch_search";
    private static final Pattern API_PATH = Pattern.compile(
            "(?i)(/api/[a-z0-9_./{}:-]+)");

    private final OpsToolExecutionService toolExecutionService;
    private final OpsAuthoritativeDatasourceEvidenceProjector evidenceProjector;
    private final OpsExternalLocalProviderSettings providerSettings;
    private final OpsLocalAdapterSettings localAdapterSettings;

    public OpsDatasourceRuntimeToolProvider(
            OpsToolExecutionService toolExecutionService,
            OpsAuthoritativeDatasourceEvidenceProjector evidenceProjector,
            OpsExternalLocalProviderSettings providerSettings,
            OpsLocalAdapterSettings localAdapterSettings) {
        if (toolExecutionService == null) throw new IllegalArgumentException("DATASOURCE_TOOL_EXECUTION_SERVICE_REQUIRED");
        if (evidenceProjector == null) throw new IllegalArgumentException("DATASOURCE_EVIDENCE_PROJECTOR_REQUIRED");
        if (providerSettings == null) throw new IllegalArgumentException("DATASOURCE_PROVIDER_SETTINGS_REQUIRED");
        if (localAdapterSettings == null) throw new IllegalArgumentException("DATASOURCE_LOCAL_ADAPTER_SETTINGS_REQUIRED");
        this.toolExecutionService = toolExecutionService;
        this.evidenceProjector = evidenceProjector;
        this.providerSettings = providerSettings;
        this.localAdapterSettings = localAdapterSettings;
    }

    public List<ToolCallback> build(
            String projectId,
            String actor,
            String runId,
            OpsAgentRunRequestDTO analysisRequest,
            OpsRuntimeResourceContext runtimeContext) {
        if (!StringUtils.hasText(projectId) || !StringUtils.hasText(runId) || analysisRequest == null) {
            return List.of();
        }
        List<ToolCallback> tools = new ArrayList<>();
        if (providerSettings.allowsAdapter("LOCAL_PROMETHEUS")) {
            tools.add(prometheus(projectId, actor, runId, analysisRequest, runtimeContext));
        }
        if (providerSettings.allowsAdapter("LOCAL_ELASTICSEARCH")
                && shouldExposeElasticsearch(analysisRequest)
                && elasticsearchConfigured(localAdapterSettings)) {
            tools.add(elasticsearch(projectId, actor, runId, analysisRequest, runtimeContext));
        }
        return List.copyOf(tools);
    }

    static boolean shouldExposeElasticsearch(OpsAgentRunRequestDTO analysisRequest) {
        return analysisRequest != null && !Boolean.FALSE.equals(analysisRequest.getIncludeRecentLogs());
    }

    static boolean elasticsearchConfigured(OpsLocalAdapterSettings settings) {
        return settings != null && StringUtils.hasText(settings.elasticsearchIndex());
    }

    static boolean resourceIdentityResolvedForInput(
            OpsAgentRunRequestDTO analysisRequest,
            OpsRuntimeResourceContext runtimeContext,
            String toolInput) {
        Set<String> requestedPaths = apiPaths(toolInput);
        if (requestedPaths.isEmpty()) return true;
        return allowedResourcePaths(analysisRequest, runtimeContext).containsAll(requestedPaths);
    }

    static Set<String> allowedResourcePaths(
            OpsAgentRunRequestDTO analysisRequest,
            OpsRuntimeResourceContext runtimeContext) {
        Set<String> allowed = new LinkedHashSet<>();
        if (analysisRequest != null) {
            allowed.addAll(apiPaths(analysisRequest.getQuery()));
            allowed.addAll(apiPaths(analysisRequest.getQuestion()));
        }
        allowed.addAll(OpsBusinessResourceIdentityProjector.explicitUserPaths(runtimeContext));
        allowed.addAll(OpsBusinessResourceIdentityProjector.resolvedPaths(runtimeContext));
        return allowed;
    }

    static Set<String> apiPaths(String value) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = API_PATH.matcher(textValue(value));
        while (matcher.find()) {
            result.add(matcher.group(1).toLowerCase(Locale.ROOT));
        }
        return result;
    }

    private ToolCallback prometheus(
            String projectId,
            String actor,
            String runId,
            OpsAgentRunRequestDTO analysisRequest,
            OpsRuntimeResourceContext runtimeContext) {
        Function<PrometheusInput, String> function = input -> {
            PrometheusInput safe = input == null ? new PrometheusInput() : input;
            String query = text(safe.getQuery());
            if (!resourceIdentityResolvedForInput(analysisRequest, runtimeContext, query)) {
                return resourceIdentityBlocked(PROMETHEUS_TOOL, query, analysisRequest, runtimeContext);
            }
            Map<String, Object> arguments = new LinkedHashMap<>();
            arguments.put("query", query);
            if (safe.getRangeMinutes() != null) arguments.put("rangeMinutes", safe.getRangeMinutes());
            if (StringUtils.hasText(safe.getStep())) arguments.put("step", safe.getStep().trim());
            boolean range = Boolean.TRUE.equals(safe.getRange()) || safe.getRangeMinutes() != null;
            Map<String, Object> result = execute(
                    projectId,
                    actor,
                    runId,
                    "observability.prometheus",
                    range ? "prometheus_range_query" : "prometheus_instant_query",
                    arguments);
            recordAuthoritative(
                    analysisRequest,
                    OpsAuthoritativeDatasourceEvidenceProjector.PROMETHEUS,
                    "LOCAL_PROMETHEUS",
                    result,
                    runtimeContext);
            return JSON.toJSONString(result);
        };
        return FunctionToolCallback.builder(PROMETHEUS_TOOL, function)
                .description("查询 Prometheus 指标。query 必填；最近 N 分钟趋势请传 rangeMinutes，必要时传 step（如 30s）。仅用于只读指标诊断。")
                .inputType(PrometheusInput.class)
                .build();
    }

    private ToolCallback elasticsearch(
            String projectId,
            String actor,
            String runId,
            OpsAgentRunRequestDTO analysisRequest,
            OpsRuntimeResourceContext runtimeContext) {
        Function<ElasticsearchInput, String> function = input -> {
            ElasticsearchInput safe = input == null ? new ElasticsearchInput() : input;
            String query = text(safe.getQuery(), "*");
            if (!resourceIdentityResolvedForInput(analysisRequest, runtimeContext, query)) {
                return resourceIdentityBlocked(ELASTICSEARCH_TOOL, query, analysisRequest, runtimeContext);
            }
            Map<String, Object> arguments = new LinkedHashMap<>();
            arguments.put("query", query);
            if (StringUtils.hasText(safe.getIndex())) arguments.put("index", safe.getIndex().trim());
            if (safe.getRangeMinutes() != null) arguments.put("rangeMinutes", safe.getRangeMinutes());
            if (StringUtils.hasText(safe.getStartTime())) arguments.put("startTime", safe.getStartTime().trim());
            if (StringUtils.hasText(safe.getEndTime())) arguments.put("endTime", safe.getEndTime().trim());
            if (safe.getSize() != null) arguments.put("size", safe.getSize());
            Map<String, Object> result = execute(
                    projectId,
                    actor,
                    runId,
                    "observability.logs",
                    "elk_search",
                    arguments);
            recordAuthoritative(
                    analysisRequest,
                    OpsAuthoritativeDatasourceEvidenceProjector.ELASTICSEARCH,
                    "LOCAL_ELASTICSEARCH",
                    result,
                    runtimeContext);
            return JSON.toJSONString(result);
        };
        return FunctionToolCallback.builder(ELASTICSEARCH_TOOL, function)
                .description("检索 Elasticsearch 日志。query 可使用 query_string；必须提供 rangeMinutes，或同时提供 startTime/endTime。index 可省略并使用平台白名单默认索引。只读。")
                .inputType(ElasticsearchInput.class)
                .build();
    }

    private Map<String, Object> execute(
            String projectId,
            String actor,
            String runId,
            String toolsetId,
            String toolName,
            Map<String, Object> arguments) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("projectId", projectId);
        request.put("userId", text(actor, "ops-agent"));
        request.put("runId", runId);
        request.put("executionScope", OpsToolExecutionScope.PRE_APPROVAL_WORKFLOW.name());
        request.put("toolsetId", toolsetId);
        request.put("toolName", toolName);
        request.put("arguments", arguments == null ? Map.of() : arguments);
        Map<String, Object> result = toolExecutionService.execute(request, text(actor, "ops-agent"));
        if (!Boolean.TRUE.equals(result.get("allowed"))) {
            throw new OpsToolExecutionService.ToolBlockedException(
                    text(result.get("decision"), "DATASOURCE_TOOL_BLOCKED"),
                    result);
        }
        return result;
    }

    private String resourceIdentityBlocked(
            String toolName,
            String toolInput,
            OpsAgentRunRequestDTO analysisRequest,
            OpsRuntimeResourceContext runtimeContext) {
        Set<String> requestedPaths = apiPaths(toolInput);
        Set<String> allowedPaths = allowedResourcePaths(analysisRequest, runtimeContext);
        Set<String> unresolvedPaths = new LinkedHashSet<>(requestedPaths);
        unresolvedPaths.removeAll(allowedPaths);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("status", "BLOCKED");
        envelope.put("allowed", false);
        envelope.put("remoteCallExecuted", false);
        envelope.put("reasonCode", "BUSINESS_RESOURCE_IDENTITY_NOT_RESOLVED_USE_OPENAPI");
        envelope.put("message", "数据源参数包含当前请求未授权的 API path。只能使用用户原始问题明示的 URI，或本轮 OpenAPI 针对当前业务对象解析出的 endpoint；请移除邻近 operation 或重新解析当前业务对象后重试。");
        envelope.put("toolName", toolName);
        envelope.put("paths", List.copyOf(requestedPaths));
        envelope.put("allowedPaths", List.copyOf(allowedPaths));
        envelope.put("unresolvedPaths", List.copyOf(unresolvedPaths));
        if (runtimeContext != null) {
            runtimeContext.record(OpsRuntimeEvent.builder()
                    .eventType("BUSINESS_RESOURCE_IDENTITY_BLOCKED")
                    .status("BLOCKED")
                    .summary("数据源查询携带未经本轮权威解析的 API path，已阻断远端调用。")
                    .payload(Map.copyOf(envelope))
                    .build());
        }
        return JSON.toJSONString(envelope);
    }

    private void recordAuthoritative(
            OpsAgentRunRequestDTO request,
            String sourceType,
            String source,
            Map<String, Object> result,
            OpsRuntimeResourceContext runtimeContext) {
        if (result == null || !Boolean.TRUE.equals(result.get("allowed"))) return;
        evidenceProjector.record(request, sourceType, source, result, runtimeContext);
    }

    private static String textValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String text(Object value) {
        return text(value, "");
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    public static final class PrometheusInput {
        private String query;
        private Integer rangeMinutes;
        private String step;
        private Boolean range;

        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
        public Integer getRangeMinutes() { return rangeMinutes; }
        public void setRangeMinutes(Integer rangeMinutes) { this.rangeMinutes = rangeMinutes; }
        public String getStep() { return step; }
        public void setStep(String step) { this.step = step; }
        public Boolean getRange() { return range; }
        public void setRange(Boolean range) { this.range = range; }
    }

    public static final class ElasticsearchInput {
        private String query;
        private String index;
        private Integer rangeMinutes;
        private String startTime;
        private String endTime;
        private Integer size;

        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
        public String getIndex() { return index; }
        public void setIndex(String index) { this.index = index; }
        public Integer getRangeMinutes() { return rangeMinutes; }
        public void setRangeMinutes(Integer rangeMinutes) { this.rangeMinutes = rangeMinutes; }
        public String getStartTime() { return startTime; }
        public void setStartTime(String startTime) { this.startTime = startTime; }
        public String getEndTime() { return endTime; }
        public void setEndTime(String endTime) { this.endTime = endTime; }
        public Integer getSize() { return size; }
        public void setSize(Integer size) { this.size = size; }
    }
}
