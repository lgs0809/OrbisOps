package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.memory.MemoryPostProcessingFailurePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Runtime diagnostics for fail-open memory post-processing operations. */
@Slf4j
@Component
public class OpsMemoryPostProcessingFailureAdapter implements MemoryPostProcessingFailurePort {

    @Override
    public void onFailure(String operation, RuntimeException error) {
        log.warn("运维记忆后处理失败，operation={}，已降级：{}",
                operation,
                error == null ? "unknown" : error.getMessage());
    }
}
