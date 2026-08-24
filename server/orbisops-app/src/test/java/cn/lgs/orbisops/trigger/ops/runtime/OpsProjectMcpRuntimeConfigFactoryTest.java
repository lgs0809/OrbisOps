package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.project.ProjectMcpRuntimeDescriptor;
import cn.lgs.orbisops.domain.project.model.ProjectMcpRiskLevel;
import cn.lgs.orbisops.domain.project.model.ProjectMcpDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectMcpStatus;
import cn.lgs.orbisops.domain.project.model.ProjectResourceDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectResourceType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsProjectMcpRuntimeConfigFactoryTest {

    @Test
    void buildsInternalMysqlRuntimeConfigFromDescriptor() {
        OpsSecretResolver secrets = mock(OpsSecretResolver.class);
        when(secrets.resolve("${env:TEST_DB_PASSWORD}")).thenReturn("resolved-password");
        OpsProjectMcpRuntimeConfigFactory factory = new OpsProjectMcpRuntimeConfigFactory(secrets);
        ProjectMcpRuntimeDescriptor descriptor = new ProjectMcpRuntimeDescriptor(
                mcp(
                        "orders-mcp",
                        "Orders MCP",
                        "orders-db",
                        "mysql",
                        "stdio",
                        Map.of(),
                        List.of()),
                resource(
                        "orders-db",
                        "mysql",
                        "mysql://db.example:3307/orders",
                        Map.of(
                                "username", "reader",
                                "passwordRef", "${env:TEST_DB_PASSWORD}"),
                        Map.of(
                                "maxRows", 50,
                                "objects", List.of("orders"),
                                "allowJoin", false)));

        OpsMcpServerConfig config = factory.build(descriptor);

        assertEquals("orders-mcp", config.getMcpId());
        assertEquals("orders-mcp", config.getToolId());
        assertEquals("stdio", config.getTransport());
        assertEquals("node", config.getCommand());
        assertEquals("db.example", config.getEnv().get("MYSQL_HOST"));
        assertEquals("3307", config.getEnv().get("MYSQL_PORT"));
        assertEquals("orders", config.getEnv().get("MYSQL_DATABASE"));
        assertEquals("reader", config.getEnv().get("MYSQL_USER"));
        assertEquals("resolved-password", config.getEnv().get("MYSQL_PASSWORD"));
        assertEquals("50", config.getEnv().get("MYSQL_MCP_MAX_LIMIT"));
    }

    @Test
    void buildsExternalBearerRuntimeConfigWithoutResolvingStoredReference() {
        OpsSecretResolver secrets = mock(OpsSecretResolver.class);
        when(secrets.isReference("${env:TEST_MCP_TOKEN}")).thenReturn(true);
        OpsProjectMcpRuntimeConfigFactory factory = new OpsProjectMcpRuntimeConfigFactory(secrets);
        ProjectMcpRuntimeDescriptor descriptor = new ProjectMcpRuntimeDescriptor(
                mcp(
                        "logs-mcp",
                        "Logs MCP",
                        "",
                        "external_mcp",
                        "streamable-http",
                        Map.of(
                                "endpoint", "https://mcp.example.test/mcp",
                                "authMode", "BEARER",
                                "credentialRef", "${env:TEST_MCP_TOKEN}"),
                        List.of("search_logs")),
                null);

        OpsMcpServerConfig config = factory.build(descriptor);

        assertEquals("streamable-http", config.getTransport());
        assertEquals("https://mcp.example.test/mcp", config.getUrl());
        assertEquals("Bearer ${env:TEST_MCP_TOKEN}", config.getHeaders().get("Authorization"));
        assertEquals(List.of("search_logs"), config.getAllowedTools());
        assertEquals("INVESTIGATE,PREPARE,LANDING", config.getToolCapabilities().get("allowedStages"));
        assertEquals("true", config.getToolCapabilities().get("readOnly"));
        assertEquals(true, config.getProgressiveManaged());
    }

    @Test
    void buildsControlledServiceRuntimeConfigWithDedicatedProvider() {
        OpsProjectMcpRuntimeConfigFactory factory = new OpsProjectMcpRuntimeConfigFactory(
                mock(OpsSecretResolver.class));
        ProjectMcpRuntimeDescriptor descriptor = new ProjectMcpRuntimeDescriptor(
                mcp(
                        "service-control-mcp",
                        "Service Control MCP",
                        "service-control",
                        "service_control",
                        "stdio",
                        Map.of(),
                        List.of("restart_service_dry_run", "restart_service")),
                resource(
                        "service-control",
                        "service_control",
                        "service-control://order-service",
                        Map.of(),
                        Map.of("lockTimeoutMs", 3000, "readOnly", false)));

        OpsMcpServerConfig config = factory.build(descriptor);

        assertEquals("node", config.getCommand());
        assertEquals(List.of(
                "get_service_status",
                "restart_service_dry_run",
                "restart_service",
                "get_operation_receipt"), config.getAllowedTools());
        assertEquals("order-service", config.getEnv().get("SERVICE_CONTROL_ALLOWED_SERVICES"));
        assertEquals("3000", config.getEnv().get("SERVICE_CONTROL_LOCK_TIMEOUT_MS"));
        assertEquals("false", config.getToolCapabilities().get("readOnly"));
        assertEquals("INVESTIGATE,LANDING", config.getToolCapabilities().get("allowedStages"));
        assertEquals(true, config.getArgs().stream().anyMatch(item -> item.contains("service-control-mcp-server.mjs")));
    }

    @Test
    void buildsOpenApiRuntimeConfigWithSingleReadOnlyTool() {
        OpsProjectMcpRuntimeConfigFactory factory = new OpsProjectMcpRuntimeConfigFactory(
                mock(OpsSecretResolver.class));
        ProjectMcpRuntimeDescriptor descriptor = new ProjectMcpRuntimeDescriptor(
                mcp(
                        "openapi-mcp",
                        "OpenAPI MCP",
                        "project-openapi",
                        "openapi",
                        "stdio",
                        Map.of(),
                        List.of("READ_OPENAPI")),
                resource(
                        "project-openapi",
                        "openapi",
                        "file:///tmp/project-openapi.json",
                        Map.of(),
                        Map.of()));

        OpsMcpServerConfig config = factory.build(descriptor);

        assertEquals("node", config.getCommand());
        assertEquals(List.of("openapi_list_operations"), config.getAllowedTools());
        assertEquals("file:///tmp/project-openapi.json", config.getEnv().get("OPENAPI_URL"));
        assertEquals("10000", config.getEnv().get("OPENAPI_TIMEOUT_MS"));
        assertEquals("true", config.getToolCapabilities().get("readOnly"));
        assertEquals("INVESTIGATE,PREPARE,LANDING", config.getToolCapabilities().get("allowedStages"));
        assertEquals(true, config.getArgs().stream().anyMatch(item -> item.contains("openapi-mcp-server.mjs")));
    }

    @Test
    void rejectsExternalBearerCredentialThatIsNotAReference() {
        OpsSecretResolver secrets = mock(OpsSecretResolver.class);
        OpsProjectMcpRuntimeConfigFactory factory = new OpsProjectMcpRuntimeConfigFactory(secrets);
        ProjectMcpRuntimeDescriptor descriptor = new ProjectMcpRuntimeDescriptor(
                mcp(
                        "logs-mcp",
                        "Logs MCP",
                        "",
                        "external_mcp",
                        "streamable-http",
                        Map.of(
                                "endpoint", "https://mcp.example.test/mcp",
                                "authMode", "BEARER",
                                "credentialRef", "invalid-reference"),
                        List.of()),
                null);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> factory.build(descriptor));

        assertEquals("MCP_CREDENTIAL_REFERENCE_INVALID", error.getMessage());
    }

    private ProjectMcpDefinition mcp(
            String mcpId,
            String name,
            String resourceId,
            String resourceType,
            String transportType,
            Map<String, Object> transportConfig,
            List<String> allowedActions) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 31, 15, 40);
        return new ProjectMcpDefinition(
                mcpId,
                name,
                "project-1",
                resourceId,
                resourceType,
                transportType,
                "",
                transportConfig,
                allowedActions,
                "service_control".equals(resourceType)
                        ? ProjectMcpRiskLevel.HIGH
                        : ProjectMcpRiskLevel.LOW,
                !"service_control".equals(resourceType),
                Map.of(),
                30,
                ProjectMcpStatus.ENABLED,
                now,
                now);
    }

    private ProjectResourceDefinition resource(
            String resourceId,
            String type,
            String endpoint,
            Map<String, Object> credential,
            Map<String, Object> permission) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 31, 15, 40);
        return new ProjectResourceDefinition(
                resourceId,
                "project-1",
                ProjectResourceType.from(type),
                type,
                resourceId,
                "prod",
                endpoint,
                credential,
                "READY",
                Map.of(),
                permission,
                now,
                now);
    }
}
