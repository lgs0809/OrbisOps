package cn.lgs.orbisops.trigger.application.worksession;

import cn.lgs.orbisops.application.worksession.run.WorkSessionRunApplicationService;
import cn.lgs.orbisops.application.worksession.run.WorkSessionRunIdentityPort;
import cn.lgs.orbisops.domain.worksession.run.adapter.repository.IWorkSessionRunRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class WorkSessionRunApplicationConfiguration {

    @Bean
    public WorkSessionRunApplicationService workSessionRunApplicationService(
            IWorkSessionRunRepository repository,
            WorkSessionRunIdentityPort identity,
            @Value("${orbisops.work-session.lease-seconds:90}") long leaseSeconds) {
        return new WorkSessionRunApplicationService(
                repository,
                identity,
                Duration.ofSeconds(Math.max(30L, leaseSeconds)));
    }
}
