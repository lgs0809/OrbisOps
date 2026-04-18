package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import cn.lgs.orbisops.trigger.ops.runtime.OpsChangePackageRuntimeToolContributor;
import cn.lgs.orbisops.trigger.ops.runtime.OpsRuntimeEvent;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkSessionClaimMetadata;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Spring AI adapter for the evidence-bound PrepareChangePackage tool. */
@Service
public class OpsChangePackageToolProvider {

    private static final String TOOL_NAME = "PrepareChangePackage";
    private static final List<String> RUNTIME_METADATA_KEYS = List.of(
            OpsWorkSessionClaimMetadata.ATTEMPT_ID,
            OpsWorkSessionClaimMetadata.LEASE_TOKEN,
            OpsWorkSessionClaimMetadata.FENCING_TOKEN,
            OpsWorkSessionClaimMetadata.STATE_VERSION,
            OpsWorkSessionClaimMetadata.RUN_MANIFEST_HASH,
            "executionHarness");
    private static final OpsChangePackageRunEvidenceCollector EVIDENCE_COLLECTOR =
            new OpsChangePackageRunEvidenceCollector();
    private static final OpsChangePackageToolRequestFactory REQUEST_FACTORY =
            new OpsChangePackageToolRequestFactory();
    private static final OpsChangePackageToolResultRenderer RESULT_RENDERER =
            new OpsChangePackageToolResultRenderer();

    private final OpsChangePackageToolExecutionGateway executionGateway;
    private final OpsChangePackageActionPolicyBinder actionPolicyBinder;
    private final OpsStoredMcpPreparationEvidence storedMcpEvidence;

    @Autowired
    public OpsChangePackageToolProvider(ObjectProvider<OpsToolExecutionService> toolExecutionServiceProvider,
                                        OpsOwningPrepareValidationService owningPrepareValidationService,
                                        OpsChangePackageActionPolicyBinder actionPolicyBinder,
                                        OpsStoredMcpPreparationEvidence storedMcpEvidence) {
        this(toolExecutionServiceProvider == null
                        ? null
                        : toolExecutionServiceProvider.getIfAvailable(),
                owningPrepareValidationService,
                actionPolicyBinder, storedMcpEvidence);
    }

    OpsChangePackageToolProvider(OpsToolExecutionService toolExecutionService) {
        this(toolExecutionService, null, null);
    }

    OpsChangePackageToolProvider(OpsToolExecutionService toolExecutionService,
                                 OpsOwningPrepareValidationService owningPrepareValidationService) {
        this(toolExecutionService, owningPrepareValidationService, null);
    }

    OpsChangePackageToolProvider(OpsToolExecutionService toolExecutionService,
                                 OpsOwningPrepareValidationService owningPrepareValidationService,
                                 OpsChangePackageActionPolicyBinder actionPolicyBinder) {
        this(toolExecutionService, owningPrepareValidationService, actionPolicyBinder, null);
    }

    OpsChangePackageToolProvider(OpsToolExecutionService toolExecutionService,
                                 OpsOwningPrepareValidationService owningPrepareValidationService,
                                 OpsChangePackageActionPolicyBinder actionPolicyBinder,
                                 OpsStoredMcpPreparationEvidence storedMcpEvidence) {
        this.executionGateway = new OpsChangePackageToolExecutionGateway(
                toolExecutionService,
                owningPrepareValidationService);
        this.actionPolicyBinder = actionPolicyBinder;
        this.storedMcpEvidence = storedMcpEvidence;
    }

    public record StatusQuery(String reason) {}

    public ToolCallback buildStatusQuery(String projectId, String actor, String runId, OpsAgentChatRequest request) {
        Function<StatusQuery, String> query = input -> RESULT_RENDERER.statusQuery(
                executionGateway.readStatus(executionContext(projectId, actor, runId, request)));
        return FunctionToolCallback.builder("QueryChangePackageStatus", query)
                .description("只读查询当前项目最近二十条真实变更记录。核对方案是否已保存、是否待审批时必须使用此工具；"
                        + "按实际 packageId、标题和状态辨认对应方案，存在其他方案不代表本次已创建。"
                        + "工具返回不完整或失败时不得声称已保存或已审批。此工具不创建、审批或执行变更。")
                .inputType(StatusQuery.class).build();
    }

    public boolean available(String projectId, List<String> executionTargetIds) {
        return projectId != null && !projectId.trim().isEmpty();
    }

    public ToolCallback build(String projectId,
                              String actor,
                              String runId,
                              String contextBundleId,
                              String contextBundleHash,
                              List<OpsRuntimeEvent> events,
                              List<String> executionTargetIds) {
        return build(
                projectId,
                actor,
                runId,
                contextBundleId,
                contextBundleHash,
                events,
                executionTargetIds,
                null);
    }

    public ToolCallback build(String projectId,
                              String actor,
                              String runId,
                              String contextBundleId,
                              String contextBundleHash,
                              List<OpsRuntimeEvent> events,
                              List<String> executionTargetIds,
                              OpsAgentChatRequest runtimeRequest) {
        Set<String> availableAuthoritativeSources = availableAuthoritativeSourceTypes(runtimeRequest);
        Function<OpsChangePackageToolInput, String> function = input -> {
            List<Map<String, Object>> governedActions = actionPolicyBinder == null
                    ? (input == null || input.getActions() == null ? List.of() : List.copyOf(input.getActions()))
                    : actionPolicyBinder.bind(projectId, input == null ? List.of() : input.getActions());
            List<OpsChangePackageRunEvidenceCollector.Evidence> collectedEvidence =
                    new java.util.ArrayList<>(EVIDENCE_COLLECTOR.collect(runId, evidenceEvents(runtimeRequest, events)));
            if (storedMcpEvidence != null) collectedEvidence.addAll(storedMcpEvidence.collect(projectId, runId));
            List<OpsChangePackageRunEvidenceCollector.Evidence> evidence = governedEvidence(
                    governedActions, collectedEvidence, availableAuthoritativeSources);
            Map<String, Object> request = REQUEST_FACTORY.create(
                    new OpsChangePackageToolRequestFactory.Context(
                            projectId,
                            runId,
                            contextBundleId,
                            contextBundleHash),
                    input,
                    evidence,
                    governedActions);
            Map<String, Object> changePackage = executionGateway.execute(
                    request,
                    executionContext(projectId, actor, runId, runtimeRequest));
            int actionCount = input.getActions() == null ? 0 : input.getActions().size();
            return RESULT_RENDERER.success(changePackage, evidence.size(), actionCount);
        };
        return FunctionToolCallback.builder(TOOL_NAME, function)
                .description(description(projectId, executionTargetIds))
                .inputType(OpsChangePackageToolInput.class)
                .inputSchema(OpsChangePackageToolSchema.inputSchema())
                .build();
    }

    private List<OpsRuntimeEvent> evidenceEvents(
            OpsAgentChatRequest runtimeRequest,
            List<OpsRuntimeEvent> currentEvents) {
        List<OpsRuntimeEvent> prior = priorSessionEvidence(runtimeRequest);
        if (prior.isEmpty()) return currentEvents == null ? List.of() : currentEvents;
        List<OpsRuntimeEvent> merged = new java.util.ArrayList<>(prior.size()
                + (currentEvents == null ? 0 : currentEvents.size()));
        merged.addAll(prior);
        if (currentEvents != null) merged.addAll(currentEvents);
        return merged;
    }

    private List<OpsRuntimeEvent> priorSessionEvidence(OpsAgentChatRequest runtimeRequest) {
        if (runtimeRequest == null || runtimeRequest.getMetadata() == null) return List.of();
        Object value = runtimeRequest.getMetadata().get(
                OpsChangePackageRuntimeToolContributor.SESSION_EVIDENCE_KEY);
        if (!(value instanceof Iterable<?> iterable)) return List.of();
        List<OpsRuntimeEvent> result = new java.util.ArrayList<>();
        for (Object item : iterable) {
            if (item instanceof OpsRuntimeEvent event) result.add(event);
        }
        return List.copyOf(result);
    }

    private OpsChangePackageToolExecutionGateway.Context executionContext(
            String projectId,
            String actor,
            String runId,
            OpsAgentChatRequest runtimeRequest) {
        if (runtimeRequest == null) {
            return new OpsChangePackageToolExecutionGateway.Context(
                    projectId, actor, runId, "", Map.of(), false);
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (runtimeRequest.getMetadata() != null) {
            for (String key : RUNTIME_METADATA_KEYS) {
                if (runtimeRequest.getMetadata().containsKey(key)) {
                    metadata.put(key, runtimeRequest.getMetadata().get(key));
                }
            }
        }
        return new OpsChangePackageToolExecutionGateway.Context(
                projectId,
                actor,
                runId,
                text(runtimeRequest.getSessionId()),
                metadata,
                true);
    }

    private List<OpsChangePackageRunEvidenceCollector.Evidence> governedEvidence(
            List<Map<String, Object>> governedActions,
            List<OpsChangePackageRunEvidenceCollector.Evidence> evidence,
            Set<String> availableAuthoritativeSources) {
        List<OpsChangePackageRunEvidenceCollector.Evidence> safeEvidence =
                evidence == null ? List.of() : List.copyOf(evidence);
        if (!requiresAuthoritativeRuntimeEvidence(governedActions)) return safeEvidence;

        Set<String> actualSourceTypes = new LinkedHashSet<>();
        List<OpsChangePackageRunEvidenceCollector.Evidence> authoritative = safeEvidence.stream()
                .filter(item -> item != null && Set.of(
                        "PROMETHEUS", "ELASTICSEARCH", "MYSQL_SLOW_SQL", "MYSQL", "RABBITMQ", "REDIS",
                        "SERVICE_CONTROL", "MCP_REMOTE")
                        .contains(text(item.sourceType()).toUpperCase()))
                .peek(item -> actualSourceTypes.add(text(item.sourceType()).toUpperCase()))
                .toList();
        boolean serviceControlAuthorityAvailable = (availableAuthoritativeSources != null
                && availableAuthoritativeSources.contains("SERVICE_CONTROL"))
                || authoritative.stream().anyMatch(item ->
                        "SERVICE_CONTROL".equalsIgnoreCase(text(item.sourceType())));
        if (serviceControlAction(governedActions) && serviceControlAuthorityAvailable) {
            List<OpsChangePackageRunEvidenceCollector.Evidence> serviceEvidence = authoritative.stream()
                    .filter(item -> "SERVICE_CONTROL".equalsIgnoreCase(text(item.sourceType())))
                    .toList();
            if (serviceControlEvidenceComplete(serviceEvidence)) return serviceEvidence;
            throw new IllegalArgumentException(
                    "CHANGE_PACKAGE_SERVICE_CONTROL_EVIDENCE_REQUIRED: get_service_status + restart_service_dry_run");
        }
        Set<String> diagnosticAvailable = new LinkedHashSet<>(
                availableAuthoritativeSources == null ? Set.of() : availableAuthoritativeSources);
        diagnosticAvailable.remove("SERVICE_CONTROL");
        int requiredDistinct = diagnosticAvailable.isEmpty()
                ? 1
                : Math.min(2, diagnosticAvailable.size());
        if (actualSourceTypes.size() < requiredDistinct) {
            throw new IllegalArgumentException(
                    "CHANGE_PACKAGE_AUTHORITATIVE_EVIDENCE_REQUIRED: requiredDistinct="
                            + requiredDistinct
                            + ", actual=" + actualSourceTypes
                            + ", available=" + diagnosticAvailable);
        }
        return authoritative;
    }

    private boolean serviceControlAction(List<Map<String, Object>> governedActions) {
        if (governedActions == null || governedActions.isEmpty()) return false;
        return governedActions.stream().anyMatch(action -> {
            String mcpId = text(action.get("mcpId")).toLowerCase();
            String toolName = text(firstNonNull(action.get("remoteToolName"),
                    action.get("toolName"), action.get("name"))).toLowerCase();
            String resourceScope = text(action.get("resourceScope")).toLowerCase();
            return mcpId.contains("service-control") || mcpId.contains("service_control")
                    || resourceScope.startsWith("service-control://")
                    || "get_service_status".equals(toolName)
                    || "restart_service_dry_run".equals(toolName)
                    || "restart_service".equals(toolName)
                    || "get_operation_receipt".equals(toolName);
        });
    }

    private boolean serviceControlEvidenceComplete(
            List<OpsChangePackageRunEvidenceCollector.Evidence> evidence) {
        Set<String> toolNames = new LinkedHashSet<>();
        for (OpsChangePackageRunEvidenceCollector.Evidence item : evidence == null ? List.<OpsChangePackageRunEvidenceCollector.Evidence>of() : evidence) {
            if (item == null || !"SERVICE_CONTROL".equalsIgnoreCase(text(item.sourceType()))) continue;
            Map<String, Object> metadata = item.metadata();
            String toolName = text(metadata.get("remoteToolName"));
            if (toolName.isBlank()) toolName = text(metadata.get("toolName"));
            if (!toolName.isBlank()) toolNames.add(toolName.toLowerCase());
            if ("restart_service_dry_run".equalsIgnoreCase(toolName)
                    && trustedDryRunObservation(item.summary())) {
                toolNames.add("get_service_status");
            }
        }
        return toolNames.contains("get_service_status")
                && toolNames.contains("restart_service_dry_run");
    }

    private boolean trustedDryRunObservation(String summary) {
        String compact = text(summary).replace("\\", "").replaceAll("\\s+", "");
        return compact.contains("\"validationType\":\"SERVICE_RESTART_DRY_RUN\"")
                && compact.contains("\"status\":\"PASSED\"")
                && compact.contains("\"trustedProviderObservation\":true")
                && compact.contains("\"writesTargetResource\":false")
                && compact.contains("\"expectedVersion\":");
    }

    private Object firstNonNull(Object... values) {
        if (values == null) return null;
        for (Object value : values) if (value != null) return value;
        return null;
    }

    private boolean requiresAuthoritativeRuntimeEvidence(List<Map<String, Object>> governedActions) {
        if (governedActions == null || governedActions.isEmpty()) return false;
        return governedActions.stream().anyMatch(action -> {
            String effectScope = text(action.get("effectScope")).toUpperCase();
            String mutability = text(action.get("mutability")).toUpperCase();
            String riskLevel = text(action.get("riskLevel")).toUpperCase();
            return "PRODUCTION".equals(effectScope)
                    || "PROD_MUTATING".equals(mutability)
                    || "HIGH".equals(riskLevel)
                    || "CRITICAL".equals(riskLevel)
                    || Boolean.TRUE.equals(action.get("requiresDryRun"));
        });
    }

    private Set<String> availableAuthoritativeSourceTypes(OpsAgentChatRequest runtimeRequest) {
        if (runtimeRequest == null || runtimeRequest.getMetadata() == null) return Set.of();
        Object value = runtimeRequest.getMetadata().get(
                OpsChangePackageRuntimeToolContributor.AVAILABLE_AUTHORITATIVE_SOURCE_TYPES_KEY);
        if (!(value instanceof Iterable<?> iterable)) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        for (Object item : iterable) {
            String sourceType = text(item).toUpperCase();
            if (Set.of("PROMETHEUS", "ELASTICSEARCH", "MYSQL_SLOW_SQL", "MYSQL", "RABBITMQ", "REDIS",
                    "SERVICE_CONTROL")
                    .contains(sourceType)) {
                result.add(sourceType);
            }
        }
        return Set.copyOf(result);
    }

    private String description(String projectId, List<String> executionTargetIds) {
        List<Map<String, Object>> proposalCatalog = actionPolicyBinder == null
                ? List.of()
                : actionPolicyBinder.proposalCatalog(projectId);
        return """
                根据当前 Run 已收集的真实证据创建 ChangePackage。该工具只运行 PREPARE，
                不会执行、审批或跳过 Tool Policy / ChangePackage 安全边界。只有根因和修复方向已被 MCP/RAG/工具证据支持时才创建自动化候选；
                如果方案显式声明 validation operation，则该 validation 必须产生可信结果；普通生产 operation 不要求泛化 Sandbox proof。

                当前节点绑定的执行目标引用如下，仅用于说明上下文，真正外部操作必须通过项目 MCP Tool Router / Policy Guard：
                %s

                服务端 ACTIVE + HUMAN_REVIEWED、可用于 ChangePackage 的权威动作目录：
                %s
                actions 只能引用上述目录中的 mcpId + toolName。禁止编造工具身份、schema、effect、mutability 或风险属性；
                服务端会忽略这些由模型声称的治理属性，并按权威 MCP Policy 重新绑定。
                若目标动作 requiresDryRun=true，且目录存在同一 MCP 的 purpose=PRE_APPROVAL_VALIDATION 动作，
                actions 中应先包含 validation，再包含审批后 Landing 动作；PREPARE 只会实际执行策略允许的 validation。
                测试环境已经达到目标状态时优先选择只读 VALIDATE_ONLY 验证，不能重复写入相同版本制造测试。
                必须先通过 Tool Router 获取所选工具的完整 schema，业务参数按真实观测值填写。


                actions 包含可执行 MCP 操作时 packageType 使用 MCP_OPERATION_PACKAGE，或省略以由服务端分类。
                需要人工审批、提供 manualFallback 不等于 MANUAL_REQUIRED 类型；后者以及 NEEDS_HUMAN_DESIGN、NO_ACTION_REQUIRED
                只能用于没有可执行操作的人工处理记录。不要沿用失败方案的人工类型。
                actions 每项必须描述 operationId、mcpId、toolName、arguments。
                Landing 写操作还必须提供 preconditions、postCheck、rollbackPlan、rollbackPrecondition 和 manualFallback（字段名不可替换成 verification/rollback）。
                postCheck 包含 toolsetId（mcp. 加真实 MCP ID）、toolName（只读工具）、arguments、
                resourceIdentityField（默认 resourceKey）、expectedValues（真实预期结果，含与动作 resourceScope 相同的资源身份）。
                expectedValues 可用点号表示嵌套字段，值必须保留原始类型，不得虚构观测结果。
                若成功标准包含配置版本及真实业务请求等多个观测，postCheck 必须用 additionalChecks 数组补充其他只读检查，
                每项同样包含 toolsetId、toolName、arguments、resourceIdentityField、expectedValues；最多 15 项，不允许嵌套。
                每项都必须核对同一目标资源身份，所有检查通过才能完成 Landing，不能用一项成功代替其他成功标准。
                不要把发布后检查另列为 purpose=POST_APPROVAL_VALIDATION 的 actions；它没有业务成功判定。
                所有发布后必需检查都放入写操作的 postCheck / additionalChecks，并填写 expectedValues。
                preconditions 应包含执行前实际资源版本及可核对条件；rollbackPrecondition 描述允许回退的资源状态与版本条件。
                rollbackPlan 描述失败时的受控恢复方案；manualFallback 描述自动恢复无法安全执行时的处置，不代表取消 Landing 自动执行。
                上述五个字段均为 JSON 对象，不能直接填字符串或数组；自然语言说明放在对象的 summary 字段中。
                对象不得为空，不要把恢复描述声称为已执行。additionalChecks 只能放在 postCheck 对象内部。
                approvalBoundary、preferredPlan、adjustmentPolicy 应说明被审批的任务目标、资源范围和调整边界。
                若需 Workflow C 的业务验收，必须在本次准备中传 serviceId 和 verificationCriteria；审批后不能补填或放宽标准。
                verificationCriteria 中只放一个 kind=OBSERVABILITY_SLO_V1 的观测标准，包含真实服务、环境、前后版本、资源身份、路由与采集定义。
                changeKind 区分 RELEASE 和 RECOVERY；恢复性变更必须预先约定 maxErrorRate、maxP95Seconds，以及可比负载 minQps/maxQps。
                错误率上限不得超过 0.01、P95 上限不得超过 1 秒；前后各十五分钟至少一百请求的要求由服务端固定，不能改小。
                缺少实际采集定义或用户目标时先查询或自然语言澄清，禁止编造标准以取得通过。

                effectType/effectScope/mutability/riskLevel 仅可按目录原值表达，最终仍以服务端绑定为准。
                不要传 Shell、原始任意 SQL 或凭据。LAND 阶段只能在审批的 approvalBoundary 内执行。
                """.formatted(
                RESULT_RENDERER.resourceCatalog(executionTargetIds),
                RESULT_RENDERER.policyCatalog(proposalCatalog));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
