package cn.lgs.orbisops.trigger.application.runtime;

import cn.lgs.orbisops.application.runtime.taskcontext.TaskContextAuditPort;
import cn.lgs.orbisops.application.runtime.taskcontext.TaskContextCommandApplicationService;
import cn.lgs.orbisops.application.runtime.taskcontext.TaskContextQueryApplicationService;
import cn.lgs.orbisops.domain.runtime.taskcontext.adapter.repository.ITaskContextRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TaskContextApplicationConfiguration {

    @Bean
    public TaskContextCommandApplicationService taskContextCommandApplicationService(
            ITaskContextRepository repository,
            TaskContextAuditPort audit) {
        return new TaskContextCommandApplicationService(repository, audit);
    }

    @Bean
    public TaskContextQueryApplicationService taskContextQueryApplicationService(
            ITaskContextRepository repository) {
        return new TaskContextQueryApplicationService(repository);
    }
}
