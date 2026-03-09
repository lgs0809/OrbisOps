package cn.lgs.orbisops.trigger.application.agentdefinition;

import cn.lgs.orbisops.application.agentdefinition.AgentCapabilityBindingUseCase;
import cn.lgs.orbisops.domain.agentdefinition.adapter.repository.IAgentCapabilityBindingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/** Trigger compatibility adapter for legacy capability binding query responses. */
@Slf4j
public final class OpsAgentCapabilityBindingQueryAdapter {

    private final AgentCapabilityBindingUseCase useCase;
    private final OpsAgentCapabilityBindingMapper mapper;
    private volatile boolean storeUnavailableLogged;

    public OpsAgentCapabilityBindingQueryAdapter(
            IAgentCapabilityBindingRepository capabilityRepository) {
        this(new AgentCapabilityBindingUseCase(capabilityRepository),
                new OpsAgentCapabilityBindingMapper());
    }

    OpsAgentCapabilityBindingQueryAdapter(AgentCapabilityBindingUseCase useCase,
                                          OpsAgentCapabilityBindingMapper mapper) {
        if (useCase == null) {
            throw new IllegalArgumentException("AGENT_CAPABILITY_BINDING_USE_CASE_REQUIRED");
        }
        if (mapper == null) {
            throw new IllegalArgumentException("AGENT_CAPABILITY_BINDING_MAPPER_REQUIRED");
        }
        this.useCase = useCase;
        this.mapper = mapper;
    }

    public List<Map<String, Object>> list(String agentId) {
        if (!StringUtils.hasText(agentId)) {
            return List.of();
        }
        try {
            return mapper.views(useCase.latest(agentId.trim()));
        } catch (DataAccessException error) {
            logStoreFallback(error);
            return List.of();
        }
    }

    private void logStoreFallback(Exception error) {
        if (!storeUnavailableLogged) {
            storeUnavailableLogged = true;
            log.warn("运维 Agent Capability Binding MySQL 存储不可用，返回空绑定：{}",
                    error.getMessage());
        }
    }
}
