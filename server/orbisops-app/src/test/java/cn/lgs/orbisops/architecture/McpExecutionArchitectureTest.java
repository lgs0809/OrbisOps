package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpExecutionArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java/cn/lgs/orbisops/domain/mcpexecution/";
    private static final String APPLICATION = "orbisops-application/src/main/java/cn/lgs/orbisops/application/mcpexecution/";
    private static final String TRIGGER = "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/";
    private static final String SERVICE = TRIGGER + "ops/toolset/OpsToolExecutionService.java";

    @Test
    void domainAndApplicationMustRemainTypedAndFrameworkFree() throws IOException {
        String domain = readTree(DOMAIN);
        String application = readTree(APPLICATION);
        String combined = domain + application;
        assertAll(
                () -> assertTrue(domain.contains("class McpExecutionPolicy")),
                () -> assertTrue(domain.contains("record McpExecutionRequest(")),
                () -> assertTrue(application.contains("class McpExecutionApplicationService")),
                () -> assertTrue(application.contains("McpExecutionRuntimePort")),
                () -> assertTrue(application.contains("McpExecutionRemotePort")),
                () -> assertTrue(application.contains("McpExecutionRouterPort")),
                () -> assertTrue(application.contains("McpExecutionRecordPort")),
                () -> assertFalse(combined.contains("org.springframework")),
                () -> assertFalse(combined.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(combined.contains("OpsMcpServerConfig")),
                () -> assertFalse(combined.contains("com.alibaba.fastjson")),
                () -> assertFalse(combined.contains("LANDING_RUNTIME_TOKEN")));
    }

    @Test
    void serviceMcpEntriesMustOnlyDelegateToTypedExecution() throws IOException {
        String service = read(SERVICE);
        String executionSection = service.substring(
                service.indexOf("public Map<String, Object> executeMcp("));
        assertAll(
                () -> assertTrue(executionSection.contains("executeTypedMcp(")),
                () -> assertTrue(executionSection.contains("mcpMapper.request(")),
                () -> assertTrue(executionSection.contains("OpsMcpToolExecutionBinding.CONTEXT_KEY")),
                () -> assertTrue(executionSection.contains("toolExecution.execute(toolRequest)")),
                () -> assertTrue(executionSection.contains("landingContext(mcpRequest")),
                () -> assertFalse(executionSection.contains("mcpExecution.execute(")),
                () -> assertFalse(service.contains("executeMcpInternal(")),
                () -> assertFalse(service.contains("progressiveSchema(")),
                () -> assertFalse(service.contains("mcpAuthorizationBlock(")),
                () -> assertFalse(service.contains("mcpPolicyBlock(")),
                () -> assertFalse(service.contains("mcpToolset(")),
                () -> assertFalse(service.contains("recordEnvelope(")),
                () -> assertFalse(service.contains("blockedEnvelope(")),
                () -> assertFalse(service.contains("parseJsonMap(")));
    }

    @Test
    void triggerAclsMustOwnRouterTokensRemoteProviderAndJson() throws IOException {
        String mapper = read(TRIGGER + "application/mcpexecution/OpsMcpExecutionMapper.java");
        String router = read(TRIGGER + "application/mcpexecution/OpsMcpExecutionRouterAdapter.java");
        String remote = read(TRIGGER + "application/mcpexecution/OpsMcpExecutionRemoteAdapter.java");
        String record = read(TRIGGER + "application/mcpexecution/OpsMcpExecutionRecordAdapter.java");
        assertAll(
                () -> assertTrue(mapper.contains("JSON.parse")),
                () -> assertTrue(mapper.contains("LANDING_RUNTIME_TOKEN")),
                () -> assertTrue(router.contains("OpsToolsetRouter")),
                () -> assertTrue(remote.contains("callProgressiveDirect")),
                () -> assertTrue(record.contains("ToolResultApplicationService")),
                () -> assertTrue(record.contains("EvidenceApplicationService")),
                () -> assertFalse(record.contains("OpsToolResultStore")),
                () -> assertFalse(record.contains("OpsEvidenceStore")));
    }

    private String readTree(String relativeRoot) throws IOException {
        try (var stream = Files.walk(projectRoot().resolve(relativeRoot))) {
            StringBuilder source = new StringBuilder();
            for (Path file : stream.filter(path -> path.toString().endsWith(".java")).toList()) {
                source.append(Files.readString(file)).append('\n');
            }
            return source.toString();
        }
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-trigger"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-trigger"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-trigger"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
