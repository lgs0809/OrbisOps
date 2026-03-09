package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.memory.MemoryQueryFailurePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Runtime diagnostics for fail-open unified memory query assembly. */
@Slf4j
@Component
public class OpsMemoryQueryFailureAdapter implements MemoryQueryFailurePort {

    @Override
    public void onFailure(String operation, RuntimeException error) {
        log.warn("组装运维记忆上下文失败，operation={}，已降级为空上下文：{}",
                operation,
                error == null ? "unknown" : error.getMessage());
    }
}
