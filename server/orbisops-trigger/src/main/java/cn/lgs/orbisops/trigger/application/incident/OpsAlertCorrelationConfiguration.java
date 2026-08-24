package cn.lgs.orbisops.trigger.application.incident;

import cn.lgs.orbisops.application.incident.AlertCorrelationApplicationService;
import cn.lgs.orbisops.application.incident.AlertCorrelationStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsAlertCorrelationConfiguration {
    @Bean
    public AlertCorrelationApplicationService alertCorrelationApplicationService(AlertCorrelationStore store) {
        return new AlertCorrelationApplicationService(store);
    }
}
