package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.application.mcp.McpToolCatalogStore.*;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import java.time.Clock;
import java.util.*;
import java.util.function.Supplier;

/** Full fetch/validation happens outside the short atomic publication transaction. */
public final class McpToolCatalogService {
    private final McpToolCatalogStore store;
    private final Clock clock;
    public McpToolCatalogService(McpToolCatalogStore store, Clock clock) { this.store=store; this.clock=clock; }

    public Snapshot current(Scope scope, Supplier<List<Map<String,Object>>> fetch) {
        return store.find(scope.identity()).filter(s -> s.generation()>0).orElseGet(() -> refresh(scope,fetch));
    }
    public Snapshot refresh(Scope scope, Supplier<List<Map<String,Object>>> fetch) {
        var started=clock.instant();
        long expected=store.find(scope.identity()).map(Snapshot::generation).orElse(0L);
        try {
            var tools=new TreeMap<String,Map<String,Object>>();
            for (var tool:Objects.requireNonNull(fetch.get(),"MCP_CATALOG_MISSING_LIST")) {
                Object name=tool.get("name");
                if (!(name instanceof String value) || value.isBlank() || tools.putIfAbsent(value,tool)!=null)
                    throw new IllegalArgumentException("MCP_CATALOG_INVALID_TOOL_IDENTITY");
                if (!(tool.get("inputSchema") instanceof Map<?,?> schema) || !"object".equals(schema.get("type")))
                    throw new IllegalArgumentException("MCP_CATALOG_INVALID_INPUT_SCHEMA");
            }
            String json=CanonicalJson.stringify(new ArrayList<>(tools.values()));
            if (json.length()>8_000_000) throw new IllegalArgumentException("MCP_CATALOG_TOO_LARGE");
            var published=store.publish(scope,expected,CanonicalObjectHasher.sha256Text(json),json,started);
            if (published.generation()<1) throw new IllegalStateException("MCP_CATALOG_NO_READY_GENERATION");
            return published;
        } catch (RuntimeException error) {
            // Never persist a remote exception message: it may contain an authorization header or URL.
            store.failed(scope,started,"MCP_CATALOG_REFRESH_FAILED");
            throw error;
        }
    }
    public List<Status> status(String projectId) { return store.status(projectId); }
    public List<Scope> scopesAfter(String cursor,int limit) { return store.scopesAfter(cursor,limit); }
}
