package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.application.agent.OpsMainAgentActionHandler;
import cn.lgs.orbisops.application.agent.OpsMainAgentCoordinator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpsMainAgentApplicationConfiguration {

    @Bean
    public OpsMainAgentCoordinator opsMainAgentCoordinator(List<OpsMainAgentActionHandler> handlers) {
        return new OpsMainAgentCoordinator(handlers);
    }
}
