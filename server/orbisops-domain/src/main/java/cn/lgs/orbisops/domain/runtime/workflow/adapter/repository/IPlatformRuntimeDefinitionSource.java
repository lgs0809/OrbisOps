package cn.lgs.orbisops.domain.runtime.workflow.adapter.repository;

import java.util.Map;
import java.util.Optional;

/** Platform-owned definitions are not user-published workflow versions. */
public interface IPlatformRuntimeDefinitionSource {
    Optional<Map<String, Object>> find(String agentId, int version, String definitionHash);
}
