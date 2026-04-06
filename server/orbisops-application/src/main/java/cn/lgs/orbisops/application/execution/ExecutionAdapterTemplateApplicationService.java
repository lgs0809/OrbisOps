package cn.lgs.orbisops.application.execution;

import cn.lgs.orbisops.domain.execution.model.ExecutionAdapterTemplate;
import cn.lgs.orbisops.domain.execution.service.ExecutionAdapterTemplatePolicy;

import java.util.List;
import java.util.Map;

/** Governed template lifecycle use case over a typed template port. */
public final class ExecutionAdapterTemplateApplicationService<T> {

    private final ExecutionAdapterTemplatePort<T> port;
    private final ExecutionAuditPort auditPort;
    private final ExecutionAdapterTemplatePolicy policy;

    public ExecutionAdapterTemplateApplicationService(
            ExecutionAdapterTemplatePort<T> port,
            ExecutionAuditPort auditPort) {
        this(port, auditPort, new ExecutionAdapterTemplatePolicy());
    }

    ExecutionAdapterTemplateApplicationService(
            ExecutionAdapterTemplatePort<T> port,
            ExecutionAuditPort auditPort,
            ExecutionAdapterTemplatePolicy policy) {
        if (port == null) throw new IllegalArgumentException("EXECUTION_TEMPLATE_PORT_REQUIRED");
        if (auditPort == null) throw new IllegalArgumentException("EXECUTION_AUDIT_PORT_REQUIRED");
        if (policy == null) throw new IllegalArgumentException("EXECUTION_TEMPLATE_POLICY_REQUIRED");
        this.port = port;
        this.auditPort = auditPort;
        this.policy = policy;
    }

    public List<Map<String, Object>> list() {
        return port.listTemplates().stream().map(this::view).toList();
    }

    public Map<String, Object> get(String templateId) {
        return view(port.getTemplate(policy.requiredId(templateId)));
    }

    public List<Map<String, Object>> generatedTargets(String templateId) {
        String id = policy.requiredId(templateId);
        port.getTemplate(id);
        return port.generatedTargets(id);
    }

    public Map<String, Object> create(ExecutionAdapterTemplateCommands.Mutation command) {
        require(command);
        String id = policy.requiredId(command.templateId().value());
        ExecutionAdapterTemplate created = port.createTemplate(command);
        Map<String, Object> result = view(created);
        auditPort.record("", "execution-adapter-template", "create", id, null,
                Map.of("template", result, "actor", command.actor()));
        return result;
    }

    public Map<String, Object> update(
            String templateId,
            ExecutionAdapterTemplateCommands.Mutation command) {
        require(command);
        String id = policy.requiredId(templateId);
        ExecutionAdapterTemplate beforeTemplate = port.getTemplate(id);
        ExecutionAdapterTemplate updated = port.updateTemplate(id, command);
        Map<String, Object> before = view(beforeTemplate);
        Map<String, Object> result = view(updated);
        auditPort.record("", "execution-adapter-template", "update", id, before,
                Map.of("template", result, "actor", command.actor()));
        return result;
    }

    public Map<String, Object> updateStatus(
            String templateId,
            ExecutionAdapterTemplateCommands.StatusChange command) {
        if (command == null) throw new IllegalArgumentException("EXECUTION_TEMPLATE_STATUS_COMMAND_REQUIRED");
        String id = policy.requiredId(templateId);
        ExecutionAdapterTemplate beforeTemplate = port.getTemplate(id);
        ExecutionAdapterTemplate updated = port.updateTemplateStatus(id, command.status());
        Map<String, Object> before = view(beforeTemplate);
        Map<String, Object> result = view(updated);
        auditPort.record("", "execution-adapter-template", "status", id, before,
                Map.of("template", result, "actor", command.actor()));
        return result;
    }

    public Map<String, Object> copy(
            String templateId,
            ExecutionAdapterTemplateCommands.Mutation command) {
        require(command);
        String id = policy.requiredId(templateId);
        port.getTemplate(id);
        ExecutionAdapterTemplate copied = port.copyTemplate(id, command);
        Map<String, Object> result = view(copied);
        auditPort.record("", "execution-adapter-template", "copy", id,
                Map.of("sourceTemplateId", id),
                Map.of("template", result, "actor", command.actor()));
        return result;
    }

    public T generateTarget(ExecutionAdapterTemplateCommands.TargetGeneration command) {
        if (command == null) throw new IllegalArgumentException("EXECUTION_TARGET_GENERATION_COMMAND_REQUIRED");
        String id = policy.requiredId(command.templateId());
        port.getTemplate(id);
        T result = port.generateTarget(command.projectId(), id, command.request());
        auditPort.record(command.projectId(), "execution-target", "generate-from-template", port.targetId(result),
                Map.of("adapterTemplateId", id), Map.of("target", result, "actor", command.actor()));
        return result;
    }

    private Map<String, Object> view(ExecutionAdapterTemplate template) {
        return ExecutionAdapterTemplateView.of(
                template,
                port.generatedTargets(template.templateId()));
    }

    private void require(ExecutionAdapterTemplateCommands.Mutation command) {
        if (command == null) throw new IllegalArgumentException("EXECUTION_TEMPLATE_COMMAND_REQUIRED");
    }
}
