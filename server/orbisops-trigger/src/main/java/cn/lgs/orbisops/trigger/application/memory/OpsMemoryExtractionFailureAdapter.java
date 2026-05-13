package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.memory.MemoryExtractionFailurePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Trigger observer for optional model extraction failures. */
@Slf4j
@Component
public class OpsMemoryExtractionFailureAdapter implements MemoryExtractionFailurePort {

    @Override
    public void onFailure(String operation, RuntimeException error) {
        log.warn("LLM 长期记忆抽取失败，降级使用规则抽取：{}",
                error == null ? "unknown" : error.getMessage());
    }
}
