package cn.lgs.orbisops.application.mcp;

import cn.lgs.orbisops.domain.mcp.adapter.repository.IMcpTemplateRepository;
import cn.lgs.orbisops.domain.mcp.model.McpTemplateCatalogEntry;
import cn.lgs.orbisops.domain.mcp.model.McpTemplateDefinition;
import cn.lgs.orbisops.domain.mcp.model.McpTemplateStatus;
import cn.lgs.orbisops.domain.mcp.service.McpTemplatePolicy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Application owner of the typed MCP template catalog lifecycle. */
public final class McpTemplateCatalogApplicationService {

    private final IMcpTemplateRepository repository;
    private final McpTemplatePolicy policy;
    private final Supplier<String> copySuffixSupplier;

    public McpTemplateCatalogApplicationService(IMcpTemplateRepository repository) {
        this(
                repository,
                new McpTemplatePolicy(),
                () -> UUID.randomUUID().toString().replace("-", "").substring(0, 8));
    }

    McpTemplateCatalogApplicationService(
            IMcpTemplateRepository repository,
            McpTemplatePolicy policy,
            Supplier<String> copySuffixSupplier) {
        if (repository == null) {
            throw new IllegalArgumentException("MCP_TEMPLATE_REPOSITORY_REQUIRED");
        }
        if (policy == null) {
            throw new IllegalArgumentException("MCP_TEMPLATE_POLICY_REQUIRED");
        }
        if (copySuffixSupplier == null) {
            throw new IllegalArgumentException("MCP_TEMPLATE_COPY_SUFFIX_REQUIRED");
        }
        this.repository = repository;
        this.policy = policy;
        this.copySuffixSupplier = copySuffixSupplier;
    }

    public List<Map<String, Object>> list() {
        return listEntries().stream().map(McpTemplateCatalogView::of).toList();
    }

    public List<McpTemplateCatalogEntry> listEntries() {
        List<McpTemplateCatalogEntry> entries = repository.listVisible();
        return entries == null ? List.of() : List.copyOf(entries);
    }

    public Map<String, Object> get(String templateId) {
        return McpTemplateCatalogView.of(getEntry(templateId));
    }

    public McpTemplateCatalogEntry getEntry(String templateId) {
        return find(templateId);
    }

    public Map<String, Object> create(Map<String, Object> request) {
        return McpTemplateCatalogView.of(createDefinition(policy.create(request)));
    }

    public McpTemplateCatalogEntry createDefinition(McpTemplateDefinition definition) {
        McpTemplateDefinition required = required(definition);
        if (repository.exists(required.templateId())) {
            throw new IllegalArgumentException("MCP 模板已存在：" + required.templateId());
        }
        return repository.insert(required);
    }

    public Map<String, Object> update(String templateId, Map<String, Object> request) {
        McpTemplateCatalogEntry current = find(templateId);
        McpTemplateDefinition updated = policy.update(
                policy.toMap(current.definition()), request);
        return McpTemplateCatalogView.of(updateDefinition(templateId, updated));
    }

    public McpTemplateCatalogEntry updateDefinition(
            String templateId,
            McpTemplateDefinition definition) {
        McpTemplateCatalogEntry current = find(templateId);
        McpTemplateDefinition required = required(definition);
        if (!current.definition().templateId().equals(required.templateId())) {
            throw new IllegalArgumentException("MCP_TEMPLATE_ID_IMMUTABLE");
        }
        return repository.update(withCreator(required, current.definition().createBy()));
    }

    public Map<String, Object> updateStatus(String templateId, String status) {
        McpTemplateStatus target = policy.transition(
                find(templateId).definition().status().name(), status);
        return McpTemplateCatalogView.of(updateStatusDefinition(templateId, target));
    }

    public McpTemplateCatalogEntry updateStatusDefinition(
            String templateId,
            McpTemplateStatus status) {
        if (status == null) throw new IllegalArgumentException("MCP_TEMPLATE_STATUS_REQUIRED");
        McpTemplateCatalogEntry current = find(templateId);
        McpTemplateStatus target = policy.transition(
                current.definition().status().name(), status.name());
        return repository.updateStatus(current.definition().templateId(), target);
    }

    public Map<String, Object> copy(String templateId, Map<String, Object> request) {
        return McpTemplateCatalogView.of(copyDefinition(templateId, request));
    }

    public McpTemplateCatalogEntry copyDefinition(
            String templateId,
            Map<String, Object> request) {
        McpTemplateCatalogEntry source = find(templateId);
        Map<String, Object> command = new LinkedHashMap<>(
                policy.toMap(source.definition()));
        Map<String, Object> copyRequest = request == null ? Map.of() : request;
        String nextId = text(copyRequest.get("templateId"));
        if (nextId.isBlank()) {
            nextId = source.definition().templateId()
                    + "-copy-"
                    + copySuffixSupplier.get();
        }
        command.put("templateId", nextId);
        command.put("mcpTemplateId", nextId);
        command.put("templateName", text(
                copyRequest.get("templateName"),
                source.definition().templateName() + " Copy"));
        command.put("createBy", text(copyRequest.get("createBy")));
        copyRequest.forEach(command::put);
        command.put("templateId", nextId);
        command.put("mcpTemplateId", nextId);
        return createDefinition(policy.create(command));
    }

    private McpTemplateCatalogEntry find(String templateId) {
        String id = required(templateId, "MCP_TEMPLATE_ID_REQUIRED");
        return repository.findVisible(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "MCP 模板不存在：" + id));
    }

    private McpTemplateDefinition required(McpTemplateDefinition definition) {
        if (definition == null) throw new IllegalArgumentException("MCP_TEMPLATE_DEFINITION_REQUIRED");
        return definition;
    }

    private McpTemplateDefinition withCreator(
            McpTemplateDefinition definition,
            String creator) {
        return new McpTemplateDefinition(
                definition.templateId(),
                definition.templateName(),
                definition.resourceType(),
                definition.transportType(),
                definition.defaultTransportConfig(),
                definition.supportedActions(),
                definition.riskLevel(),
                definition.readOnly(),
                definition.description(),
                definition.status(),
                creator);
    }

    private String required(String value, String error) {
        String normalized = text(value);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(error);
        }
        return normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String text(Object value, String fallback) {
        String normalized = text(value);
        return normalized.isBlank() ? fallback : normalized;
    }
}
