package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeMcpResourceBoundaryArchitectureTest {

    private static final String RUNTIME = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/runtime/";

    @Test
    void resolverMustOwnSourceChainDecorationAndCallbackAssembly() throws IOException {
        String resolver = read(RUNTIME + "OpsRuntimeMcpResolver.java");

        assertAll(
                () -> assertTrue(resolver.contains("private final OpsMcpToolProvider toolProvider;")),
                () -> assertTrue(resolver.contains("private final OpsMcpRuntimeConfigSourceChain configSources;")),
                () -> assertTrue(resolver.contains("Supplier<ProjectMcpAuthorizationApplicationService>")),
                () -> assertTrue(resolver.contains("configSources.resolve(")),
                () -> assertTrue(resolver.contains("MCP_CONFIG_RESOLVED")),
                () -> assertTrue(resolver.contains("fallbackReason")),
                () -> assertFalse(resolver.contains("IAiClientToolMcpConfigRepository")),
                () -> assertFalse(resolver.contains("OpsProjectMcpRuntimeConfigService")),
                () -> assertFalse(resolver.contains("OpsSourceRepositoryService")),
                () -> assertFalse(resolver.contains("OpsLegacyMcpConfigMapper")),
                () -> assertTrue(resolver.contains("toolProvider.buildToolCallbacks(servers)")),
                () -> assertTrue(resolver.contains("toolProvider::currentAuthorizedDefinition")),
                () -> assertTrue(resolver.contains("server.setToolCallStage(stage)")),
                () -> assertTrue(resolver.contains("RESOURCE_WARN")),
                () -> assertFalse(resolver.contains("ObjectProvider")),
                () -> assertFalse(resolver.contains("@Autowired")),
                () -> assertFalse(resolver.contains("@Value")),
                () -> assertFalse(resolver.contains("JSON.parseObject")),
                () -> assertTrue(resolver.contains("AgentRunExecutionContext executionContext")),
                () -> assertTrue(resolver.contains("AGENT_RUN_EXECUTION_CONTEXT_EXPIRED")),
                () -> assertFalse(resolver.contains("definition.getPhase()")),
                () -> assertFalse(resolver.contains("definition.getCapabilities()")),
                () -> assertTrue(resolver.lines().count() < 375));
    }

    @Test
    void legacyMapperMustOwnOnlyTypedCatalogTransportConversion() throws IOException {
        String mapper = read(RUNTIME + "OpsLegacyMcpConfigMapper.java");

        assertAll(
                () -> assertTrue(mapper.contains("McpClientDefinition")),
                () -> assertFalse(mapper.contains("AiClientConfigRecord")),
                () -> assertFalse(mapper.contains("domain.agent")),
                () -> assertTrue(mapper.contains("JSON.parseObject")),
                () -> assertTrue(mapper.contains("toolCapabilities")),
                () -> assertTrue(mapper.contains("allowedTools")),
                () -> assertTrue(mapper.contains("streamable-http")),
                () -> assertTrue(mapper.contains("builder.command")),
                () -> assertFalse(mapper.contains("OpsMcpToolProvider")),
                () -> assertFalse(mapper.contains("ObjectProvider")),
                () -> assertFalse(mapper.contains("@Autowired")),
                () -> assertFalse(mapper.contains("@Value")),
                () -> assertTrue(mapper.lines().count() < 140));
    }

    @Test
    void pipelineMustDelegateMcpRuleAndAssemblerMustNeverReclaimMcpInfrastructure() throws IOException {
        String pipeline = read(RUNTIME + "OpsRuntimeResourcePipeline.java");
        String contextFactory = read(RUNTIME + "OpsRuntimeResourceContextFactory.java");
        String assembler = read(RUNTIME + "OpsRuntimeResourceAssembler.java");

        assertAll(
                () -> assertTrue(pipeline.contains("OpsRuntimeMcpResolver mcpResolver")),
                () -> assertTrue(pipeline.contains("rule(\"MCP\", mcpResolver::resolve)")),
                () -> assertTrue(contextFactory.contains("mcpResolver.enabledProjectMcpIds(projectId)")),
                () -> assertEquals(1, occurrences(assembler, "public OpsRuntimeResourceAssembler(")),
                () -> assertFalse(assembler.contains("OpsRuntimeMcpResolver")),
                () -> assertFalse(assembler.contains("OpsMcpToolProvider")),
                () -> assertFalse(assembler.contains("OpsProjectMcpRuntimeConfigService")),
                () -> assertFalse(assembler.contains("IAiClientToolMcpConfigRepository")),
                () -> assertFalse(assembler.contains("ProjectMcpAuthorizationApplicationService")),
                () -> assertFalse(assembler.contains("OpsSourceRepositoryService")),
                () -> assertFalse(assembler.contains("AiClientConfigRecord")),
                () -> assertFalse(assembler.contains("JSON.parseObject")),
                () -> assertFalse(assembler.contains("toOpsMcpServerConfig")),
                () -> assertFalse(assembler.contains("toolCallStage(")),
                () -> assertTrue(assembler.lines().count() < 70));
    }

    @Test
    void configurationMustAssembleOrderedSourcesAndResolver() throws IOException {
        String configuration = read(RUNTIME + "OpsRuntimeMcpResolverConfiguration.java");
        String projectSource = read(RUNTIME + "OpsProjectMcpRuntimeConfigSource.java");
        String legacySource = read(RUNTIME + "OpsLegacyMcpRuntimeConfigSource.java");
        String repositorySource = read(RUNTIME + "OpsSourceRepositoryMcpRuntimeConfigSource.java");

        assertAll(
                () -> assertTrue(configuration.contains("List<OpsMcpRuntimeConfigSource> sources")),
                () -> assertTrue(configuration.contains("OpsMcpRuntimeConfigResolutionTelemetry telemetry")),
                () -> assertTrue(configuration.contains("new OpsMcpRuntimeConfigSourceChain(sources, telemetry)")),
                () -> assertTrue(configuration.contains("ObjectProvider<ProjectMcpAuthorizationApplicationService>")),
                () -> assertTrue(configuration.contains("new OpsLegacyMcpConfigMapper()")),
                () -> assertTrue(configuration.contains("new OpsRuntimeMcpResolver(")),
                () -> assertTrue(configuration.contains("authorizationProvider::getIfAvailable")),
                () -> assertTrue(projectSource.contains("ObjectProvider<OpsProjectMcpRuntimeConfigService>")),
                () -> assertTrue(legacySource.contains("ObjectProvider<McpClientCatalogPort>")),
                () -> assertFalse(legacySource.contains("IAiClientToolMcpConfigRepository")),
                () -> assertFalse(legacySource.contains("AiClientConfigRecord")),
                () -> assertTrue(legacySource.contains("ObjectProvider<ProjectMcpAuthorizationApplicationService>")),
                () -> assertTrue(repositorySource.contains("ObjectProvider<OpsSourceRepositoryService>")),
                () -> assertTrue(configuration.lines().count() < 45));
    }

    private int occurrences(String source, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
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
