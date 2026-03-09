package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.model.McpToolPolicy;
import cn.lgs.orbisops.trigger.application.mcp.OpsMcpToolPolicyCommandMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpGovernanceApplicationServiceTest {

    @Test
    void queryOperationsUseDedicatedPolicyAndHistoryQueries() {
        McpSummaryPort summaries = mock(McpSummaryPort.class);
        McpPolicyCommandPort commands = mock(McpPolicyCommandPort.class);
        McpRuntimeHistoryQueryService history = mock(McpRuntimeHistoryQueryService.class);
        McpToolSnapshotQueryService snapshots = mock(McpToolSnapshotQueryService.class);
        McpPolicyQueryService policies = mock(McpPolicyQueryService.class);
        McpGovernanceApplicationService service = new McpGovernanceApplicationService(
                summaries, commands, history, snapshots, policies);
        when(policies.policies("project-1", 20))
                .thenReturn(List.of(Map.of("policyId", "policy-1")));
        when(history.calls("project-1", 20))
                .thenReturn(List.of(Map.of("callId", "call-1")));

        assertEquals("policy-1", service.toolPolicies("project-1", 20).get(0).get("policyId"));
        assertEquals("call-1", service.mcpCalls("project-1", 20).get(0).get("callId"));

        verify(policies).policies("project-1", 20);
        verify(history).calls("project-1", 20);
    }

    @Test
    void summaryAndPolicyCommandsUseTheirDedicatedPorts() {
        McpSummaryPort summaries = mock(McpSummaryPort.class);
        McpPolicyCommandPort commands = mock(McpPolicyCommandPort.class);
        McpRuntimeHistoryQueryService history = mock(McpRuntimeHistoryQueryService.class);
        McpToolSnapshotQueryService snapshots = mock(McpToolSnapshotQueryService.class);
        McpPolicyQueryService policies = mock(McpPolicyQueryService.class);
        McpGovernanceApplicationService service = new McpGovernanceApplicationService(
                summaries, commands, history, snapshots, policies);
        McpCommands.PolicyMutation command = new OpsMcpToolPolicyCommandMapper().mutation(
                "project-1", "policy-1", "admin", Map.of("riskLevel", "LOW"));
        McpToolPolicy approved = mock(McpToolPolicy.class);
        Map<String, Object> approvedView = Map.of("status", "ACTIVE");
        when(summaries.summary("project-1")).thenReturn(Map.of("status", "READY"));
        when(commands.approveToolPolicy(command)).thenReturn(approved);
        when(policies.view(approved)).thenReturn(approvedView);

        assertEquals("READY", service.summary("project-1").get("status"));
        assertEquals("ACTIVE", service.approveToolPolicy(command).get("status"));
        verify(summaries).summary("project-1");
        verify(commands).approveToolPolicy(command);
        verify(policies).view(approved);
    }
}
