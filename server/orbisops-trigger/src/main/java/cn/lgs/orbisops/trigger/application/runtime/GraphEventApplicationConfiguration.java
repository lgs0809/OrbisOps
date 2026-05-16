package cn.lgs.orbisops.trigger.application.runtime;

import cn.lgs.orbisops.application.runtime.graph.GraphEventApplicationService;
import cn.lgs.orbisops.domain.runtime.graph.adapter.repository.IGraphEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GraphEventApplicationConfiguration {

    @Bean
    public GraphEventApplicationService graphEventApplicationService(
            IGraphEventRepository repository,
            @Value("${orbisops.graph-events.max-memory-events:500}") int maxMemoryEvents) {
        return new GraphEventApplicationService(repository, maxMemoryEvents);
    }
}
