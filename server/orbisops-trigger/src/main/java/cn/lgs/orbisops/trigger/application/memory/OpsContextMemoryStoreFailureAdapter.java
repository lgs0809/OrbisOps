package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.memory.ContextMemoryStoreFailurePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Runtime diagnostics for best-effort Context Memory projection persistence. */
@Slf4j
@Component
public class OpsContextMemoryStoreFailureAdapter implements ContextMemoryStoreFailurePort {

    @Override
    public void onFailure(String operation, RuntimeException error) {
        log.debug("同步 Context Memory 失败，operation={}，已跳过：{}",
                operation,
                error == null ? "unknown" : error.getMessage());
    }
}
