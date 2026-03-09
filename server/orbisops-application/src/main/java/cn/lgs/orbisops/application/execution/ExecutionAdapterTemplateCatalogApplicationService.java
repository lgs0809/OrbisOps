package cn.lgs.orbisops.application.execution;

import cn.lgs.orbisops.domain.execution.adapter.repository.IExecutionAdapterTemplateRepository;
import cn.lgs.orbisops.domain.execution.model.ExecutionAdapterTemplate;
import cn.lgs.orbisops.domain.execution.model.ExecutionAdapterType;
import cn.lgs.orbisops.domain.execution.model.ExecutionResourceStatus;
import cn.lgs.orbisops.domain.execution.model.ExecutionRiskLevel;
import cn.lgs.orbisops.domain.execution.service.ExecutionAdapterTemplatePolicy;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public final class ExecutionAdapterTemplateCatalogApplicationService {

    private final IExecutionAdapterTemplateRepository repository;
    private final ExecutionAdapterGeneratedTargetQueryPort generatedTargetQueryPort;
    private final ExecutionAdapterTemplatePolicy policy;
    private final Supplier<String> copySuffixSupplier;
    private final Supplier<LocalDateTime> nowSupplier;

    public ExecutionAdapterTemplateCatalogApplicationService(
            IExecutionAdapterTemplateRepository repository,
            ExecutionAdapterGeneratedTargetQueryPort generatedTargetQueryPort) {
        this(repository, generatedTargetQueryPort, new ExecutionAdapterTemplatePolicy(),
                () -> UUID.randomUUID().toString().replace("-", "").substring(0, 8),
                LocalDateTime::now);
    }

    ExecutionAdapterTemplateCatalogApplicationService(
            IExecutionAdapterTemplateRepository repository,
            ExecutionAdapterGeneratedTargetQueryPort generatedTargetQueryPort,
            ExecutionAdapterTemplatePolicy policy,
            Supplier<String> copySuffixSupplier) {
        this(repository, generatedTargetQueryPort, policy, copySuffixSupplier, LocalDateTime::now);
    }

    ExecutionAdapterTemplateCatalogApplicationService(
            IExecutionAdapterTemplateRepository repository,
            ExecutionAdapterGeneratedTargetQueryPort generatedTargetQueryPort,
            ExecutionAdapterTemplatePolicy policy,
            Supplier<String> copySuffixSupplier,
            Supplier<LocalDateTime> nowSupplier) {
        if (repository == null) throw new IllegalArgumentException("EXECUTION_TEMPLATE_REPOSITORY_REQUIRED");
        if (generatedTargetQueryPort == null) {
            throw new IllegalArgumentException("EXECUTION_TEMPLATE_TARGET_QUERY_PORT_REQUIRED");
        }
        if (policy == null) throw new IllegalArgumentException("EXECUTION_TEMPLATE_POLICY_REQUIRED");
        if (copySuffixSupplier == null) throw new IllegalArgumentException("EXECUTION_TEMPLATE_COPY_SUFFIX_REQUIRED");
        if (nowSupplier == null) throw new IllegalArgumentException("EXECUTION_TEMPLATE_CLOCK_REQUIRED");
        this.repository = repository;
        this.generatedTargetQueryPort = generatedTargetQueryPort;
        this.policy = policy;
        this.copySuffixSupplier = copySuffixSupplier;
        this.nowSupplier = nowSupplier;
    }

    public List<Map<String, Object>> list() {
        return listTemplates().stream().map(this::view).toList();
    }

    public List<ExecutionAdapterTemplate> listTemplates() {
        List<ExecutionAdapterTemplate> templates = repository.list();
        return templates == null ? List.of() : List.copyOf(templates);
    }

    public Map<String, Object> get(String templateId) {
        return view(getTemplate(templateId));
    }

    public ExecutionAdapterTemplate getTemplate(String templateId) {
        return find(policy.requiredId(templateId));
    }

    public Map<String, Object> create(ExecutionAdapterTemplateCommands.Mutation command) {
        return view(createTemplate(command));
    }

    public ExecutionAdapterTemplate createTemplate(
            ExecutionAdapterTemplateCommands.Mutation command) {
        require(command);
        LocalDateTime now = nowSupplier.get();
        String templateId = policy.requiredId(required(command.templateId().value(), "EXECUTION_TEMPLATE_ID_REQUIRED"));
        if (repository.exists(templateId)) {
            throw new IllegalArgumentException("执行适配器模板已存在：" + templateId);
        }
        ExecutionAdapterTemplate candidate = new ExecutionAdapterTemplate(
                0L,
                templateId,
                text(command.templateName().value(), templateId),
                command.adapterType().value(),
                command.supportedActions().orElse(List.of()),
                command.defaultConfig().orElse(Map.of()),
                command.riskLevel().orElse(ExecutionRiskLevel.HIGH),
                command.readOnly().orElse(false),
                text(command.description().value(), ""),
                command.status().orElse(ExecutionResourceStatus.ENABLED),
                command.actor(),
                now,
                now);
        return repository.insert(policy.create(candidate));
    }

    public Map<String, Object> update(String templateId,
                                      ExecutionAdapterTemplateCommands.Mutation command) {
        return view(updateTemplate(templateId, command));
    }

    public ExecutionAdapterTemplate updateTemplate(
            String templateId,
            ExecutionAdapterTemplateCommands.Mutation command) {
        require(command);
        String id = policy.requiredId(templateId);
        ExecutionAdapterTemplate current = find(id);
        String requestedId = command.templateId().orElse(id);
        ExecutionAdapterTemplate candidate = new ExecutionAdapterTemplate(
                current.catalogId(),
                requestedId,
                command.templateName().orElse(current.templateName()),
                command.adapterType().orElse(current.adapterType()),
                command.supportedActions().orElse(current.supportedActions()),
                command.defaultConfig().orElse(current.defaultConfig()),
                command.riskLevel().orElse(current.riskLevel()),
                command.readOnly().orElse(current.readOnly()),
                command.description().orElse(current.description()),
                command.status().orElse(current.status()),
                current.createBy(),
                current.createdAt(),
                nowSupplier.get());
        return repository.update(policy.update(current, candidate));
    }

    public Map<String, Object> updateStatus(String templateId, ExecutionResourceStatus status) {
        return view(updateTemplateStatus(templateId, status));
    }

    public ExecutionAdapterTemplate updateTemplateStatus(
            String templateId,
            ExecutionResourceStatus status) {
        String id = policy.requiredId(templateId);
        find(id);
        ExecutionResourceStatus next = status == null ? ExecutionResourceStatus.DISABLED : status;
        return repository.updateStatus(id, next);
    }

    public Map<String, Object> copy(String templateId,
                                    ExecutionAdapterTemplateCommands.Mutation command) {
        return view(copyTemplate(templateId, command));
    }

    public ExecutionAdapterTemplate copyTemplate(
            String templateId,
            ExecutionAdapterTemplateCommands.Mutation command) {
        require(command);
        ExecutionAdapterTemplate source = find(policy.requiredId(templateId));
        String targetId = text(command.templateId().value(), "");
        if (targetId.isBlank()) targetId = source.templateId() + "-copy-" + copySuffixSupplier.get();
        targetId = policy.requiredId(targetId);
        if (repository.exists(targetId)) {
            throw new IllegalArgumentException("执行适配器模板已存在：" + targetId);
        }
        LocalDateTime now = nowSupplier.get();
        ExecutionAdapterTemplate candidate = new ExecutionAdapterTemplate(
                0L,
                targetId,
                command.templateName().orElse(source.templateName() + " Copy"),
                command.adapterType().orElse(source.adapterType()),
                command.supportedActions().orElse(source.supportedActions()),
                command.defaultConfig().orElse(source.defaultConfig()),
                command.riskLevel().orElse(source.riskLevel()),
                command.readOnly().orElse(source.readOnly()),
                command.description().orElse(source.description()),
                command.status().orElse(source.status()),
                command.actor(),
                now,
                now);
        return repository.insert(policy.create(candidate));
    }

    public List<Map<String, Object>> generatedTargets(String templateId) {
        String id = policy.requiredId(templateId);
        find(id);
        return targetViews(id);
    }

    private ExecutionAdapterTemplate find(String templateId) {
        return repository.find(templateId)
                .orElseThrow(() -> new IllegalArgumentException("执行适配器模板不存在：" + templateId));
    }

    public Map<String, Object> view(ExecutionAdapterTemplate template) {
        return ExecutionAdapterTemplateView.of(
                template,
                targetViews(template.templateId()));
    }

    private List<Map<String, Object>> targetViews(String templateId) {
        List<ExecutionAdapterGeneratedTarget> targets = generatedTargetQueryPort.listByTemplate(templateId);
        if (targets == null || targets.isEmpty()) return List.of();
        return targets.stream().map(this::targetView).toList();
    }

    private Map<String, Object> targetView(ExecutionAdapterGeneratedTarget target) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("projectId", target.projectId());
        item.put("executionTargetId", target.executionTargetId());
        item.put("resourceId", target.executionTargetId());
        item.put("targetName", target.targetName());
        item.put("adapterTemplateId", target.adapterTemplateId());
        item.put("adapterType", target.adapterType());
        item.put("workerId", target.workerId());
        item.put("environments", target.environments());
        item.put("status", target.status());
        item.put("updatedAt", target.updatedAt());
        return Map.copyOf(item);
    }

    private void require(ExecutionAdapterTemplateCommands.Mutation command) {
        if (command == null) throw new IllegalArgumentException("EXECUTION_TEMPLATE_COMMAND_REQUIRED");
    }

    private String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private String text(String value, String fallback) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    private String time(LocalDateTime value) {
        return value == null ? "" : value.toString();
    }
}
