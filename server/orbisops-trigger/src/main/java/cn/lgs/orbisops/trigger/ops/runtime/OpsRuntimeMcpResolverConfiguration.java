package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.project.ProjectMcpAuthorizationApplicationService;
import cn.lgs.orbisops.application.changepackage.LandingOperationJournalApplicationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpsRuntimeMcpResolverConfiguration {

    @Bean
    public OpsLegacyMcpConfigMapper opsLegacyMcpConfigMapper() {
        return new OpsLegacyMcpConfigMapper();
    }

    @Bean
    public OpsMcpRuntimeConfigSourceChain opsMcpRuntimeConfigSourceChain(
            List<OpsMcpRuntimeConfigSource> sources,
            OpsMcpRuntimeConfigResolutionTelemetry telemetry) {
        return new OpsMcpRuntimeConfigSourceChain(sources, telemetry);
    }

    @Bean
    public OpsRuntimeMcpResolver opsRuntimeMcpResolver(
            OpsMcpToolProvider toolProvider,
            OpsMcpRuntimeConfigSourceChain configSources,
            ObjectProvider<ProjectMcpAuthorizationApplicationService> authorizationProvider,
            OpsMcpRuntimeCatalogReconciler catalogReconciler,
            ObjectProvider<LandingOperationJournalApplicationService> landingJournalProvider) {
        return new OpsRuntimeMcpResolver(
                toolProvider,
                configSources,
                authorizationProvider::getIfAvailable,
                catalogReconciler,
                landingJournalProvider::getIfAvailable);
    }
}
