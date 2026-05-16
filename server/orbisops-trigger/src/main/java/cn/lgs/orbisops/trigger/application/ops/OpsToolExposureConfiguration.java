package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.domain.runtime.tool.model.ToolExposureSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsToolExposureConfiguration {

    @Bean
    public ToolExposureSettings opsToolExposureSettings(
            @Value("${orbisops.agent.action-mode:" + ToolExposureSettings.DEFAULT_ACTION_MODE + "}")
            String actionMode,
            @Value("${orbisops.agent.enforce-read-only-tools:true}") boolean enforceReadOnlyTools) {
        return ToolExposureSettings.fromRaw(actionMode, enforceReadOnlyTools);
    }
}
