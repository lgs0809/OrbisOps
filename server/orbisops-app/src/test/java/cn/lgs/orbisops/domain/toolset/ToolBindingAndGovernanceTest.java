package cn.lgs.orbisops.domain.toolset;

import cn.lgs.orbisops.domain.toolset.model.ApprovalRequirement;
import cn.lgs.orbisops.domain.toolset.model.CompensationCapability;
import cn.lgs.orbisops.domain.toolset.model.IdempotencyCapability;
import cn.lgs.orbisops.domain.toolset.model.InternalToolBinding;
import cn.lgs.orbisops.domain.toolset.model.McpToolBinding;
import cn.lgs.orbisops.domain.toolset.model.ReconciliationCapability;
import cn.lgs.orbisops.domain.toolset.model.ToolBinding;
import cn.lgs.orbisops.domain.toolset.model.ToolEffect;
import cn.lgs.orbisops.domain.toolset.model.ToolGovernance;
import cn.lgs.orbisops.domain.toolset.model.ToolProviderDescriptor;
import cn.lgs.orbisops.domain.toolset.model.ToolProviderType;
import cn.lgs.orbisops.domain.toolset.model.ToolRiskLevel;
import cn.lgs.orbisops.domain.toolset.model.ToolSemantics;
import cn.lgs.orbisops.domain.toolset.model.ToolTrustLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ToolBindingAndGovernanceTest {

    @Test
    void providerProtocolDoesNotDetermineApprovalOrEffect() {
        ToolProviderDescriptor mcp = new ToolProviderDescriptor(
                ToolProviderType.MCP, "metrics", "MCP", "", "metrics", "query", "{}", "{}");
        ToolGovernance read = ToolGovernance.from(mcp, ToolSemantics.readOnlyTool());
        ToolGovernance write = ToolGovernance.from(mcp, ToolSemantics.targetResourceWrite());

        assertEquals(ToolEffect.READ_ONLY, read.effect());
        assertEquals(ApprovalRequirement.NONE, read.approvalRequirement());
        assertEquals(ToolEffect.SIDE_EFFECTING, write.effect());
        assertEquals(ApprovalRequirement.OPERATOR_APPROVAL, write.approvalRequirement());
        assertEquals(ToolRiskLevel.HIGH, write.risk());
        assertEquals(CompensationCapability.MANUAL, write.compensationCapability());
    }

    @Test
    void legacyProviderFactsProjectToCanonicalBindings() {
        ToolBinding local = ToolBinding.compatibility(ToolProviderDescriptor.local("LOCAL_MYSQL"));
        ToolBinding mcp = ToolBinding.compatibility(new ToolProviderDescriptor(
                ToolProviderType.MCP, "ops", "MCP", "", "ops-server", "restart_service", "{}", "{}"));

        assertInstanceOf(InternalToolBinding.class, local);
        McpToolBinding typed = assertInstanceOf(McpToolBinding.class, mcp);
        assertEquals("ops-server", typed.mcpServerId());
        assertEquals("restart_service", typed.remoteToolName());
    }

    @Test
    void reconciliationRequiresIdempotency() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new ToolGovernance(
                        ToolEffect.SIDE_EFFECTING,
                        ToolRiskLevel.HIGH,
                        ApprovalRequirement.OPERATOR_APPROVAL,
                        IdempotencyCapability.NONE,
                        ReconciliationCapability.QUERY_BY_EXECUTION_KEY,
                        CompensationCapability.MANUAL,
                        ToolTrustLevel.PLATFORM_CONFIGURED));

        assertEquals("TOOL_RECONCILIATION_REQUIRES_IDEMPOTENCY", error.getMessage());
    }
}
