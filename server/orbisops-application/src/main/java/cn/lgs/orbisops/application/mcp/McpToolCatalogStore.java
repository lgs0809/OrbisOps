package cn.lgs.orbisops.application.mcp;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Durable remote definitions; never contains credentials or execution authority. */
public interface McpToolCatalogStore {
    record Scope(String identity, String projectId, String serverId) { }
    record Snapshot(Scope scope, long generation, String contentHash, String toolsJson,
                    Instant checkedAt, Instant succeededAt, String errorCode) { }
    record Status(String connectionId, String serverId, long generation, int toolCount,
                  Instant checkedAt, Instant succeededAt, String errorCode) { }
    default List<Status> status(String projectId) { return List.of(); }
    Optional<Snapshot> find(String identity);
    Snapshot publish(Scope scope, long expectedGeneration, String contentHash, String toolsJson, Instant startedAt);
    void failed(Scope scope, Instant startedAt, String errorCode);
    List<Scope> scopesAfter(String identity, int limit);
}
