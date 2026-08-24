package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.mcp.*;
import cn.lgs.orbisops.application.mcp.McpToolCatalogStore.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpsMcpRemoteCatalogTest {
    static final class MemoryStore implements McpToolCatalogStore {
        Snapshot value;
        public Optional<Snapshot> find(String id) { return Optional.ofNullable(value); }
        public Snapshot publish(Scope scope,long expected,String hash,String json,Instant at) {
            long v=value==null?1:value.generation()+(hash.equals(value.contentHash())?0:1);
            return value=new Snapshot(scope,v,hash,json,at,at,"");
        }
        public void failed(Scope scope,Instant started,String error) { }
        public List<Scope> scopesAfter(String cursor,int limit) { return List.of(); }
    }
    ToolCallback[] tools(OpsMcpClientRegistry.ClientHandle handle,String description,String propertyType) {
        var map=Map.of("name","query","description",description,"inputSchema",Map.of("type","object","properties",Map.of("value",Map.of("type",propertyType))));
        return new ToolCallback[]{new OpsMcpFullResultToolCallback(handle,new ObjectMapper().convertValue(map,McpSchema.Tool.class))};
    }
    @Test void catalogIdentityIgnoresRunAndLocalFiltersButSeparatesCredentials() {
        var factory=new OpsMcpClientFactory(new OpsSecretResolver(new org.springframework.core.env.StandardEnvironment()),
            new OpsMcpTransportSecurityPolicy(OpsMcpTransportSecuritySettings.defaults()));
        var first=OpsMcpServerConfig.builder().projectId("p").mcpId("m").transport("streamable-http")
            .url("http://127.0.0.1:8000/mcp").headers(Map.of("Authorization","Bearer synthetic-a")).build();
        var next=first.toBuilder().runId("different-run").command("").allowedTools(List.of("restricted"))
            .runtimeAuthority("OBSERVE_ONLY").toolCallStage("INVESTIGATE").build();
        assertEquals(factory.catalogIdentity(first),factory.catalogIdentity(next));
        next.setHeaders(Map.of("Authorization","Bearer synthetic-b"));assertNotEquals(factory.catalogIdentity(first),factory.catalogIdentity(next));
        next.setHeaders(first.getHeaders());next.setProjectId("other");assertNotEquals(factory.catalogIdentity(first),factory.catalogIdentity(next));
    }

    @Test void reconnectReusesDefinitionsAndSchemaChangeRejectsOldCallbackBeforeDispatch() {
        var store=new MemoryStore();var factory=mock(OpsMcpClientFactory.class);var config=new OpsMcpServerConfig();
        config.setProjectId("project");config.setMcpId("server");when(factory.catalogIdentity(config)).thenReturn("identity");
        var catalog=new OpsMcpRemoteCatalog(new McpToolCatalogService(store,Clock.systemUTC()),store,factory);
        var oldClient=mock(McpSyncClient.class);var newClient=mock(McpSyncClient.class);
        var oldHandle=new OpsMcpClientRegistry.ClientHandle(oldClient,config,0);
        var newHandle=new OpsMcpClientRegistry.ClientHandle(newClient,config,0);
        var fetches=new AtomicInteger();
        var first=catalog.callbacks(config,oldHandle,()->{fetches.incrementAndGet();return tools(oldHandle,"old","string");},false);
        var reconnect=catalog.callbacks(config,newHandle,()->{fetches.incrementAndGet();return tools(newHandle,"wrong","number");},false);
        assertEquals(1,fetches.get());assertEquals(first[0].getToolDefinition(),reconnect[0].getToolDefinition());
        catalog.callbacks(config,newHandle,()->tools(newHandle,"new","integer"),true);
        assertEquals(2,store.value.generation());
        var current=catalog.currentDefinition(config,"query");
        assertEquals("new",current.get("description"));
        assertEquals(Map.of("type","integer"),((Map<?,?>)((Map<?,?>)current.get("inputSchema")).get("properties")).get("value"));
        assertTrue(catalog.currentDefinition(config,"deleted").isEmpty());
        assertEquals(1,fetches.get());
        assertThrows(SecurityException.class,()->first[0].call("{\"value\":\"stale\"}"));
        assertThrows(SecurityException.class,()->reconnect[0].call("{\"value\":\"stale\"}"));
        verifyNoInteractions(oldClient,newClient);
    }
    @Test void deletionRejectsPreviouslyLoadedCallbackWithoutReplayingAWrite() {
        var store=new MemoryStore();var factory=mock(OpsMcpClientFactory.class);var config=new OpsMcpServerConfig();
        when(factory.catalogIdentity(config)).thenReturn("identity");
        var catalog=new OpsMcpRemoteCatalog(new McpToolCatalogService(store,Clock.systemUTC()),store,factory);
        var client=mock(McpSyncClient.class);var handle=new OpsMcpClientRegistry.ClientHandle(client,config,0);
        var loaded=catalog.callbacks(config,handle,()->tools(handle,"old","string"),false);
        assertEquals(0,catalog.callbacks(config,handle,()->new ToolCallback[0],true).length);
        assertThrows(SecurityException.class,()->loaded[0].call("{}"));verifyNoInteractions(client);
    }
    @Test void duplicateNamesNeverPublishPartialCatalog() {
        var store=new MemoryStore();var factory=mock(OpsMcpClientFactory.class);var config=new OpsMcpServerConfig();
        when(factory.catalogIdentity(config)).thenReturn("identity");
        var catalog=new OpsMcpRemoteCatalog(new McpToolCatalogService(store,Clock.systemUTC()),store,factory);
        var handle=new OpsMcpClientRegistry.ClientHandle(mock(McpSyncClient.class),config,0);
        var callbacks=tools(handle,"valid","string");catalog.callbacks(config,handle,()->callbacks,false);
        String old=store.value.contentHash();
        assertThrows(IllegalArgumentException.class,()->catalog.callbacks(config,handle,()->new ToolCallback[]{callbacks[0],callbacks[0]},true));
        assertEquals(old,store.value.contentHash());
    }
}
