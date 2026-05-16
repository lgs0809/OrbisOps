package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.runtime.workflow.adapter.repository.IPlatformRuntimeDefinitionSource;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Exact platform identity only; neither metadata nor a user-controlled label selects it. */
@Component
public final class OpsPlatformRuntimeDefinitionSource implements IPlatformRuntimeDefinitionSource {
    @Override
    public Optional<Map<String, Object>> find(String agentId, int version, String definitionHash) {
        var definition = new OpsPlatformLandingRuntimeDefinitionFactory().create("");
        if (!definition.getAgentId().equals(agentId) || definition.getVersion() != version
                || !definition.getDefinitionHash().equals(definitionHash)) return Optional.empty();
        // This ReAct runtime has no user graph START-node budget. Its WorkSession
        // deadline, iteration limit, approval and dispatch journal remain authoritative.
        return Optional.of(Map.of("agentId", agentId, "version", version,
                "definitionHash", definitionHash, "definitionKind", definition.getDefinitionKind()));
    }
}
