package cn.lgs.orbisops.trigger.application.source;

/** Typed runtime settings for generated source-repository MCP projections. */
public record OpsSourceMcpProjectionSettings(int requestTimeoutSeconds) {

    public OpsSourceMcpProjectionSettings {
        requestTimeoutSeconds = Math.max(1, Math.min(requestTimeoutSeconds, 300));
    }

    public static OpsSourceMcpProjectionSettings defaults() {
        return new OpsSourceMcpProjectionSettings(8);
    }
}
