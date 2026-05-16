package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.mcp.McpToolCatalogStore;
import cn.lgs.orbisops.application.mcp.McpToolCatalogStore.Scope;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OpsMcpCatalogRefreshWorkerTest {
    @Test void configurationHistoryDoesNotMultiplyDailyRefreshAndFailuresDoNotStopOtherProjects() {
        var store=mock(McpToolCatalogStore.class);
        var source=mock(OpsMcpRuntimeConfigSource.class);
        when(source.sourceId()).thenReturn("current-project-config");
        var remote=mock(OpsMcpRemoteClientAdapter.class);
        var unavailable=new Scope("01","project-a","unavailable");
        var old=new Scope("02","project-a","same-server");
        var current=new Scope("03","project-a","same-server");
        var otherProject=new Scope("04","project-b","same-server");
        var revoked=new Scope("05","project-a","revoked");
        // A configuration's history can straddle a keyset page boundary.
        when(store.scopesAfter("",100)).thenReturn(List.of(unavailable,old));
        when(store.scopesAfter("02",100)).thenReturn(List.of(current,otherProject,revoked));
        when(store.scopesAfter("05",100)).thenReturn(List.of());
        var failedConfig=OpsMcpServerConfig.builder().name("unavailable").build();
        var activeA=OpsMcpServerConfig.builder().name("current-a").build();
        var activeB=OpsMcpServerConfig.builder().name("current-b").build();
        when(source.resolve(any())).thenAnswer(call->{
            OpsMcpRuntimeConfigRequest request=call.getArgument(0);
            if(request.mcpId().equals("unavailable")) return OpsMcpRuntimeConfigSourceResult.match(failedConfig);
            if(request.mcpId().equals("revoked")) return OpsMcpRuntimeConfigSourceResult.blocked("REVOKED");
            return OpsMcpRuntimeConfigSourceResult.match(request.projectId().equals("project-a")?activeA:activeB);
        });
        doThrow(new IllegalStateException("synthetic transport failure")).when(remote).refreshCatalog(failedConfig);
        var worker=new OpsMcpCatalogRefreshWorker(store,new OpsMcpRuntimeConfigSourceChain(List.of(source),null),remote);
        worker.refresh();
        verify(remote).refreshCatalog(failedConfig);
        verify(remote).refreshCatalog(activeA);
        verify(remote).refreshCatalog(activeB);
        verifyNoMoreInteractions(remote);
        verify(store).failed(eq(unavailable),any(),eq("MCP_CATALOG_REFRESH_FAILED"));
        verify(store).failed(eq(revoked),any(),eq("MCP_CATALOG_SOURCE_UNAVAILABLE"));
        verify(source,times(4)).resolve(any());
    }
}
