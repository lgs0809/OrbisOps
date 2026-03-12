package cn.lgs.orbisops.application.config;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** Application process manager for MCP client catalog mutations and protected queries. */
public final class McpClientCatalogUseCase {

    private final McpClientCatalogPort catalogPort;
    private final McpClientRuntimeCachePort runtimeCachePort;
    private final McpClientAuditPort auditPort;
    private final McpTransportConfigProtectionPort protectionPort;
    private final Clock clock;

    public McpClientCatalogUseCase(
            McpClientCatalogPort catalogPort,
            McpClientRuntimeCachePort runtimeCachePort,
            McpClientAuditPort auditPort,
            McpTransportConfigProtectionPort protectionPort) {
        this(catalogPort, runtimeCachePort, auditPort, protectionPort, Clock.systemDefaultZone());
    }

    public McpClientCatalogUseCase(
            McpClientCatalogPort catalogPort,
            McpClientRuntimeCachePort runtimeCachePort,
            McpClientAuditPort auditPort,
            McpTransportConfigProtectionPort protectionPort,
            Clock clock) {
        if (catalogPort == null) {
            throw new IllegalArgumentException("MCP_CLIENT_CATALOG_PORT_REQUIRED");
        }
        if (runtimeCachePort == null) {
            throw new IllegalArgumentException("MCP_CLIENT_RUNTIME_CACHE_PORT_REQUIRED");
        }
        if (auditPort == null) {
            throw new IllegalArgumentException("MCP_CLIENT_AUDIT_PORT_REQUIRED");
        }
        if (protectionPort == null) {
            throw new IllegalArgumentException("MCP_TRANSPORT_CONFIG_PROTECTION_PORT_REQUIRED");
        }
        if (clock == null) {
            throw new IllegalArgumentException("MCP_CLIENT_CLOCK_REQUIRED");
        }
        this.catalogPort = catalogPort;
        this.runtimeCachePort = runtimeCachePort;
        this.auditPort = auditPort;
        this.protectionPort = protectionPort;
        this.clock = clock;
    }

    public boolean create(McpClientCommand requested) {
        McpClientCommand command = requireCommand(requested);
        LocalDateTime now = LocalDateTime.now(clock);
        McpClientDefinition definition = definition(
                command,
                protectionPort.resolveIncoming(command.transportConfig(), null),
                now,
                now);
        boolean created = catalogPort.insert(definition);
        if (created) {
            runtimeCachePort.invalidateAll();
            auditPort.created(definition);
        }
        return created;
    }

    public boolean updateById(McpClientCommand requested) {
        McpClientCommand command = requireCommand(requested);
        McpClientDefinition before = catalogPort.findById(command.id());
        McpClientDefinition placeholderSource = before;
        if (placeholderSource == null && hasText(command.mcpId())) {
            placeholderSource = catalogPort.findByMcpId(command.mcpId());
        }
        McpClientDefinition after = definition(
                command,
                protectionPort.resolveIncoming(
                        command.transportConfig(),
                        placeholderSource == null ? null : placeholderSource.transportConfig()),
                before == null ? null : before.createTime(),
                LocalDateTime.now(clock));
        boolean updated = catalogPort.updateById(after);
        if (updated) {
            runtimeCachePort.invalidateAll();
            auditPort.updatedById(command.id(), before, after);
        }
        return updated;
    }

    public boolean updateByMcpId(McpClientCommand requested) {
        McpClientCommand command = requireCommand(requested);
        McpClientDefinition before = catalogPort.findByMcpId(command.mcpId());
        McpClientDefinition placeholderSource = command.id() == null
                ? before
                : catalogPort.findById(command.id());
        if (placeholderSource == null) {
            placeholderSource = before;
        }
        McpClientDefinition after = definition(
                command,
                protectionPort.resolveIncoming(
                        command.transportConfig(),
                        placeholderSource == null ? null : placeholderSource.transportConfig()),
                before == null ? null : before.createTime(),
                LocalDateTime.now(clock));
        boolean updated = catalogPort.updateByMcpId(after);
        if (updated) {
            runtimeCachePort.invalidateAll();
            auditPort.updatedByMcpId(command.mcpId(), before, after);
        }
        return updated;
    }

    public boolean deleteById(Long id) {
        McpClientDefinition before = catalogPort.findById(id);
        boolean deleted = catalogPort.deleteById(id);
        if (deleted) {
            runtimeCachePort.invalidateAll();
            auditPort.deletedById(id, before);
        }
        return deleted;
    }

    public boolean deleteByMcpId(String mcpId) {
        McpClientDefinition before = catalogPort.findByMcpId(mcpId);
        boolean deleted = catalogPort.deleteByMcpId(mcpId);
        if (deleted) {
            runtimeCachePort.invalidateAll();
            auditPort.deletedByMcpId(mcpId, before);
        }
        return deleted;
    }

    public McpClientDefinition findById(Long id) {
        return protect(catalogPort.findById(id));
    }

    public McpClientDefinition findByMcpId(String mcpId) {
        return protect(catalogPort.findByMcpId(mcpId));
    }

    public List<McpClientDefinition> listAll() {
        return protect(catalogPort.listAll());
    }

    public List<McpClientDefinition> listByStatus(Integer status) {
        return protect(catalogPort.listByStatus(status));
    }

    public List<McpClientDefinition> listByTransportType(String transportType) {
        return protect(catalogPort.listByTransportType(transportType));
    }

    public List<McpClientDefinition> listEnabled() {
        return protect(catalogPort.listEnabled());
    }

    public List<McpClientDefinition> query(McpClientCatalogQuery requested) {
        McpClientCatalogQuery query = requested == null ? McpClientCatalogQuery.all() : requested;
        List<McpClientDefinition> values;
        if (hasText(query.mcpId())) {
            McpClientDefinition single = catalogPort.findByMcpId(query.mcpId());
            values = single == null ? List.of() : List.of(single);
        } else if (query.status() != null) {
            values = immutable(catalogPort.listByStatus(query.status()));
        } else if (hasText(query.transportType())) {
            values = immutable(catalogPort.listByTransportType(query.transportType()));
        } else {
            values = immutable(catalogPort.listAll());
        }
        if (hasText(query.mcpName())) {
            values = values.stream()
                    .filter(value -> value.mcpName() != null && value.mcpName().contains(query.mcpName()))
                    .toList();
        }
        return protect(values);
    }

    private McpClientDefinition definition(
            McpClientCommand command,
            String transportConfig,
            LocalDateTime createTime,
            LocalDateTime updateTime) {
        return new McpClientDefinition(
                command.id(),
                command.mcpId(),
                command.mcpName(),
                command.transportType(),
                transportConfig,
                command.requestTimeout(),
                command.status(),
                createTime,
                updateTime);
    }

    private McpClientCommand requireCommand(McpClientCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("MCP_CLIENT_COMMAND_REQUIRED");
        }
        return command;
    }

    private McpClientDefinition protect(McpClientDefinition definition) {
        return definition == null
                ? null
                : definition.withProtectedTransportConfig(
                        protectionPort.protectForRead(definition.transportConfig()));
    }

    private List<McpClientDefinition> protect(List<McpClientDefinition> values) {
        return immutable(values).stream().map(this::protect).toList();
    }

    private List<McpClientDefinition> immutable(List<McpClientDefinition> values) {
        return values == null || values.isEmpty() ? List.of() : List.copyOf(values);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
