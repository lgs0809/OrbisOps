package cn.lgs.orbisops.application.config;

/** Security protocol boundary for MCP transport configuration placeholders and read masking. */
public interface McpTransportConfigProtectionPort {

    String resolveIncoming(String incomingConfig, String existingConfig);

    String protectForRead(String rawConfig);
}
