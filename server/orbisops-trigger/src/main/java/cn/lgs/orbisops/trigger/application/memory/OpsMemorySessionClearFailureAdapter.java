package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.memory.MemorySessionClearFailurePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Runtime diagnostics for isolated session-memory clear failures. */
@Slf4j
@Component
public class OpsMemorySessionClearFailureAdapter implements MemorySessionClearFailurePort {

    @Override
    public void onFailure(String operation, RuntimeException error) {
        log.warn("清理运维会话记忆失败，operation={}，其余清理步骤继续：{}",
                operation,
                error == null ? "unknown" : error.getMessage());
    }
}
