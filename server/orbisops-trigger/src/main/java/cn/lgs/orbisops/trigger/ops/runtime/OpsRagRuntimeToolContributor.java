package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStage;
import com.alibaba.fastjson.JSON;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Exposes project-scoped knowledge retrieval as an explicit ReAct tool. */
@Component
public final class OpsRagRuntimeToolContributor implements OpsRuntimeToolContributor {

    private static final String TOOL_NAME = "knowledge_retrieve";
    private static final List<String> EXPLICIT_KNOWLEDGE_INTENTS = List.of(
            "sop", "runbook", "规程", "标准操作流程", "知识库", "文档", "指标含义", "指标字典", "错误码字典", "历史案例", "历史故障");
    private static final List<String> OPERATIONAL_SCHEMA_DISCOVERY = List.of(
            "promql", "指标名", "指标名称", "标签名", "标签写法", "label", "日志字段", "字段名", "sql 字段", "sql字段");

    private final ObjectProvider<OpsNodeRagService> nodeRagService;

    public OpsRagRuntimeToolContributor(ObjectProvider<OpsNodeRagService> nodeRagService) {
        if (nodeRagService == null) throw new IllegalArgumentException("NODE_RAG_SERVICE_PROVIDER_REQUIRED");
        this.nodeRagService = nodeRagService;
    }

    @Override
    public String id() {
        return "rag";
    }

    @Override
    public int order() {
        return 70;
    }

    @Override
    public OpsRuntimeToolContributorRequirement requirement() {
        return OpsRuntimeToolContributorRequirement.REQUIRED;
    }

    @Override
    public void contribute(OpsRuntimeResourceContext context) {
        if (context == null) throw new IllegalArgumentException("RUNTIME_RESOURCE_CONTEXT_REQUIRED");
        if (context.getAgentScope() == null || !Boolean.TRUE.equals(context.getRagEnabled())) return;
        var execution = context.getExecutionContext();
        if (execution != null && execution.stage() != AgentExecutionStage.INVESTIGATE
                && execution.stage() != AgentExecutionStage.PREPARE) {
            return;
        }
        OpsNodeRagService service = nodeRagService.getIfAvailable();
        if (service == null) throw new IllegalStateException("NODE_RAG_SERVICE_UNAVAILABLE");
        String projectId = value(context.getProjectId());
        String knowledgeBaseId = value(context.getKnowledgeBaseId());
        String scope = value(context.getMetadata().get("knowledgeBaseScope"));
        Function<KnowledgeRetrieveInput, String> function = input -> {
            String query = input == null ? "" : value(input.getQuery());
            if (!StringUtils.hasText(query)) {
                throw new IllegalArgumentException("KNOWLEDGE_RETRIEVE_QUERY_REQUIRED");
            }
            if (!knowledgeRetrieveAllowed(context, query)) {
                return blockedKnowledgeRetrieve(context, query);
            }
            return service.enhancePrompt(
                    query,
                    query,
                    true,
                    knowledgeBaseId,
                    context.getEvents(),
                    context.getEventSink(),
                    false,
                    projectId,
                    scope);
        };
        ToolCallback callback = FunctionToolCallback.builder(TOOL_NAME, function)
                .description("""
                        检索当前项目已授权知识库中的稳定知识、SOP、架构说明和历史案例。
                        仅在用户明确询问 SOP/规程/稳定知识，或实时数据源已经返回且存在一个具体的知识口径缺口阻塞后续判断时调用。
                        对普通实时诊断、Incident 是否需要跟进、故障是否仍存在、是否需要受控修复等问题，直接使用 Prometheus/Elasticsearch/MySQL 等权威实时源；不要为了“多找一点背景”额外调用知识库。
                        不要仅为了寻找 PromQL/日志字段/SQL 字段名称而先调用本工具：先调用实时数据源；只有数据源明确缺少口径且稳定知识确实必要时再补充检索。
                        输入 query 应描述需要检索的知识问题。返回内容只能作为知识证据，不能冒充实时运行状态。
                        """)
                .inputType(KnowledgeRetrieveInput.class)
                .build();
        context.getTools().add(OpsRuntimeGovernedToolCallback.wrap(
                callback,
                OpsRuntimeToolAuthorityDescriptor.readOnly(
                        "PROJECT_KNOWLEDGE",
                        Set.of(AgentExecutionStage.INVESTIGATE, AgentExecutionStage.PREPARE),
                        Set.of(TOOL_NAME))));
        context.getMetadata().put("ragToolEnabled", true);
    }

    static boolean knowledgeRetrieveAllowed(
            OpsRuntimeResourceContext context,
            String retrievalQuery) {
        String userQuery = userQuery(context).toLowerCase(Locale.ROOT);
        if (containsAny(userQuery, EXPLICIT_KNOWLEDGE_INTENTS)) return true;
        if (!hasAuthoritativeLiveEvidence(context)) return false;
        return !containsAny(value(retrievalQuery).toLowerCase(Locale.ROOT), OPERATIONAL_SCHEMA_DISCOVERY);
    }

    static boolean hasAuthoritativeLiveEvidence(OpsRuntimeResourceContext context) {
        if (context == null || context.getEvents() == null) return false;
        return context.getEvents().stream().anyMatch(event -> {
            if (event == null
                    || !"SOURCE_QUERY_FINISHED".equalsIgnoreCase(value(event.getEventType()))
                    || !"SUCCEEDED".equalsIgnoreCase(value(event.getStatus()))) {
                return false;
            }
            Map<String, Object> payload = event.getPayload();
            String sourceType = payload == null ? "" : value(payload.get("sourceType")).toUpperCase(Locale.ROOT);
            return Set.of("PROMETHEUS", "ELASTICSEARCH", "MYSQL_SLOW_SQL", "MYSQL").contains(sourceType)
                    && payload != null
                    && Boolean.TRUE.equals(payload.get("verified"));
        });
    }

    private static String blockedKnowledgeRetrieve(
            OpsRuntimeResourceContext context,
            String retrievalQuery) {
        Map<String, Object> envelope = Map.of(
                "status", "BLOCKED",
                "allowed", false,
                "remoteCallExecuted", false,
                "reasonCode", "KNOWLEDGE_RETRIEVE_REQUIRES_EXPLICIT_KNOWLEDGE_INTENT_OR_LIVE_EVIDENCE",
                "message", "普通实时诊断不得先用知识库猜测指标名、标签或字段。请先调用权威实时数据源；实时证据出现具体知识缺口后再按需检索稳定知识。",
                "query", retrievalQuery);
        if (context != null) {
            context.record(OpsRuntimeEvent.builder()
                    .eventType("KNOWLEDGE_RETRIEVE_BLOCKED")
                    .status("BLOCKED")
                    .summary("普通实时诊断的知识检索不满足最小充分证据边界，已阻断。")
                    .payload(envelope)
                    .build());
        }
        return JSON.toJSONString(envelope);
    }

    private static String userQuery(OpsRuntimeResourceContext context) {
        OpsAgentRunRequestDTO request = context == null
                ? null
                : OpsRuntimeToolContributionSupport.analysisRequest(context.getRequest());
        if (request != null && StringUtils.hasText(request.getQuestion())) return request.getQuestion().trim();
        if (request != null && StringUtils.hasText(request.getQuery())) return request.getQuery().trim();
        return context != null && context.getRequest() != null ? value(context.getRequest().getQuery()) : "";
    }

    private static boolean containsAny(String value, List<String> markers) {
        if (!StringUtils.hasText(value) || markers == null) return false;
        return markers.stream().anyMatch(value::contains);
    }

    private static String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public static class KnowledgeRetrieveInput {
        private String query;

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }
    }
}
