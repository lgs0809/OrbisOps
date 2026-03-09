package cn.lgs.orbisops.trigger.application.agent;

import cn.lgs.orbisops.domain.agent.adapter.repository.IAgentRepository;
import cn.lgs.orbisops.domain.agent.service.ITaskService;
import cn.lgs.orbisops.domain.agent.service.task.AiAgentTaskService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpsAgentTaskDomainConfiguration {

    @Bean
    public ITaskService agentTaskService(IAgentRepository repository) {
        return new AiAgentTaskService(repository);
    }
}
