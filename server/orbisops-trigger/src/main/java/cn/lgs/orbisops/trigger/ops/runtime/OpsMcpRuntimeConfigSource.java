package cn.lgs.orbisops.trigger.ops.runtime;

/** Ordered first-match source for executable MCP runtime configuration. */
public interface OpsMcpRuntimeConfigSource {

    String sourceId();

    int order();

    OpsMcpRuntimeConfigSourceResult resolve(OpsMcpRuntimeConfigRequest request);
}
