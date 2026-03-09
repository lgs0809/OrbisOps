package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.memory.ColdMemoryStoreFailurePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Logs the first durable Cold Memory failure while preserving fail-open runtime behavior. */
@Slf4j
@Component
public class OpsColdMemoryStoreFailureAdapter implements ColdMemoryStoreFailurePort {

    private volatile boolean unavailableLogged;

    @Override
    public void onFailure(String operation, RuntimeException error) {
        if (unavailableLogged) return;
        unavailableLogged = true;
        log.warn("运维对话冷记忆持久化失败，已降级，operation={}：{}",
                operation,
                error == null ? "unknown" : error.getMessage());
    }
}
