package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import com.alibaba.fastjson.JSON;
import org.springframework.ai.tool.ToolCallback;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Evaluates whether an Analysis report should create a governed ChangePackage proposal. */
final class OpsAnalysisChangePackageCoordinator {

    private static final Set<String> AUTHORITATIVE_DATASOURCE_TYPES = Set.of(
            "PROMETHEUS", "ELASTICSEARCH", "MYSQL_SLOW_SQL");
    private static final int MAX_RUNTIME_EVIDENCE = 20;
    private static final int MAX_RUNTIME_EVIDENCE_CHARS = 1800;
    private static final int MAX_EVIDENCE_COMPLETION_ATTEMPTS = 2;

    private final OpsRuntimeResourceAssembler resourceAssembler;
    private final OpsRuntimePromptAssembler promptAssembler;
    private final OpsRuntimeLlmInvoker llmInvoker;
    private final OpsAnalysisRuntimeStateManager analysisStateManager;
    private final OpsRuntimeEventJournal eventJournal;

    OpsAnalysisChangePackageCoordinator(OpsRuntimeResourceAssembler resourceAssembler,
                                        OpsRuntimePromptAssembler promptAssembler,
                                        OpsRuntimeLlmInvoker llmInvoker,
                                        OpsAnalysisRuntimeStateManager analysisStateManager,
                                        OpsRuntimeEventJournal eventJournal) {
        this.resourceAssembler = resourceAssembler;
        this.promptAssembler = promptAssembler;
        this.llmInvoker = llmInvoker;
        this.analysisStateManager = analysisStateManager;
        this.eventJournal = eventJournal;
    }

    String evaluate(OpsAgentDefinition definition,
                    OpsWorkflowNode node,
                    OpsAgentChatRequest request,
                    OpsAnalysisResponseDTO response,
                    List<OpsRuntimeEvent> events,
                    Consumer<OpsRuntimeEvent> eventSink,
                    String originalQuestion,
                    String nodeType) {
        OpsWorkflowNode resourceNode = changePackageNode(definition, node);
        boolean enabled = resourceNode != null
                || Boolean.TRUE.equals(definition == null ? null : definition.getChangePackageEnabled());
        if (!enabled) {
            return "";
        }
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = response == null
                ? null
                : response.getInvestigationPlan();
        OpsAgentRunRequestDTO analysisRequest = OpsRuntimeToolContributionSupport.analysisRequest(request);
        boolean changeRequested = Boolean.TRUE.equals(request == null ? null : request.getChangeRequested())
                || (plan != null && Boolean.TRUE.equals(plan.getChangeRequested()))
                || (analysisRequest != null && Boolean.TRUE.equals(analysisRequest.getChangeRequested()));
        if (!changeRequested) {
            return "";
        }
        if (proposalCreated(events, null)) {
            return "";
        }

        // Resource and policy assembly stay fail-closed, as they were outside the original LLM degradation block.
        OpsRuntimeResourceBundle bundle = assembleGovernanceResources(
                definition, resourceNode, request, events, eventSink);
        if (!Boolean.TRUE.equals(bundle.getMetadata().get("changePackageToolEnabled"))) {
            eventJournal.record(events, eventSink, OpsRuntimeEvent.builder()
                    .eventType("CHANGE_PACKAGE_SKIPPED")
                    .nodeId(node == null ? null : node.getNodeId())
                    .nodeType(node == null ? null : nodeType)
                    .agent(node == null ? null : node.getAgent())
                    .status("SKIPPED")
                    .summary("ChangePackage PREPARE 工具未暴露，保持 fail-closed。")
                    .payload(Map.of(
                            "reason", "CHANGE_PACKAGE_TOOL_UNAVAILABLE",
                            "resourceMetadataKeys", bundle.getMetadata() == null
                                    ? List.of()
                                    : List.copyOf(bundle.getMetadata().keySet())))
                    .build());
            analysisStateManager.appendExecutionNotes(response, List.of(
                    "ChangePackage 未创建：PREPARE 工具当前不可用，系统保持 fail-closed。"));
            return "";
        }
        ensureOpenApiResourceCatalog(bundle, events, eventSink);
        if (!ensureAuthoritativeEvidence(
                definition,
                resourceNode,
                request,
                response,
                events,
                eventSink,
                originalQuestion,
                bundle)) {
            eventJournal.record(events, eventSink, OpsRuntimeEvent.builder()
                    .eventType("CHANGE_PACKAGE_SKIPPED")
                    .nodeId(node == null ? null : node.getNodeId())
                    .nodeType(node == null ? null : nodeType)
                    .agent(node == null ? null : node.getAgent())
                    .status("SKIPPED")
                    .summary("权威实时证据不足，ChangePackage 保持 fail-closed。")
                    .payload(Map.of(
                            "reason", "INSUFFICIENT_AUTHORITATIVE_EVIDENCE",
                            "sourceTypes", List.copyOf(authoritativeSourceTypes(events))))
                    .build());
            analysisStateManager.appendExecutionNotes(response, List.of(
                    "ChangePackage 未创建：受控证据补全后仍缺少足够的独立权威实时证据。"));
            return "";
        }
        bundle = assembleGovernanceResources(definition, resourceNode, request, events, eventSink);
        bundle = governedPrepareBundle(bundle);

        String prompt = """
                ### 用户原始请求
                %s

                ### 已生成的运维诊断报告
                %s

                ### 本轮已校验的权威实时证据
                %s

                ### 本轮权威 OpenAPI 资源目录预检
                %s

                现在进入受控 PREPARE 判定阶段，不要改写诊断报告。
                - 项目存在 OpenAPI 资源目录时，平台已经在进入本阶段前完成只读预检；上面的“权威 OpenAPI 资源目录预检”就是本轮可用的资源身份上下文。本阶段不要重复做资源发现。
                - 本阶段只允许 PrepareChangePackage；前置实时调查和资源发现已经结束，不要再调用 Skill、RAG、Prometheus、Elasticsearch、MySQL、OpenAPI、代码工具或任何生产写工具。
                - 仅当用户明确要求修复/调整，且本轮真实证据足以支持一个具体、结构化、可验证、可回滚的候选方案时，调用 PrepareChangePackage 一次。
                - actions/mcpSteps 必须使用项目 MCP Tool Router / Policy Guard 认可的通用 effect 信息描述，不能传 Shell、凭据或原始目标环境写参数。
                - 证据不足、验证能力不足、用户只要求分析时，可以不调用；如需要人工设计，只能生成 MANUAL_REQUIRED / NEEDS_HUMAN_DESIGN 包。
                - 不调用时输出：NO_CHANGE_PACKAGE: <原因>。
                - 调用成功后输出：CHANGE_PACKAGE_CREATED: <packageId>，并提醒用户审核 version + packageHash；LAND 阶段由平台固定 LandingRuntime 执行。
                """.formatted(
                        value(originalQuestion),
                        value(response == null ? null : response.getMarkdownReport()),
                        runtimeEvidenceSnapshot(events),
                        openApiResourceSnapshot(events));
        try {
            String decision = callGovernanceWithSafeRetry(
                    promptAssembler.systemPrompt(definition, resourceNode, bundle),
                    prompt,
                    bundle,
                    events,
                    eventSink,
                    eventJournal.requestStartedNanos(request));
            boolean created = proposalCreated(events, null);
            if (created && !hasSuccessfulReactOutcome(events)) {
                recordWorkflowOutcome(request, events, eventSink);
            }
            analysisStateManager.appendExecutionNotes(response, List.of(created
                    ? "Agent 已基于本轮证据创建 ChangePackage；仍需用户审核 version + packageHash。"
                    : "Agent 已完成 ChangePackage 创建判断，本轮未创建变更包。"));
            eventJournal.record(events, eventSink, OpsRuntimeEvent.builder()
                    .eventType("CHANGE_PACKAGE_EVALUATED")
                    .nodeId(node == null ? null : node.getNodeId())
                    .nodeType(node == null ? null : nodeType)
                    .agent(node == null ? null : node.getAgent())
                    .status("SUCCEEDED")
                    .summary(created ? "Agent 已创建 ChangePackage。" : "Agent 判断本轮无需创建 ChangePackage。")
                    .payload(Map.of(
                            "created", created,
                            "decision", OpsMemoryTextUtils.abbreviate(decision, 2000)))
                    .build());
            return decision;
        } catch (RuntimeException error) {
            analysisStateManager.appendExecutionNotes(response, List.of(
                    "ChangePackage 判断失败，已按 fail-closed 阻断本次变更准备：" + value(error.getMessage())));
            eventJournal.record(events, eventSink, OpsRuntimeEvent.builder()
                    .eventType("CHANGE_PACKAGE_EVALUATION_FAILED")
                    .nodeId(node == null ? null : node.getNodeId())
                    .nodeType(node == null ? null : nodeType)
                    .agent(node == null ? null : node.getAgent())
                    .status("FAILED")
                    .summary("ChangePackage 判断失败：" + value(error.getMessage()))
                    .build());
            throw new IllegalStateException("CHANGE_PACKAGE_GOVERNANCE_FAILED", error);
        }
    }

    private void ensureOpenApiResourceCatalog(
            OpsRuntimeResourceBundle bundle,
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink) {
        if (hasSuccessfulOpenApiDiscovery(events) || bundle == null || bundle.getTools() == null) {
            return;
        }
        ToolCallback discovery = bundle.getTools().stream()
                .filter(this::isExecutableOpenApiDiscoveryTool)
                .findFirst()
                .orElse(null);
        if (discovery == null) {
            return;
        }
        String toolName = discovery.getToolDefinition() == null
                ? ""
                : text(discovery.getToolDefinition().name());
        String input = toolName.toLowerCase().startsWith("project_mcp_")
                ? "{\"toolName\":\"openapi_list_operations\",\"arguments\":{}}"
                : "{}";
        eventJournal.record(events, eventSink, OpsRuntimeEvent.builder()
                .eventType("CHANGE_PACKAGE_OPENAPI_PREFLIGHT_STARTED")
                .status("RUNNING")
                .summary("ChangePackage PREPARE 前开始读取项目权威 OpenAPI 资源目录。")
                .payload(Map.of("toolName", toolName))
                .build());
        try {
            String output = discovery.call(input);
            if (text(output).isBlank()) {
                throw new IllegalStateException("OPENAPI_RESOURCE_CATALOG_EMPTY");
            }
            eventJournal.record(events, eventSink, OpsRuntimeEvent.builder()
                    .eventType("CHANGE_PACKAGE_OPENAPI_PREFLIGHT_FINISHED")
                    .status("SUCCEEDED")
                    .summary("ChangePackage PREPARE 前项目权威 OpenAPI 资源目录读取完成。")
                    .payload(Map.of(
                            "toolName", toolName,
                            "output", OpsMemoryTextUtils.abbreviate(output, 4000)))
                    .build());
        } catch (RuntimeException error) {
            eventJournal.record(events, eventSink, OpsRuntimeEvent.builder()
                    .eventType("CHANGE_PACKAGE_OPENAPI_PREFLIGHT_FAILED")
                    .status("FAILED")
                    .summary("ChangePackage OpenAPI 资源预检失败：" + value(error.getMessage()))
                    .payload(Map.of("toolName", toolName))
                    .build());
            throw new IllegalStateException("CHANGE_PACKAGE_OPENAPI_PREFLIGHT_FAILED", error);
        }
    }

    private boolean hasSuccessfulOpenApiDiscovery(List<OpsRuntimeEvent> events) {
        if (events == null || events.isEmpty()) return false;
        List<OpsRuntimeEvent> snapshot;
        synchronized (events) {
            snapshot = new ArrayList<>(events);
        }
        return snapshot.stream().anyMatch(event -> {
            if (event == null || !"SUCCEEDED".equalsIgnoreCase(value(event.getStatus()))) return false;
            if ("CHANGE_PACKAGE_OPENAPI_PREFLIGHT_FINISHED".equals(event.getEventType())) return true;
            if (!"TOOL_CALL_FINISHED".equals(event.getEventType())) return false;
            Map<String, Object> payload = event.getPayload() == null ? Map.of() : event.getPayload();
            String toolName = text(payload.get("toolName")).toLowerCase();
            String input = text(payload.get("input")).toLowerCase();
            return toolName.contains("openapi") && input.contains("openapi_list_operations");
        });
    }

    private boolean isExecutableOpenApiDiscoveryTool(ToolCallback callback) {
        if (callback == null || callback.getToolDefinition() == null) return false;
        String name = text(callback.getToolDefinition().name()).toLowerCase();
        return (name.startsWith("project_mcp_") && name.contains("openapi"))
                || "openapi_list_operations".equals(name);
    }

    private String callGovernanceWithSafeRetry(
            String systemPrompt,
            String userPrompt,
            OpsRuntimeResourceBundle bundle,
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink,
            long requestStartedNanos) {
        int toolCallsBefore = toolCallStartedCount(events);
        try {
            return llmInvoker.call(
                    systemPrompt,
                    userPrompt,
                    bundle,
                    events,
                    eventSink,
                    requestStartedNanos);
        } catch (RuntimeException error) {
            if (!hasIOExceptionCause(error) || toolCallStartedCount(events) != toolCallsBefore) {
                throw error;
            }
            eventJournal.record(events, eventSink, OpsRuntimeEvent.builder()
                    .eventType("CHANGE_PACKAGE_MODEL_RETRY")
                    .status("RETRYING")
                    .summary("ChangePackage governance 模型调用发生无副作用 transport 故障，执行一次受控重试。")
                    .payload(Map.of(
                            "reason", value(error.getMessage()),
                            "attempt", 2,
                            "maxAttempts", 2))
                    .build());
            return llmInvoker.call(
                    systemPrompt,
                    userPrompt,
                    bundle,
                    events,
                    eventSink,
                    requestStartedNanos);
        }
    }

    private int toolCallStartedCount(List<OpsRuntimeEvent> events) {
        if (events == null || events.isEmpty()) return 0;
        List<OpsRuntimeEvent> snapshot;
        synchronized (events) {
            snapshot = new ArrayList<>(events);
        }
        return (int) snapshot.stream()
                .filter(event -> event != null && "TOOL_CALL_STARTED".equals(event.getEventType()))
                .count();
    }

    private boolean hasIOExceptionCause(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof IOException) return true;
            current = current.getCause();
        }
        return false;
    }

    private boolean ensureAuthoritativeEvidence(
            OpsAgentDefinition definition,
            OpsWorkflowNode resourceNode,
            OpsAgentChatRequest request,
            OpsAnalysisResponseDTO response,
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink,
            String originalQuestion,
            OpsRuntimeResourceBundle initialBundle) {
        Set<String> available = availableAuthoritativeSourceTypes(initialBundle);
        Set<String> current = authoritativeSourceTypes(events);
        if (available.isEmpty()) return true;
        int required = Math.min(2, Math.max(1, available.size()));
        if (current.size() >= required) return true;

        OpsRuntimeResourceBundle evidenceBundle = evidenceCompletionBundle(initialBundle);
        for (int attempt = 1; attempt <= MAX_EVIDENCE_COMPLETION_ATTEMPTS; attempt++) {
            current = authoritativeSourceTypes(events);
            if (current.size() >= required) return true;
            Set<String> missing = new LinkedHashSet<>(available);
            missing.removeAll(current);
            String prompt = """
                    ### 用户原始请求
                    %s

                    ### 当前诊断报告
                    %s

                    ### 受控变更证据补全
                    当前已完成的权威实时 sourceType：%s
                    本阶段可用的权威实时 sourceType：%s
                    仍需优先补齐：%s

                    这是 ChangePackage PREPARE 之前的只读证据补全阶段。
                    - 不要创建 ChangePackage，不要调用 Skill/RAG/代码工具或任何写工具。
                    - 用户声称“故障已确认”只能作为上下文，不能替代本轮实时观测。
                    - 必须调用可用的权威实时数据源。若至少两个独立 sourceType 可用，则在结束前至少取得两个不同 sourceType 的真实查询结果。
                    - 对运行时服务故障，Prometheus 用于实例/请求量/错误率/延迟等数值证据，Elasticsearch 用于近期错误/异常样本；两者都可用时应交叉验证。
                    - 查询返回空结果仍是有效观测，不要为了填充内容改用稳定知识源。
                    - 完成工具查询后只简短说明已补齐哪些 sourceType；不要宣称执行了生产变更。
                    """.formatted(
                    value(originalQuestion),
                    value(response == null ? null : response.getMarkdownReport()),
                    current,
                    available,
                    missing);
            try {
                eventJournal.record(events, eventSink, OpsRuntimeEvent.builder()
                        .eventType("CHANGE_PACKAGE_EVIDENCE_COMPLETION_STARTED")
                        .status("RUNNING")
                        .summary("ChangePackage PREPARE 前开始补齐权威实时证据。")
                        .payload(Map.of(
                                "attempt", attempt,
                                "requiredSourceCount", required,
                                "currentSourceTypes", List.copyOf(current),
                                "availableSourceTypes", List.copyOf(available)))
                        .build());
                llmInvoker.call(
                        promptAssembler.systemPrompt(definition, resourceNode, evidenceBundle),
                        prompt,
                        evidenceBundle,
                        events,
                        eventSink,
                        eventJournal.requestStartedNanos(request));
                Set<String> after = authoritativeSourceTypes(events);
                eventJournal.record(events, eventSink, OpsRuntimeEvent.builder()
                        .eventType("CHANGE_PACKAGE_EVIDENCE_COMPLETION_FINISHED")
                        .status(after.size() >= required ? "SUCCEEDED" : "INSUFFICIENT")
                        .summary("ChangePackage PREPARE 前权威实时证据补全完成。")
                        .payload(Map.of(
                                "attempt", attempt,
                                "requiredSourceCount", required,
                                "sourceTypes", List.copyOf(after)))
                        .build());
            } catch (RuntimeException error) {
                eventJournal.record(events, eventSink, OpsRuntimeEvent.builder()
                        .eventType("CHANGE_PACKAGE_EVIDENCE_COMPLETION_FAILED")
                        .status("FAILED")
                        .summary("ChangePackage 证据补全失败：" + value(error.getMessage()))
                        .build());
                return false;
            }
        }
        return authoritativeSourceTypes(events).size() >= required;
    }

    private OpsRuntimeResourceBundle evidenceCompletionBundle(OpsRuntimeResourceBundle bundle) {
        if (bundle == null) return null;
        List<ToolCallback> evidenceTools = bundle.getTools() == null
                ? List.of()
                : bundle.getTools().stream()
                .filter(callback -> isAuthoritativeDatasourceTool(callback)
                        || isReadOnlyOpenApiDiscoveryTool(callback))
                .toList();
        bundle.setTools(evidenceTools);
        bundle.setSkillContext("");
        bundle.setSkillNames(List.of());
        bundle.setRagEnabled(false);
        bundle.setKnowledgeBaseId("");
        bundle.setMcpIds(List.of());
        bundle.setMcpServers(List.of());
        return bundle;
    }

    private Set<String> availableAuthoritativeSourceTypes(OpsRuntimeResourceBundle bundle) {
        Set<String> result = new LinkedHashSet<>();
        if (bundle == null || bundle.getTools() == null) return result;
        for (ToolCallback callback : bundle.getTools()) {
            String sourceType = datasourceSourceType(callback);
            if (!sourceType.isBlank()) result.add(sourceType);
        }
        return result;
    }

    private Set<String> authoritativeSourceTypes(List<OpsRuntimeEvent> events) {
        Set<String> result = new LinkedHashSet<>();
        if (events == null || events.isEmpty()) return result;
        List<OpsRuntimeEvent> snapshot;
        synchronized (events) {
            snapshot = new ArrayList<>(events);
        }
        for (OpsRuntimeEvent event : snapshot) {
            if (event == null
                    || !"SOURCE_QUERY_FINISHED".equals(event.getEventType())
                    || !"SUCCEEDED".equalsIgnoreCase(value(event.getStatus()))) continue;
            Map<String, Object> payload = event.getPayload() == null ? Map.of() : event.getPayload();
            String sourceType = text(payload.get("sourceType")).toUpperCase();
            if (Boolean.TRUE.equals(payload.get("verified"))
                    && AUTHORITATIVE_DATASOURCE_TYPES.contains(sourceType)) {
                result.add(sourceType);
            }
        }
        return result;
    }

    private boolean isAuthoritativeDatasourceTool(ToolCallback callback) {
        return !datasourceSourceType(callback).isBlank();
    }

    private String datasourceSourceType(ToolCallback callback) {
        if (callback == null || callback.getToolDefinition() == null) return "";
        String name = text(callback.getToolDefinition().name()).toLowerCase();
        if (OpsDatasourceRuntimeToolProvider.PROMETHEUS_TOOL.equals(name)) return "PROMETHEUS";
        if (OpsDatasourceRuntimeToolProvider.ELASTICSEARCH_TOOL.equals(name)) return "ELASTICSEARCH";
        if (name.contains("mysql") && (name.contains("slow") || name.contains("sql"))) return "MYSQL_SLOW_SQL";
        return "";
    }

    private OpsRuntimeResourceBundle assembleGovernanceResources(
            OpsAgentDefinition definition,
            OpsWorkflowNode resourceNode,
            OpsAgentChatRequest request,
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink) {
        if (request == null) {
            return resourceNode == null
                    ? resourceAssembler.assembleAgent(definition, null, events, eventSink)
                    : resourceAssembler.assembleNode(definition, resourceNode, null, events, eventSink);
        }
        Map<String, Object> metadata = request.getMetadata();
        if (metadata == null) {
            metadata = new LinkedHashMap<>();
            request.setMetadata(metadata);
        }
        boolean hadPrevious = metadata.containsKey(OpsChangePackageRuntimeToolContributor.GOVERNANCE_PHASE_KEY);
        Object previous = metadata.get(OpsChangePackageRuntimeToolContributor.GOVERNANCE_PHASE_KEY);
        metadata.put(OpsChangePackageRuntimeToolContributor.GOVERNANCE_PHASE_KEY, true);
        try {
            return resourceNode == null
                    ? resourceAssembler.assembleAgent(definition, request, events, eventSink)
                    : resourceAssembler.assembleNode(definition, resourceNode, request, events, eventSink);
        } finally {
            if (hadPrevious) {
                metadata.put(OpsChangePackageRuntimeToolContributor.GOVERNANCE_PHASE_KEY, previous);
            } else {
                metadata.remove(OpsChangePackageRuntimeToolContributor.GOVERNANCE_PHASE_KEY);
            }
        }
    }

    private String openApiResourceSnapshot(List<OpsRuntimeEvent> events) {
        if (events == null || events.isEmpty()) return "当前项目未提供或尚未完成 OpenAPI 资源目录预检。";
        List<OpsRuntimeEvent> snapshot;
        synchronized (events) {
            snapshot = new ArrayList<>(events);
        }
        for (int index = snapshot.size() - 1; index >= 0; index--) {
            OpsRuntimeEvent event = snapshot.get(index);
            if (event == null || !"SUCCEEDED".equalsIgnoreCase(value(event.getStatus()))) continue;
            Map<String, Object> payload = event.getPayload() == null ? Map.of() : event.getPayload();
            if ("CHANGE_PACKAGE_OPENAPI_PREFLIGHT_FINISHED".equals(event.getEventType())) {
                String output = text(payload.get("output"));
                if (!output.isBlank()) return output;
            }
            if ("TOOL_CALL_FINISHED".equals(event.getEventType())) {
                String toolName = text(payload.get("toolName")).toLowerCase();
                String input = text(payload.get("input")).toLowerCase();
                if (toolName.contains("openapi") && input.contains("openapi_list_operations")) {
                    String output = text(payload.get("output"));
                    if (!output.isBlank()) return output;
                }
            }
        }
        return "当前项目未提供或尚未完成 OpenAPI 资源目录预检。";
    }

    private String runtimeEvidenceSnapshot(List<OpsRuntimeEvent> events) {
        if (events == null || events.isEmpty()) {
            return "无已校验的权威实时证据。";
        }
        List<OpsRuntimeEvent> snapshot;
        synchronized (events) {
            snapshot = new ArrayList<>(events);
        }
        List<String> evidence = new ArrayList<>();
        for (OpsRuntimeEvent event : snapshot) {
            if (evidence.size() >= MAX_RUNTIME_EVIDENCE
                    || event == null
                    || !"SOURCE_QUERY_FINISHED".equals(event.getEventType())
                    || !"SUCCEEDED".equalsIgnoreCase(value(event.getStatus()))) {
                continue;
            }
            Map<String, Object> payload = event.getPayload() == null ? Map.of() : event.getPayload();
            String sourceType = text(payload.get("sourceType")).toUpperCase();
            String resultId = text(payload.get("resultId"));
            String evidenceId = text(payload.get("evidenceId"));
            String outputHash = text(payload.get("outputHash")).toLowerCase();
            Object structuredSummary = payload.get("structuredSummary");
            if (!Boolean.TRUE.equals(payload.get("verified"))
                    || !AUTHORITATIVE_DATASOURCE_TYPES.contains(sourceType)
                    || resultId.isBlank()
                    || evidenceId.isBlank()
                    || !outputHash.matches("[0-9a-f]{64}")
                    || structuredSummary == null
                    || !outputHash.equals(CanonicalObjectHasher.sha256(structuredSummary))) {
                continue;
            }
            String summary = JSON.toJSONString(structuredSummary);
            if (summary.length() > MAX_RUNTIME_EVIDENCE_CHARS) {
                summary = summary.substring(0, MAX_RUNTIME_EVIDENCE_CHARS);
            }
            evidence.add("- sourceType=" + sourceType
                    + ", resultId=" + resultId
                    + ", evidenceId=" + evidenceId
                    + ", outputHash=" + outputHash
                    + "\n  structuredSummary=" + summary);
        }
        return evidence.isEmpty()
                ? "无已校验的权威实时证据。"
                : String.join("\n", evidence);
    }

    private OpsRuntimeResourceBundle governedPrepareBundle(OpsRuntimeResourceBundle bundle) {
        if (bundle == null) {
            return null;
        }
        List<ToolCallback> prepareTools = bundle.getTools() == null
                ? List.of()
                : bundle.getTools().stream()
                .filter(this::isPrepareChangePackageTool)
                .toList();
        bundle.setTools(prepareTools);
        bundle.setSkillContext("");
        bundle.setSkillNames(List.of());
        bundle.setRagEnabled(false);
        bundle.setKnowledgeBaseId("");
        bundle.setMcpIds(List.of());
        bundle.setMcpServers(List.of());
        return bundle;
    }

    private boolean hasSuccessfulReactOutcome(List<OpsRuntimeEvent> events) {
        if (events == null || events.isEmpty()) return false;
        List<OpsRuntimeEvent> snapshot;
        synchronized (events) {
            snapshot = new ArrayList<>(events);
        }
        return snapshot.stream().anyMatch(event -> event != null
                && "REACT_OUTCOME".equals(event.getEventType())
                && "SUCCEEDED".equalsIgnoreCase(value(event.getStatus())));
    }

    private void recordWorkflowOutcome(
            OpsAgentChatRequest request,
            List<OpsRuntimeEvent> events,
            Consumer<OpsRuntimeEvent> eventSink) {
        Set<String> sourceTypes = authoritativeSourceTypes(events);
        String evidenceCompleteness = sourceTypes.size() >= 2
                ? "COMPLETE"
                : sourceTypes.size() == 1 ? "PARTIAL" : "INSUFFICIENT";
        String classification = taskActionClassification(request);
        if (classification.isBlank()) {
            classification = "REMEDIATE";
        }
        Map<String, Object> outcome = new LinkedHashMap<>();
        outcome.put("actionClassification", classification);
        outcome.put("requiresAction", true);
        outcome.put("verificationStatus", "NOT_APPLICABLE");
        outcome.put("abstained", false);
        outcome.put("evidenceCompleteness", evidenceCompleteness);
        outcome.put("source", "CHANGE_PACKAGE_GOVERNANCE");
        outcome.put("authoritativeSourceTypes", List.copyOf(sourceTypes));
        eventJournal.record(events, eventSink, OpsRuntimeEvent.builder()
                .eventType("WORKFLOW_OUTCOME")
                .nodeType("WORKFLOW_OUTCOME")
                .agent("change-package-governance")
                .status("SUCCEEDED")
                .summary("主 ReAct outcome 缺失，已由受控 Workflow 终态事实补齐结构化 outcome。")
                .payload(Map.copyOf(outcome))
                .build());
    }

    private String taskActionClassification(OpsAgentChatRequest request) {
        if (request == null || request.getMetadata() == null) return "";
        String classification = text(request.getMetadata().get(
                OpsWorkSessionContextMetadataKeys.TASK_ACTION_CLASSIFICATION)).toUpperCase();
        return Set.of("DIAGNOSE", "REMEDIATE", "VERIFY", "ABSTAIN").contains(classification)
                ? classification
                : "";
    }

    private boolean isPrepareChangePackageTool(ToolCallback callback) {
        return callback != null
                && callback.getToolDefinition() != null
                && "PrepareChangePackage".equals(callback.getToolDefinition().name());
    }

    private boolean isReadOnlyOpenApiDiscoveryTool(ToolCallback callback) {
        if (callback == null || callback.getToolDefinition() == null) return false;
        String name = text(callback.getToolDefinition().name()).toLowerCase();
        return name.startsWith("openapi_")
                || ((name.startsWith("project_mcp_") || name.startsWith("mcp_tool_catalog_"))
                && name.contains("openapi"));
    }

    boolean proposalCreated(List<OpsRuntimeEvent> events, String nodeId) {
        if (events == null || events.isEmpty()) {
            return false;
        }
        synchronized (events) {
            return events.stream()
                    .filter(event -> event != null && "TOOL_CALL_FINISHED".equals(event.getEventType()))
                    .filter(event -> "SUCCEEDED".equalsIgnoreCase(event.getStatus()))
                    .filter(event -> nodeId == null || nodeId.isBlank() || nodeId.equals(event.getNodeId()))
                    .map(OpsRuntimeEvent::getPayload)
                    .filter(java.util.Objects::nonNull)
                    .anyMatch(payload -> "PrepareChangePackage".equals(payload.get("toolName")));
        }
    }

    private OpsWorkflowNode changePackageNode(OpsAgentDefinition definition, OpsWorkflowNode currentNode) {
        if (currentNode != null && Boolean.TRUE.equals(currentNode.getChangePackageEnabled())) {
            return currentNode;
        }
        if (definition == null || definition.getNodes() == null) {
            return null;
        }
        return definition.getNodes().stream()
                .filter(java.util.Objects::nonNull)
                .filter(candidate -> Boolean.TRUE.equals(candidate.getChangePackageEnabled()))
                .findFirst()
                .orElse(null);
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
