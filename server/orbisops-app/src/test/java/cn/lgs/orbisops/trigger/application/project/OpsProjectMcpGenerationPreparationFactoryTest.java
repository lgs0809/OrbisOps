package cn.lgs.orbisops.trigger.application.project;

import cn.lgs.orbisops.application.project.ProjectMcpGenerationPreparation;
import cn.lgs.orbisops.application.project.ProjectMcpGenerationPreparationRequest;
import cn.lgs.orbisops.application.project.ProjectMcpTemplateGenerationPreparation;
import cn.lgs.orbisops.application.project.ProjectMcpTemplateGenerationPreparationRequest;
import cn.lgs.orbisops.domain.mcp.model.McpTemplateDefinition;
import cn.lgs.orbisops.domain.mcp.model.McpTemplateStatus;
import cn.lgs.orbisops.domain.project.model.ProjectResourceDefinition;
import cn.lgs.orbisops.domain.project.model.ProjectResourceType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsProjectMcpGenerationPreparationFactoryTest {

    private final OpsProjectMcpGenerationPreparationFactory factory =
            new OpsProjectMcpGenerationPreparationFactory();

    @Test
    void buildsReadonlyMysqlGenerationPreparation() {
        ProjectMcpGenerationPreparation preparation = factory.prepare(
                new ProjectMcpGenerationPreparationRequest(
                        "project-1",
                        "orders-db",
                        "orders-mcp",
                        "production",
                        resource()));

        assertEquals("Orders DB production MCP", preparation.mcpName());
        assertEquals("mysql", preparation.resourceType());
        assertEquals("mysql-readonly-template", preparation.templateId());
        assertEquals("stdio", preparation.transportType());
        assertEquals(List.of("SELECT", "EXPLAIN"), preparation.allowedActions());
        assertEquals(15, preparation.requestTimeout());
        assertTrue(preparation.readOnly());
        assertTrue(preparation.transportConfig().containsKey("remoteToolMetadata"));
        @SuppressWarnings("unchecked")
        Map<String, Object> credential = (Map<String, Object>) preparation
                .transportConfig().get("credential");
        assertEquals("******", credential.get("passwordMasked"));
    }

    @Test
    void appliesTemplateCommandOverridesAndNormalizesStatus() {
        ProjectMcpTemplateGenerationPreparation preparation = factory.prepareTemplate(
                new ProjectMcpTemplateGenerationPreparationRequest(
                        "project-1",
                        "logs",
                        "logs-mcp",
                        "default",
                        resource(
                                "logs",
                                "elasticsearch",
                                "Elasticsearch",
                                "Logs",
                                "default",
                                "http://es.example:9200",
                                Map.of(),
                                Map.of(),
                                Map.of("actions", List.of("SEARCH_INDEX"))),
                        new McpTemplateDefinition(
                                "es-template",
                                "Elasticsearch Template",
                                "elasticsearch",
                                "stdio",
                                Map.of("serverTemplate", "elasticsearch-policy-mcp"),
                                List.of("SEARCH_INDEX"),
                                "MEDIUM",
                                true,
                                "",
                                McpTemplateStatus.ENABLED,
                                "tester"),
                        Map.of(
                                "toolName", "Controlled Logs Search",
                                "riskLevel", "low",
                                "requestTimeout", 12,
                                "status", "unexpected-status",
                                "allowedActions", List.of("SEARCH_INDEX"),
                                "permissionPolicy", Map.of("allowJoin", true))));

        assertEquals("Controlled Logs Search", preparation.mcpName());
        assertEquals("es-template", preparation.templateId());
        assertEquals("LOW", preparation.riskLevel());
        assertEquals(12, preparation.requestTimeout());
        assertEquals("PENDING_REVIEW", preparation.status());
        assertTrue(preparation.transportConfig().containsKey("remoteToolMetadata"));
        @SuppressWarnings("unchecked")
        Map<String, Object> multiObject = (Map<String, Object>) preparation
                .permissionPolicy().get("multiObjectPermission");
        assertEquals("cross_index_search", multiObject.get("key"));
        assertEquals(true, multiObject.get("enabled"));
    }

    private ProjectResourceDefinition resource() {
        return resource(
                "orders-db",
                "mysql",
                "MySQL",
                "Orders DB",
                "production",
                "mysql://db.example:3306/orders",
                Map.of(
                        "username", "reader",
                        "passwordRef", "${env:TEST_DB_PASSWORD}"),
                Map.of("status", "READY"),
                Map.of(
                        "actions", List.of("SELECT", "EXPLAIN"),
                        "allowJoin", false));
    }

    private ProjectResourceDefinition resource(
            String resourceId,
            String type,
            String typeName,
            String name,
            String environment,
            String endpoint,
            Map<String, Object> credential,
            Map<String, Object> schema,
            Map<String, Object> permission) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 31, 12, 0);
        return new ProjectResourceDefinition(
                resourceId,
                "project-1",
                ProjectResourceType.from(type),
                typeName,
                name,
                environment,
                endpoint,
                credential,
                "READY",
                schema,
                permission,
                now,
                now);
    }
}
