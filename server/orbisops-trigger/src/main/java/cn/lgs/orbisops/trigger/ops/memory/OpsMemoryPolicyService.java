package cn.lgs.orbisops.trigger.ops.memory;

import cn.lgs.orbisops.domain.memory.service.MemoryContentPolicy;
import org.springframework.stereotype.Service;

/** Compatibility adapter. Memory content authority belongs to the Domain policy. */
@Service
public class OpsMemoryPolicyService {

    private final MemoryContentPolicy policy = new MemoryContentPolicy();

    public void assertAllowed(String content) {
        policy.requireAllowed(content);
    }
}
