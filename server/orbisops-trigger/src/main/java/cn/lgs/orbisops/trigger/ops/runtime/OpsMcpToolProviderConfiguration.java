package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.mcp.ProgressiveMcpProcessManager;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsMcpToolProviderConfiguration {

    @Bean
    public OpsProgressiveMcpCallbackAdapter opsProgressiveMcpCallbackAdapter(
            ObjectProvider<OpsToolExecutionService> toolExecutionServiceProvider) {
        return new OpsProgressiveMcpCallbackAdapter(toolExecutionServiceProvider::getIfAvailable);
    }

    @Bean
    public OpsMcpRemoteClientAdapter opsMcpRemoteClientAdapter(
            OpsMcpClientRegistry clientRegistry,
            OpsMcpClientFactory clientFactory,
            cn.lgs.orbisops.application.mcp.McpToolCatalogStore catalogs) {
        var service=new cn.lgs.orbisops.application.mcp.McpToolCatalogService(catalogs,java.time.Clock.systemUTC());
        return new OpsMcpRemoteClientAdapter(clientRegistry, clientFactory,new OpsMcpRemoteCatalog(service,catalogs,clientFactory));
    }

    @Bean
    public OpsMcpRemoteInvocationAdapter opsMcpRemoteInvocationAdapter(
            OpsMcpRemoteClientAdapter remoteClientAdapter,
            ObjectProvider<ProgressiveMcpProcessManager> progressiveMcpProcessManagerProvider,
            OpsMcpInvocationResiliencePolicy resilience) {
        return new OpsMcpRemoteInvocationAdapter(
                remoteClientAdapter,
                progressiveMcpProcessManagerProvider::getIfAvailable,
                resilience);
    }

    @Bean
    public OpsMcpProgressiveRuntimeAdapter opsMcpProgressiveRuntimeAdapter(
            ObjectProvider<ProgressiveMcpProcessManager> progressiveMcpProcessManagerProvider,
            OpsMcpProgressiveSettings settings,
            OpsMcpRemoteCallPolicy remoteCallPolicy) {
        return new OpsMcpProgressiveRuntimeAdapter(
                progressiveMcpProcessManagerProvider::getIfAvailable,
                settings,
                remoteCallPolicy);
    }
}
