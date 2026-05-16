package cn.lgs.orbisops.trigger.ops.runtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/** Emits the in-memory fallback warning once per process. */
@Slf4j
@Component
public final class OpsChatSessionFallbackReporter {

    private final AtomicBoolean logged = new AtomicBoolean(false);

    public void report(RuntimeException error) {
        if (logged.compareAndSet(false, true)) {
            log.warn(
                    "通用 Agent 会话 MySQL 存储不可用，已降级：{}",
                    error == null ? "unknown" : error.getMessage());
        }
    }
}
