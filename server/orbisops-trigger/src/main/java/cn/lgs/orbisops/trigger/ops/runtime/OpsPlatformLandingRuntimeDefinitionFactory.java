package cn.lgs.orbisops.trigger.ops.runtime;

import java.util.List;
import java.util.Map;

/** Platform-owned, non-user-selectable ReAct definition used only for approved LANDING runs. */
public final class OpsPlatformLandingRuntimeDefinitionFactory {

    public static final String RUNTIME_ID = "platform-landing-react";
    public static final int RUNTIME_VERSION = 3;
    public static final String POLICY_VERSION = "landing-policy-v2";

    private static final String INSTRUCTION = """
            You are the platform Landing ReAct runtime. The ChangePackage in this run has already been approved.
            This invocation is the execution phase after all required human approvals. The platform has already granted
            landing authorization for exactly the approved target-write operations in the frozen task book.
            Do not stop because the PREPARE snapshot contains executionNotRequested=true, requiresHumanApproval=true,
            or permissionGranted=false. Those fields describe the pre-approval proposal and are superseded by this
            approved LANDING invocation. In LANDING, permission to execute the frozen target-write operation is granted
            by the platform execution boundary.
            You have the current project's LANDING runtime capabilities, including production read/write MCP and tools.
            Treat the ChangePackage as the approved task book: understand its objective, evidence, plan, verification and rollback guidance,
            then observe the real environment and autonomously choose the tools, order, retries and additional diagnostics needed to complete it.
            Do not treat ChangePackage operations as a per-tool or per-argument ACL.
            If a step fails but the approved plan is still valid, diagnose and continue safely.
            If continuing would materially change the approved plan itself -- for example adding a new system-level change, changing the target,
            requiring a new database migration, changing the deployment strategy/risk, or invalidating the rollback plan -- stop with NEEDS_REPLAN.
            Do not return NEEDS_REPLAN merely because the approved task book was originally created as a no-execution proposal;
            return NEEDS_REPLAN only if the frozen target, effect, operation, or rollback/verification plan has materially changed.
            Return exactly one final status: LANDED, LANDING_FAILED, or NEEDS_REPLAN, plus a concise summary and verification evidence.
            LANDING_FAILED means the plan remains valid but this execution/runtime/infrastructure failed.
            NEEDS_REPLAN means the plan itself is no longer sufficient and must be revised and re-approved.
            User drag/drop workflow definitions are never used for Landing.
            """.trim();

    private static final String DEFINITION_HASH = OpsRuntimeHashing.canonicalHash(Map.of(
            "runtimeId", RUNTIME_ID,
            "runtimeVersion", RUNTIME_VERSION,
            "policyVersion", POLICY_VERSION,
            "instruction", INSTRUCTION,
            "engine", OpsUnifiedAgentEngineAdapter.KEY));

    public OpsAgentDefinition create(String projectId) {
        OpsWorkflowNode landingNode = OpsWorkflowNode.builder()
                .nodeId("landing-react")
                .type("AGENTSCOPE")
                .mode("REACT")
                .agent(RUNTIME_ID)
                .description("Platform-owned production Landing ReAct node")
                .instruction(INSTRUCTION)
                .ragEnabled(false)
                .repairEnabled(false)
                .changePackageEnabled(false)
                .config(Map.of(
                        "inheritProjectCapabilities", true,
                        "inheritProjectMcpCapabilities", true,
                        "executionStyle", "REACT"))
                .build();
        return OpsAgentDefinition.builder()
                .agentId(RUNTIME_ID)
                .schemaVersion(1)
                .version(RUNTIME_VERSION)
                .definitionHash(DEFINITION_HASH)
                .lifecycle("PUBLISHED")
                .phase("LANDING")
                .name("Platform Landing ReAct")
                .projectId(text(projectId))
                .engine(OpsUnifiedAgentEngineAdapter.KEY)
                .description("Platform-owned fixed ReAct runtime with project-scoped PROD_FULL authority")
                .instruction(INSTRUCTION)
                .source("PLATFORM")
                .definitionKind("PLATFORM_LANDING_RUNTIME")
                .workflowInvocationMode("SYSTEM_ONLY")
                .workflowAutoSelectEnabled(false)
                .workflowPriority(Integer.MIN_VALUE)
                .startNodeId("landing-react")
                .defaultMaxMainRounds(24)
                .defaultSubAgentMaxIterations(0)
                .agentScopeMode("DISABLED")
                .agentScopeMaxConcurrency(0)
                .ragEnabled(false)
                .changePackageEnabled(false)
                .skills(List.of())
                .capabilities(List.of("PROJECT_PRODUCTION_FULL"))
                .mcpIds(List.of())
                .executionTargetIds(List.of())
                .nodes(List.of(landingNode))
                .build();
    }

    public static String instruction() {
        return INSTRUCTION;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
