package cn.lgs.orbisops.domain.agentdefinition.service;

import cn.lgs.orbisops.domain.agentdefinition.model.AgentMcpServerDefinition;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Domain invariants for inline MCP transport and tool capability declarations. */
public final class AgentMcpServerDefinitionPolicy {

    private static final Set<String> TRANSPORTS = Set.of(
            "stdio", "sse", "streamable-http");
    private static final Set<String> TOOL_CAPABILITIES = Set.of(
            "read_only", "readonly", "read", "query", "search", "list", "get",
            "evidence", "observe", "inspect", "notification", "notify", "notice",
            "message", "push_report", "mutating", "write", "execute", "exec", "run",
            "dangerous", "recovery", "admin", "operation", "blocked");

    private final AgentToolNamePolicy toolNamePolicy;

    public AgentMcpServerDefinitionPolicy(AgentToolNamePolicy toolNamePolicy) {
        if (toolNamePolicy == null) {
            throw new IllegalArgumentException("AGENT_TOOL_NAME_POLICY_REQUIRED");
        }
        this.toolNamePolicy = toolNamePolicy;
    }

    public void validate(AgentMcpServerDefinition definition, String owner) {
        List<AgentMcpServerDefinition.Server> servers = definition == null
                ? List.of()
                : definition.servers();
        for (AgentMcpServerDefinition.Server server : servers) {
            validate(server, owner);
        }
    }

    private void validate(AgentMcpServerDefinition.Server server, String owner) {
        if (server == null || !hasText(server.name())) {
            throw new IllegalArgumentException(owner + " 存在缺少 name 的 MCP 配置");
        }
        String transport = hasText(server.transport())
                ? server.transport().trim().toLowerCase(Locale.ROOT)
                : "stdio";
        if (!TRANSPORTS.contains(transport)) {
            throw new IllegalArgumentException(
                    owner + " MCP " + server.name()
                            + " transport 不支持：" + server.transport());
        }
        if ("stdio".equals(transport) && !hasText(server.command())) {
            throw new IllegalArgumentException(
                    owner + " MCP " + server.name() + " 缺少 command");
        }
        if (!"stdio".equals(transport) && !hasText(server.url())) {
            throw new IllegalArgumentException(
                    owner + " MCP " + server.name() + " 缺少 url");
        }
        toolNamePolicy.validateAll(
                server.allowedTools(),
                owner + " MCP " + server.name() + " allowedTools");
        toolNamePolicy.validateAll(
                server.notificationTools(),
                owner + " MCP " + server.name() + " notificationTools");
        toolNamePolicy.validateAll(
                server.blockedTools(),
                owner + " MCP " + server.name() + " blockedTools");
        for (var entry : server.toolCapabilities().entrySet()) {
            toolNamePolicy.validate(
                    entry.getKey(),
                    owner + " MCP " + server.name() + " toolCapabilities");
            String capability = normalizeCapability(entry.getValue());
            if (!TOOL_CAPABILITIES.contains(capability)) {
                throw new IllegalArgumentException(
                        owner + " MCP " + server.name()
                                + " tool capability 不支持：" + entry.getValue());
            }
        }
    }

    private String normalizeCapability(String capability) {
        return hasText(capability)
                ? capability.trim().toLowerCase(Locale.ROOT).replace("-", "_")
                : "";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
