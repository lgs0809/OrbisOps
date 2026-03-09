package cn.lgs.orbisops.domain.agentdefinition.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Protocol-neutral inline MCP declarations embedded in an Agent definition. */
public record AgentMcpServerDefinition(List<Server> servers) {

    public AgentMcpServerDefinition {
        servers = servers == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(servers));
    }

    public record Server(
            String name,
            String transport,
            String command,
            String url,
            List<String> allowedTools,
            List<String> notificationTools,
            List<String> blockedTools,
            Map<String, String> toolCapabilities) {

        public Server {
            name = text(name);
            transport = text(transport);
            command = text(command);
            url = text(url);
            allowedTools = immutable(allowedTools);
            notificationTools = immutable(notificationTools);
            blockedTools = immutable(blockedTools);
            toolCapabilities = toolCapabilities == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(toolCapabilities));
        }
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(values));
    }
}
