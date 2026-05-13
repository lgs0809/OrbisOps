package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.memory.MemoryRetrievalFailurePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Emits low-noise diagnostics for isolated memory retrieval slice failures. */
@Slf4j
@Component
public class OpsMemoryRetrievalFailureAdapter implements MemoryRetrievalFailurePort {

    @Override
    public void onFailure(String slice, RuntimeException error) {
        log.debug("加载运维记忆分片失败，slice={}，已跳过：{}",
                slice,
                error == null ? "unknown" : error.getMessage());
    }
}
