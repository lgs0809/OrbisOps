package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.changepackage.ChangePackageLandingJournalPort;
import cn.lgs.orbisops.application.changepackage.LandingOperationFact;
import cn.lgs.orbisops.application.toolexecution.ToolExecutionIdempotencyPort;
import cn.lgs.orbisops.domain.analysis.service.AnalysisTaskPresentationPolicy;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;
import cn.lgs.orbisops.domain.worksession.runtime.model.TriggerSource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Runs the platform-owned ReAct definition under LANDING/PROD_FULL runtime authority. */
@Component
public final class OpsApprovedLandingAgentRunCoordinator {

    private static final OpsPlatformLandingRuntimeDefinitionFactory LANDING_DEFINITIONS =
            new OpsPlatformLandingRuntimeDefinitionFactory();

    private final UnifiedAgentRuntime runtime;
    private final ChangePackageLandingJournalPort journalPort;
    private final ToolExecutionIdempotencyPort toolExecutionLedger;
    private final OpsLandingIndependentVerifier verifier;

    public OpsApprovedLandingAgentRunCoordinator(
            UnifiedAgentRuntime runtime,
            ChangePackageLandingJournalPort journalPort,
            ToolExecutionIdempotencyPort toolExecutionLedger,
            OpsLandingIndependentVerifier verifier) {
        if (runtime == null) throw new IllegalArgumentException("UNIFIED_AGENT_RUNTIME_REQUIRED");
        if (toolExecutionLedger == null) {
            throw new IllegalArgumentException("TOOL_EXECUTION_IDEMPOTENCY_PORT_REQUIRED");
        }
        this.runtime = runtime;
        this.journalPort = journalPort;
        this.toolExecutionLedger = toolExecutionLedger;
        this.verifier = java.util.Objects.requireNonNull(verifier);
    }

    public Map<String, Object> execute(ApprovedLandingAgentCommand command) {
        if (command == null) throw new IllegalArgumentException("APPROVED_LANDING_COMMAND_REQUIRED");
        OpsAgentDefinition definition = LANDING_DEFINITIONS.create(command.approvedPackage().projectId());

        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("_trustedTriggerSource", TriggerSource.LANDING);
        metadata.put(OpsAgentRunExecutionContextFactory.TRUSTED_APPROVED_PACKAGE_KEY, command.approvedPackage());
        metadata.put("changePackageId", command.approvedPackage().packageId());
        metadata.put("preparationSessionId", command.current().sessionId());
        metadata.put("approvedPackageHash", command.approvedPackage().packageHash());
        metadata.put("approvedPackageVersion", command.approvedPackage().packageVersion());
        metadata.put("landingRuntimeId", OpsPlatformLandingRuntimeDefinitionFactory.RUNTIME_ID);
        metadata.put("landingRuntimeVersion", OpsPlatformLandingRuntimeDefinitionFactory.RUNTIME_VERSION);
        metadata.put("landingPolicyVersion", OpsPlatformLandingRuntimeDefinitionFactory.POLICY_VERSION);
        metadata.put("executionStyle", "REACT");
        metadata.put("landingGraphEditable", false);
        metadata.put(AnalysisTaskPresentationPolicy.PUBLIC_RUN_GOAL_METADATA_KEY,
                "受控变更包 " + command.approvedPackage().packageId() + " 的落地运行");

        OpsAgentChatRequest request = OpsAgentChatRequest.builder()
                .userId(command.actor())
                .sessionId(command.workSessionId())
                .runId(command.landingRunId())
                .query(command.instruction())
                .mode("AGENT")
                .engine("STATE_GRAPH")
                .projectId(command.approvedPackage().projectId())
                .agentDefinitionId(definition.getAgentId())
                .agentVersion(definition.getVersion())
                .agentDefinition(definition)
                .metadata(metadata)
                .build();

        try {
            OpsAgentChatResponse response = runtime.execute(request);
            return outcome(command, definition, response, null);
        } catch (RuntimeException error) {
            return outcome(command, definition, null, error);
        }
    }

    public Map<String, Object> verifyCompletedOperations(String landingRunId,
            cn.lgs.orbisops.domain.changepackage.model.ChangePackageLandingPlan plan,
            String actor, List<LandingOperationFact> facts) {
        return verifier.verifyCompleted(landingRunId, plan, actor, facts);
    }

    private Map<String, Object> outcome(
            ApprovedLandingAgentCommand command,
            OpsAgentDefinition definition,
            OpsAgentChatResponse response,
            RuntimeException runtimeFailure) {
        List<LandingOperationFact> facts = journalPort == null || !journalPort.available()
                ? List.of()
                : journalPort.operationFacts(command.landingRunId());
        boolean unresolvedUnknown = facts.stream().anyMatch(LandingOperationFact::unknown);
        boolean unresolvedToolSideEffect = toolExecutionLedger.hasUnresolvedSideEffect(
                command.approvedPackage().projectId(), command.landingRunId());
        boolean anyCompleted = facts.stream().anyMatch(fact -> fact.succeeded()
                && !fact.resultId().isBlank() && fact.outputHash().matches("[a-f0-9]{64}")
                && command.approvedPlan().operations().stream().anyMatch(operation ->
                        operation.operationId().equals(fact.operationId())
                        && List.of("WRITE", "MUTATE_TARGET_RESOURCE", "EXECUTE_EXTERNAL_ACTION",
                                "DELETE_TARGET_RESOURCE").contains(operation.effectType().toUpperCase(Locale.ROOT))));
        LinkedHashMap<String, Object> result = base(command, definition, response, facts);

        if (unresolvedUnknown || unresolvedToolSideEffect) {
            result.put("status", ChangePackageStatus.LANDING_FAILED.name());
            result.put("eventType", "LANDING_RECONCILIATION_REQUIRED");
            result.put("reasonCode", "LANDING_EXECUTION_RESULT_UNKNOWN");
            result.put("summary", "存在尚未对账的 UNKNOWN 生产执行结果，停止自动执行并要求先核对真实状态。");
            result.put("executedProductionAction", anyCompleted || unresolvedToolSideEffect);
            result.put("manualInterventionRequired", true);
            result.put("reconciliationRequired", true);
            result.put("toolExecutionUncertainty", unresolvedToolSideEffect);
            return Map.copyOf(result);
        }
        if (runtimeFailure != null) {
            result.put("status", ChangePackageStatus.LANDING_FAILED.name());
            result.put("eventType", "LANDING_FAILED");
            result.put("reasonCode", "LANDING_AGENT_RUNTIME_FAILED");
            result.put("summary", "Landing ReAct 运行失败；方案本身未被判定失效。");
            result.put("runtimeError", text(runtimeFailure.getMessage()));
            result.put("executedProductionAction", anyCompleted);
            return Map.copyOf(result);
        }

        String finalStatus = finalStatus(response == null ? "" : response.getContent());
        if (ChangePackageStatus.NEEDS_REPLAN.name().equals(finalStatus)) {
            result.put("status", ChangePackageStatus.NEEDS_REPLAN.name());
            result.put("eventType", "LANDING_NEEDS_REPLAN");
            result.put("reasonCode", "APPROVED_PLAN_NO_LONGER_SUFFICIENT");
            result.put("summary", "Landing 判断继续执行将实质改变已审批方案，返回 REVISING 重新规划并再次审批。");
            result.put("executedProductionAction", anyCompleted);
            result.put("replanTriggers", List.of("APPROVED_PLAN_NO_LONGER_SUFFICIENT"));
            return Map.copyOf(result);
        }
        if (ChangePackageStatus.LANDED.name().equals(finalStatus)) {
            Map<String, Object> verification;
            try {
                verification = verifier.verify(command, facts);
            } catch (RuntimeException unavailable) {
                result.put("status", ChangePackageStatus.LANDING_FAILED.name());
                result.put("eventType", "LANDING_FAILED");
                result.put("reasonCode", "LANDING_VERIFICATION_NOT_RECORDED");
                result.put("executedProductionAction", anyCompleted);
                return Map.copyOf(result);
            }
            boolean passed = Boolean.TRUE.equals(verification.get("passed"));
            result.put("status", passed ? ChangePackageStatus.LANDED.name() : ChangePackageStatus.LANDING_FAILED.name());
            result.put("eventType", passed ? "LANDING_SUCCEEDED" : "LANDING_FAILED");
            result.put("reasonCode", passed ? "" : "LANDING_INDEPENDENT_VERIFICATION_FAILED");
            result.put("independentVerification", verification);
            result.put("executedProductionAction", anyCompleted);
            return Map.copyOf(result);
        }

        if (ChangePackageStatus.LANDING_FAILED.name().equals(finalStatus)) {
            result.put("status", ChangePackageStatus.LANDING_FAILED.name());
            result.put("eventType", "LANDING_FAILED");
            result.put("reasonCode", "LANDING_EXECUTION_FAILED");
            result.put("summary", "Landing ReAct 判断方案仍有效，但本次生产执行失败。");
            result.put("executedProductionAction", anyCompleted);
            return Map.copyOf(result);
        }

        result.put("status", ChangePackageStatus.LANDING_FAILED.name());
        result.put("eventType", "LANDING_FAILED");
        result.put("reasonCode", "LANDING_FINAL_STATUS_INVALID");
        result.put("summary", "Landing ReAct 未返回可判定的最终状态，按执行失败处理而不是擅自扩大或重规划方案。");
        result.put("executedProductionAction", anyCompleted);
        return Map.copyOf(result);
    }

    private LinkedHashMap<String, Object> base(
            ApprovedLandingAgentCommand command,
            OpsAgentDefinition definition,
            OpsAgentChatResponse response,
            List<LandingOperationFact> facts) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("landingRuntime", OpsUnifiedAgentEngineAdapter.KEY);
        result.put("landingRuntimeRole", "PLATFORM_REACT_PROD_FULL");
        result.put("landingGraphEditable", false);
        result.put("landingAgentRunId", command.landingRunId());
        result.put("landingRuntimeId", definition.getAgentId());
        result.put("landingRuntimeVersion", definition.getVersion());
        result.put("landingPolicyVersion", OpsPlatformLandingRuntimeDefinitionFactory.POLICY_VERSION);
        result.put("approvedPackageHash", command.approvedPackage().packageHash());
        result.put("approvedPackageVersion", command.approvedPackage().packageVersion());
        result.put("agentObservation", response == null ? "" : text(response.getContent()));
        result.put("agentEventCount", eventCount(response));
        result.put("operationFacts", facts == null ? List.of() : facts.stream().map(LandingOperationFact::payload).toList());
        return result;
    }

    private String finalStatus(String content) {
        String normalized = text(content);
        if (normalized.isBlank()) return "";
        for (String rawLine : normalized.split("\\R")) {
            String line = text(rawLine).toUpperCase(Locale.ROOT);
            if (line.isBlank()) continue;
            if (ChangePackageStatus.NEEDS_REPLAN.name().equals(line)) {
                return ChangePackageStatus.NEEDS_REPLAN.name();
            }
            if (ChangePackageStatus.LANDING_FAILED.name().equals(line)) {
                return ChangePackageStatus.LANDING_FAILED.name();
            }
            if (ChangePackageStatus.LANDED.name().equals(line)) {
                return ChangePackageStatus.LANDED.name();
            }
            // Landing is a production boundary: narrative text that merely mentions a
            // status token is not authoritative. Any non-conforming first line fails closed.
            return "";
        }
        return "";
    }

    private int eventCount(OpsAgentChatResponse response) {
        return response == null || response.getEvents() == null ? 0 : response.getEvents().size();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
