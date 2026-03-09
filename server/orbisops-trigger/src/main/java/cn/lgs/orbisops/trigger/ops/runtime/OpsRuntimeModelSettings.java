package cn.lgs.orbisops.trigger.ops.runtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Immutable HTTP timeout settings for runtime-created chat models. */
@Component
public final class OpsRuntimeModelSettings {

    private final int connectTimeoutSeconds;
    private final int readTimeoutSeconds;

    public OpsRuntimeModelSettings(
            @Value("${spring.ai.openai.chat.connect-timeout-seconds:5}")
            int connectTimeoutSeconds,
            @Value("${spring.ai.openai.chat.read-timeout-seconds:45}")
            int readTimeoutSeconds) {
        this.connectTimeoutSeconds = Math.max(1, connectTimeoutSeconds);
        this.readTimeoutSeconds = Math.max(1, readTimeoutSeconds);
    }

    public int connectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public int readTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    public static OpsRuntimeModelSettings forTest(int connectTimeoutSeconds, int readTimeoutSeconds) {
        return new OpsRuntimeModelSettings(connectTimeoutSeconds, readTimeoutSeconds);
    }
}
