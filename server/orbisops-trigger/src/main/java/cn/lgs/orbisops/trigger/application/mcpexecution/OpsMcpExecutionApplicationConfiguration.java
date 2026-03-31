package cn.lgs.orbisops.trigger.application.mcpexecution;

import cn.lgs.orbisops.application.mcpexecution.McpExecutionApplicationService;
import cn.lgs.orbisops.application.mcpexecution.McpExecutionAuditPort;
import cn.lgs.orbisops.application.mcpexecution.McpExecutionRecordPort;
import cn.lgs.orbisops.application.mcpexecution.McpExecutionRemotePort;
import cn.lgs.orbisops.application.mcpexecution.McpExecutionRouterPort;
import cn.lgs.orbisops.application.mcpexecution.McpExecutionRuntimePort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsMcpExecutionApplicationConfiguration {

    @Bean
    public McpExecutionApplicationService mcpExecutionApplicationService(
            McpExecutionRuntimePort runtime,
            McpExecutionRemotePort remote,
            McpExecutionRouterPort router,
            McpExecutionRecordPort records,
            McpExecutionAuditPort audits) {
        return new McpExecutionApplicationService(
                runtime, remote, router, records, audits, System::nanoTime);
    }
}
