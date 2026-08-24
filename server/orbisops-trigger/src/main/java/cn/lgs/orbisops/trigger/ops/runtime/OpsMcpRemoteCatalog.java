package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.mcp.*;
import cn.lgs.orbisops.application.mcp.McpToolCatalogStore.*;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.tool.ToolCallback;
import java.util.*;
import java.util.function.Supplier;

/** Projects immutable remote definitions onto the current connection; never caches live callbacks. */
public final class OpsMcpRemoteCatalog {
    private final McpToolCatalogService catalogs;
    private final McpToolCatalogStore store;
    private final OpsMcpClientFactory factory;
    private final ObjectMapper json=new ObjectMapper();
    public OpsMcpRemoteCatalog(McpToolCatalogService catalogs,McpToolCatalogStore store,OpsMcpClientFactory factory) {
        this.catalogs=catalogs;this.store=store;this.factory=factory;
    }
    Scope scope(OpsMcpServerConfig config) {
        return new Scope(factory.catalogIdentity(config),text(config.getProjectId()),
            text(config.getMcpId()).isBlank()?text(config.getName()):config.getMcpId());
    }
    /** Local-only read for the next model request; never opens a transport or refreshes the peer. */
    Map<String,Object> currentDefinition(OpsMcpServerConfig config,String name) {
        return currentDefinitions(config).stream().filter(tool -> name.equals(tool.get("name"))).findFirst().orElse(Map.of());
    }
    List<Map<String,Object>> currentDefinitions(OpsMcpServerConfig config) {
        return store.find(scope(config).identity()).map(snapshot -> CanonicalJson.parseArray(snapshot.toolsJson()).stream()
                .map(raw -> json.convertValue(raw,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>() {}))
                .toList()).orElse(List.of());
    }

    ToolCallback[] callbacks(OpsMcpServerConfig config,OpsMcpClientRegistry.ClientHandle handle,Supplier<ToolCallback[]> fetch,boolean refresh) {
        Scope scope=scope(config);
        Supplier<List<Map<String,Object>>> read=()->Arrays.stream(fetch.get()).map(callback->{
            if (!(callback instanceof OpsMcpFullResultToolCallback full)) throw new IllegalArgumentException("MCP_CATALOG_FULL_DEFINITION_REQUIRED");
            Map<String,Object> definition=full.definitionSnapshot();
            OpsMcpCallResultNormalizer.rejectRemoteReferences(definition);
            // Validate every callback before publication. A partial or malformed page is never a new generation.
            new OpsMcpFullResultToolCallback(handle,json.convertValue(definition,McpSchema.Tool.class));
            return definition;
        }).toList();
        var snapshot=refresh?catalogs.refresh(scope,read):catalogs.current(scope,read);
        return CanonicalJson.parseArray(snapshot.toolsJson()).stream().map(raw->{
            var tool=json.convertValue(raw,McpSchema.Tool.class);
            String hash=CanonicalObjectHasher.sha256(raw);
            return new OpsMcpFullResultToolCallback(handle,tool,()->assertCurrent(scope.identity(),tool.name(),hash));
        }).toArray(ToolCallback[]::new);
    }
    private void assertCurrent(String identity,String name,String expectedHash) {
        var current=store.find(identity).orElseThrow(()->new SecurityException("MCP_CATALOG_UNAVAILABLE"));
        boolean matches=CanonicalJson.parseArray(current.toolsJson()).stream()
            .filter(raw->raw instanceof Map<?,?> map && name.equals(map.get("name")))
            .anyMatch(raw->expectedHash.equals(CanonicalObjectHasher.sha256(raw)));
        if (!matches) throw new SecurityException("MCP_POLICY_STALE:LOCAL_TOOL_DEFINITION_CHANGED_REPLAN_REQUIRED");
    }
    private static String text(String value) { return value==null?"":value; }
}
