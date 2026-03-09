package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.worksession.runtime.model.AgentRunExecutionContext;

import java.util.Map;

/**
 * Product-level execution boundary. Runtime graph phases are implementation details and
 * must not replace this pre/post approval split.
 */
public enum OpsExecutionHarness {
    BUILTIN_ASSISTANT,
    PROJECT_PRE_APPROVAL,
    APPROVED_LANDING;

    public int version() {
        return 1;
    }

    public String harnessHash() {
        return OpsRuntimeHashing.canonicalHash(Map.of(
                "type", name(),
                "version", version(),
                "productBoundary", this == APPROVED_LANDING
                        ? "APPROVED_SNAPSHOT_ONLY"
                        : "NO_TARGET_RESOURCE_WRITE"));
    }

    public static OpsExecutionHarness forRequest(OpsAgentChatRequest request) {
        AgentRunExecutionContext authority = new OpsAgentRunExecutionContextFactory().resolve(request);
        if (authority == null) {
            throw new SecurityException("EXECUTION_HARNESS_AUTHORITY_REQUIRED");
        }
        return authority.stage() == cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStage.LANDING
                ? APPROVED_LANDING
                : PROJECT_PRE_APPROVAL;
    }
}
