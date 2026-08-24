package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpClientRegistryArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void registryMustDelegateTypedCacheStateHandleLifecycleAndCleanupJob() throws IOException {
        String registry = read(RUNTIME + "OpsMcpClientRegistry.java");
        String settings = read(RUNTIME + "OpsMcpClientCacheSettings.java");
        String cacheState = read(RUNTIME + "OpsMcpClientCacheState.java");
        String handleService = read(RUNTIME + "OpsMcpClientHandleService.java");
        String configuration = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/application/ops/OpsMcpClientCacheConfiguration.java");
        String cleanupJob = read("orbisops-trigger/src/main/java/"
                + "cn/lgs/orbisops/trigger/application/ops/OpsMcpClientCacheCleanupJob.java");
        assertAll(
                () -> assertTrue(registry.contains("OpsMcpClientCacheState cacheState")),
                () -> assertTrue(registry.contains("OpsMcpClientHandleService handleService")),
                () -> assertTrue(registry.contains("ClientHandle acquire(")),
                () -> assertTrue(registry.contains("void initialize(ClientHandle")),
                () -> assertTrue(registry.contains("ToolCallback[] toolCallbacks(ClientHandle")),
                () -> assertTrue(registry.contains("void cleanupExpiredClients()")),
                () -> assertTrue(registry.contains("legacyConstructorDefaults()")),
                () -> assertFalse(registry.contains("@Value")),
                () -> assertFalse(registry.contains("@Scheduled")),
                () -> assertFalse(registry.contains("ConcurrentHashMap")),
                () -> assertFalse(registry.contains("SyncMcpToolCallbackProvider")),
                () -> assertFalse(registry.contains("client.close()")),
                () -> assertTrue(registry.lines().count() <= 150),
                () -> assertTrue(settings.contains("public record OpsMcpClientCacheSettings(")),
                () -> assertTrue(settings.contains("legacyConstructorDefaults()")),
                () -> assertTrue(cacheState.contains("Map<String, OpsMcpClientRegistry.ClientHandle> clients")),
                () -> assertTrue(cacheState.contains("new ConcurrentHashMap<>()")),
                () -> assertTrue(cacheState.contains("client.close()")),
                () -> assertTrue(cacheState.contains("int cleanupExpired()")),
                () -> assertFalse(cacheState.contains("@Service")),
                () -> assertTrue(handleService.contains("OpsMcpFullResultToolCallback")),
                () -> assertTrue(handleService.contains("void initialize(")),
                () -> assertFalse(handleService.contains("@Service")),
                () -> assertTrue(configuration.contains("orbisops.mcp.cache.ttl-seconds")),
                () -> assertTrue(configuration.contains("orbisops.mcp.cache.cleanup-interval-ms")),
                () -> assertTrue(cleanupJob.contains("@Scheduled")),
                () -> assertTrue(cleanupJob.contains("clientRegistry.cleanupExpiredClients()")));
    }

    @Test
    void remoteClientAdapterMustBeTheOnlyRegistryLifecycleConsumer() throws IOException {
        String adapter = read(RUNTIME + "OpsMcpRemoteClientAdapter.java");
        String provider = read(RUNTIME + "OpsMcpToolProvider.java");
        assertAll(
                () -> assertTrue(adapter.contains("private final OpsMcpClientRegistry clientRegistry;")),
                () -> assertTrue(adapter.contains("clientRegistry.acquire(")),
                () -> assertTrue(adapter.contains("clientRegistry.initialize(handle)")),
                () -> assertTrue(adapter.contains("clientRegistry.toolCallbacks(handle)")),
                () -> assertTrue(adapter.contains("clientRegistry.invalidateAll()")),
                () -> assertFalse(provider.contains("OpsMcpClientRegistry")),
                () -> assertFalse(provider.contains("ClientHandle")),
                () -> assertFalse(provider.contains("clientRegistry.acquire")),
                () -> assertFalse(provider.contains("clientRegistry.initialize")),
                () -> assertFalse(provider.contains("clientRegistry.toolCallbacks")),
                () -> assertFalse(provider.contains("clientRegistry.invalidateAll")),
                () -> assertFalse(provider.contains("Map<String, ClientHandle> clients")),
                () -> assertFalse(provider.contains("cacheTtlSeconds")),
                () -> assertFalse(provider.contains("@Scheduled")),
                () -> assertFalse(provider.contains("SyncMcpToolCallbackProvider")),
                () -> assertFalse(provider.contains("private void closeQuietly(")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-trigger"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-trigger"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-trigger"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
