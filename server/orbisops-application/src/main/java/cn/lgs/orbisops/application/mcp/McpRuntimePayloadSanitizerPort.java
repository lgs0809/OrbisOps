package cn.lgs.orbisops.application.mcp;

public interface McpRuntimePayloadSanitizerPort {

    String sanitize(Object value);
}
