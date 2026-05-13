package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.memory.MemoryCaptureFailurePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Runtime diagnostics for fail-open captured-message persistence. */
@Slf4j
@Component
public class OpsMemoryCaptureFailureAdapter implements MemoryCaptureFailurePort {

    @Override
    public void onFailure(String operation, RuntimeException error) {
        log.warn("写入运维对话记忆失败，operation={}，已降级跳过：{}",
                operation,
                error == null ? "unknown" : error.getMessage());
    }
}
