package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.adapter.repository.IMcpTemplateRepository;
import cn.lgs.orbisops.domain.mcp.model.McpTemplateCatalogEntry;
import cn.lgs.orbisops.domain.mcp.model.McpTemplateDefinition;
import cn.lgs.orbisops.domain.mcp.model.McpTemplateStatus;
import cn.lgs.orbisops.domain.mcp.service.McpTemplatePolicy;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpTemplateCatalogApplicationServiceTest {

    @Test
    void createsTypedTemplateAndProjectsCompatibilityAliases() {
        InMemoryRepository repository = new InMemoryRepository();
        McpTemplateCatalogApplicationService service = service(repository, "copy001");

        Map<String, Object> result = service.create(Map.of(
                "mcpTemplateId", "mysql-template",
                "name", "MySQL Template",
                "type", "mysql",
                "transportType", "stdio",
                "defaultTransportConfig", mapWithNull(),
                "supportedActions", List.of("SELECT", "SELECT", ""),
                "riskLevel", "LOW",
                "readOnly", true,
                "status", "ENABLED",
                "createBy", "alice"));

        assertEquals("mysql-template", result.get("templateId"));
        assertEquals("mysql-template", result.get("mcpTemplateId"));
        assertEquals("MySQL Template", result.get("templateName"));
        assertEquals("MySQL Template", result.get("name"));
        assertEquals("mysql", result.get("resourceType"));
        assertEquals(List.of("SELECT"), result.get("supportedActions"));
        assertEquals(1L, result.get("id"));
        assertTrue(repository.findVisible("mysql-template").isPresent());
    }

    @Test
    void updatesWithoutChangingIdentityOrCreatorAndTransitionsStatus() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.insert(definition("template-1", "Template 1", "alice"));
        McpTemplateCatalogApplicationService service = service(repository, "copy001");

        Map<String, Object> updated = service.update("template-1", Map.of(
                "templateName", "Updated",
                "transportType", "streamable-http",
                "riskLevel", "HIGH",
                "createBy", "mallory"));
        Map<String, Object> disabled = service.updateStatus(
                "template-1", "DISABLED");

        assertEquals("template-1", updated.get("templateId"));
        assertEquals("alice", updated.get("createBy"));
        assertEquals("Updated", updated.get("templateName"));
        assertEquals("streamable-http", updated.get("transportType"));
        assertEquals("HIGH", updated.get("riskLevel"));
        assertEquals("DISABLED", disabled.get("status"));
    }

    @Test
    void copyUsesDeterministicSuffixAndNewCreator() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.insert(definition("template-1", "Template 1", "alice"));
        McpTemplateCatalogApplicationService service = service(repository, "copy001");

        Map<String, Object> copied = service.copy(
                "template-1",
                Map.of("createBy", "bob"));

        assertEquals("template-1-copy-copy001", copied.get("templateId"));
        assertEquals("Template 1 Copy", copied.get("templateName"));
        assertEquals("bob", copied.get("createBy"));
    }

    @Test
    void rejectsDuplicateImmutableIdentityAndDeletedTransition() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.insert(definition("template-1", "Template 1", "alice"));
        McpTemplateCatalogApplicationService service = service(repository, "copy001");

        IllegalArgumentException duplicate = assertThrows(
                IllegalArgumentException.class,
                () -> service.create(Map.of(
                        "templateId", "template-1",
                        "resourceType", "mysql",
                        "createBy", "bob")));
        IllegalArgumentException immutable = assertThrows(
                IllegalArgumentException.class,
                () -> service.update("template-1", Map.of(
                        "templateId", "template-2")));
        IllegalArgumentException deleted = assertThrows(
                IllegalArgumentException.class,
                () -> service.updateStatus("template-1", "DELETED"));

        assertTrue(duplicate.getMessage().contains("已存在"));
        assertEquals("MCP_TEMPLATE_ID_IMMUTABLE", immutable.getMessage());
        assertEquals("MCP_TEMPLATE_DELETE_REQUIRES_DEDICATED_COMMAND", deleted.getMessage());
    }

    private McpTemplateCatalogApplicationService service(
            IMcpTemplateRepository repository,
            String suffix) {
        return new McpTemplateCatalogApplicationService(
                repository,
                new McpTemplatePolicy(),
                () -> suffix);
    }

    private McpTemplateDefinition definition(
            String id,
            String name,
            String creator) {
        return new McpTemplateDefinition(
                id,
                name,
                "mysql",
                "stdio",
                Map.of("serverTemplate", "mysql-policy-mcp"),
                List.of("SELECT"),
                "LOW",
                true,
                "",
                McpTemplateStatus.ENABLED,
                creator);
    }

    private Map<String, Object> mapWithNull() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("serverTemplate", "mysql-policy-mcp");
        value.put("optional", null);
        return value;
    }

    private static final class InMemoryRepository implements IMcpTemplateRepository {

        private final Map<String, McpTemplateCatalogEntry> values = new LinkedHashMap<>();
        private long sequence;

        @Override
        public List<McpTemplateCatalogEntry> listVisible() {
            return new ArrayList<>(values.values());
        }

        @Override
        public Optional<McpTemplateCatalogEntry> findVisible(String templateId) {
            return Optional.ofNullable(values.get(templateId));
        }

        @Override
        public boolean exists(String templateId) {
            return values.containsKey(templateId);
        }

        @Override
        public McpTemplateCatalogEntry insert(McpTemplateDefinition definition) {
            LocalDateTime now = LocalDateTime.of(2026, 7, 22, 12, 0);
            McpTemplateCatalogEntry entry = new McpTemplateCatalogEntry(
                    ++sequence, definition, now, now);
            values.put(definition.templateId(), entry);
            return entry;
        }

        @Override
        public McpTemplateCatalogEntry update(McpTemplateDefinition definition) {
            McpTemplateCatalogEntry current = values.get(definition.templateId());
            McpTemplateCatalogEntry updated = new McpTemplateCatalogEntry(
                    current.catalogId(),
                    definition,
                    current.createdAt(),
                    LocalDateTime.of(2026, 7, 22, 13, 0));
            values.put(definition.templateId(), updated);
            return updated;
        }

        @Override
        public McpTemplateCatalogEntry updateStatus(
                String templateId,
                McpTemplateStatus status) {
            McpTemplateCatalogEntry current = values.get(templateId);
            McpTemplateDefinition definition = current.definition();
            return update(new McpTemplateDefinition(
                    definition.templateId(),
                    definition.templateName(),
                    definition.resourceType(),
                    definition.transportType(),
                    definition.defaultTransportConfig(),
                    definition.supportedActions(),
                    definition.riskLevel(),
                    definition.readOnly(),
                    definition.description(),
                    status,
                    definition.createBy()));
        }
    }
}
