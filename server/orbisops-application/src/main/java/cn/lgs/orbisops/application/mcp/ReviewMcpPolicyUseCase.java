package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.model.McpToolPolicy;

/** Narrow typed use case for MCP policy review mutations. */
public final class ReviewMcpPolicyUseCase {

    private final McpPolicyCommandPort commands;

    public ReviewMcpPolicyUseCase(McpPolicyCommandPort commands) {
        if (commands == null) throw new IllegalArgumentException("MCP_POLICY_COMMAND_PORT_REQUIRED");
        this.commands = commands;
    }

    public McpToolPolicy upsert(McpCommands.PolicyMutation command) {
        return commands.upsertToolPolicy(required(command));
    }

    public McpToolPolicy approve(McpCommands.PolicyMutation command) {
        return commands.approveToolPolicy(required(command));
    }

    public McpToolPolicy reject(McpCommands.PolicyMutation command) {
        return commands.rejectToolPolicy(required(command));
    }

    public McpToolPolicy disable(McpCommands.PolicyMutation command) {
        return commands.disableToolPolicy(required(command));
    }

    private McpCommands.PolicyMutation required(McpCommands.PolicyMutation command) {
        if (command == null) throw new IllegalArgumentException("MCP_POLICY_COMMAND_REQUIRED");
        return command;
    }
}
