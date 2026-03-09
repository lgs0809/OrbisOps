package cn.lgs.orbisops.trigger.application.agenteval;

import cn.lgs.orbisops.application.agenteval.AgentEvalApplicationService;
import cn.lgs.orbisops.application.agenteval.AgentEvalAuditPort;
import cn.lgs.orbisops.application.agenteval.AgentEvalDefinitionPort;
import cn.lgs.orbisops.application.agenteval.AgentEvalIdentityFactory;
import cn.lgs.orbisops.domain.agenteval.adapter.repository.IAgentEvalRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.UUID;

@Configuration
public class AgentEvalApplicationConfiguration {

    @Bean
    public AgentEvalApplicationService agentEvalApplicationService(
            IAgentEvalRepository repository,
            AgentEvalDefinitionPort definitions,
            AgentEvalAuditPort audit) {
        AgentEvalIdentityFactory identities = new AgentEvalIdentityFactory(
                () -> UUID.randomUUID().toString(),
                Clock.systemUTC());
        return new AgentEvalApplicationService(repository, definitions, audit, identities);
    }
}
