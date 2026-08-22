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

    @Autowired
    public OpsChangePackageToolProvider(ObjectProvider<OpsToolExecutionService> toolExecutionServiceProvider,
                                        OpsOwningPrepareValidationService owningPrepareValidationService,
                                        OpsChangePackageActionPolicyBinder actionPolicyBinder) {
        this(toolExecutionServiceProvider == null
                        ? null
                        : toolExecutionServiceProvider.getIfAvailable(),
                owningPrepareValidationService,
                actionPolicyBinder);
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
        this.executionGateway = new OpsChangePackageToolExecutionGateway(
                toolExecutionService,
                owningPrepareValidationService);
        this.actionPolicyBinder = actionPolicyBinder;
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
                    EVIDENCE_COLLECTOR.collect(runId, events);
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
                .build();
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
                        "PROMETHEUS", "ELASTICSEARCH", "MYSQL_SLOW_SQL", "MYSQL", "RABBITMQ", "REDIS")
                        .contains(text(item.sourceType()).toUpperCase()))
                .peek(item -> actualSourceTypes.add(text(item.sourceType()).toUpperCase()))
                .toList();
        int requiredDistinct = availableAuthoritativeSources == null || availableAuthoritativeSources.isEmpty()
                ? 1
                : Math.min(2, availableAuthoritativeSources.size());
        if (actualSourceTypes.size() < requiredDistinct) {
            throw new IllegalArgumentException(
                    "CHANGE_PACKAGE_AUTHORITATIVE_EVIDENCE_REQUIRED: requiredDistinct="
                            + requiredDistinct
                            + ", actual=" + actualSourceTypes
                            + ", available=" + (availableAuthoritativeSources == null
                            ? Set.of()
                            : availableAuthoritativeSources));
        }
        return authoritative;
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
            if (Set.of("PROMETHEUS", "ELASTICSEARCH", "MYSQL_SLOW_SQL", "MYSQL", "RABBITMQ", "REDIS")
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

                actions/mcpSteps 必须描述 operationId、mcpId/toolName、arguments、rollback 和 verification；
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
