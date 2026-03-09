package cn.lgs.orbisops.trigger.application.toolset;

import cn.lgs.orbisops.application.toolset.CustomToolsetStoreApplicationService;
import cn.lgs.orbisops.application.toolset.CustomToolsetStorePort;
import cn.lgs.orbisops.application.toolset.LocalHostApplicationService;
import cn.lgs.orbisops.application.toolset.LocalHostCommandPort;
import cn.lgs.orbisops.application.toolset.LocalLogFilePort;
import cn.lgs.orbisops.application.toolset.LocalMySqlApplicationService;
import cn.lgs.orbisops.application.toolset.LocalMySqlExecutionPort;
import cn.lgs.orbisops.application.toolset.LocalRedisApplicationService;
import cn.lgs.orbisops.application.toolset.LocalRedisExecutionPort;
import cn.lgs.orbisops.application.toolset.ToolExecutionPort;
import cn.lgs.orbisops.application.toolset.ToolsetApplicationService;
import cn.lgs.orbisops.application.toolset.ToolsetAuditPort;
import cn.lgs.orbisops.application.toolset.ToolsetCatalogPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsToolsetApplicationConfiguration {

    @Bean
    public CustomToolsetStoreApplicationService customToolsetStoreApplicationService(
            CustomToolsetStorePort storePort) {
        return new CustomToolsetStoreApplicationService(storePort);
    }

    @Bean
    public LocalHostApplicationService localHostApplicationService(
            LocalHostCommandPort commandPort,
            LocalLogFilePort logFilePort) {
        return new LocalHostApplicationService(commandPort, logFilePort);
    }

    @Bean
    public LocalMySqlApplicationService localMySqlApplicationService(
            LocalMySqlExecutionPort executionPort) {
        return new LocalMySqlApplicationService(executionPort);
    }

    @Bean
    public LocalRedisApplicationService localRedisApplicationService(
            LocalRedisExecutionPort executionPort) {
        return new LocalRedisApplicationService(executionPort);
    }

    @Bean
    public ToolsetApplicationService toolsetApplicationService(
            ToolsetCatalogPort catalogPort,
            ToolExecutionPort executionPort,
            ToolsetAuditPort auditPort) {
        return new ToolsetApplicationService(catalogPort, executionPort, auditPort);
    }
}
