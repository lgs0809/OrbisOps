package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.model.McpToolPolicy;

/** Typed command boundary for governed MCP tool-policy mutations. */
public interface McpPolicyCommandPort {

    McpToolPolicy upsertToolPolicy(McpCommands.PolicyMutation command);

    McpToolPolicy approveToolPolicy(McpCommands.PolicyMutation command);

    McpToolPolicy rejectToolPolicy(McpCommands.PolicyMutation command);

    McpToolPolicy disableToolPolicy(McpCommands.PolicyMutation command);
}
