package cn.lgs.orbisops.application.execution;

import cn.lgs.orbisops.domain.execution.model.ExecutionAdapterTemplate;
import cn.lgs.orbisops.domain.execution.model.ExecutionResourceStatus;

import java.util.List;
import java.util.Map;

/** Typed lifecycle boundary for execution-adapter templates. */
public interface ExecutionAdapterTemplatePort<T> {

    List<ExecutionAdapterTemplate> listTemplates();

    ExecutionAdapterTemplate getTemplate(String templateId);

    ExecutionAdapterTemplate createTemplate(ExecutionAdapterTemplateCommands.Mutation command);

    ExecutionAdapterTemplate updateTemplate(
            String templateId,
            ExecutionAdapterTemplateCommands.Mutation command);

    ExecutionAdapterTemplate updateTemplateStatus(
            String templateId,
            ExecutionResourceStatus status);

    ExecutionAdapterTemplate copyTemplate(
            String templateId,
            ExecutionAdapterTemplateCommands.Mutation command);

    List<Map<String, Object>> generatedTargets(String templateId);

    T generateTarget(String projectId, String templateId, Map<String, Object> request);

    String targetId(T target);
}
