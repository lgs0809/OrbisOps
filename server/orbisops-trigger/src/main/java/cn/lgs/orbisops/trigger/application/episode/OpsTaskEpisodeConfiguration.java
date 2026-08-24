package cn.lgs.orbisops.trigger.application.episode;

import cn.lgs.orbisops.application.episode.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration
public class OpsTaskEpisodeConfiguration {
    @Bean
    public TaskAcceptanceApplicationService taskAcceptanceApplicationService(TaskAcceptancePort port, TaskAcceptanceDraftModelPort model) {
        return new TaskAcceptanceApplicationService(port, model);
    }
    @Bean(destroyMethod = "close")
    public TaskEpisodeApplicationService taskEpisodeApplicationService(TaskEpisodeStore store, TaskEpisodeModelPort model) {
        return new TaskEpisodeApplicationService(store, model, Clock.systemUTC());
    }
}
