package cn.lgs.orbisops.domain.execution.service;

import cn.lgs.orbisops.domain.execution.model.ExecutionAdapterTemplate;
import cn.lgs.orbisops.domain.execution.model.ExecutionTargetGenerationInput;
import cn.lgs.orbisops.domain.execution.model.ExecutionTargetSpecification;
import cn.lgs.orbisops.domain.execution.model.ExecutionRiskLevel;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ExecutionTargetGenerationPolicy {

    public ExecutionTargetSpecification generate(
            ExecutionAdapterTemplate template,
            ExecutionTargetGenerationInput input) {
        if (template == null) {
            throw new IllegalArgumentException("EXECUTION_TEMPLATE_REQUIRED");
        }
        if (input == null) {
            throw new IllegalArgumentException("EXECUTION_TARGET_GENERATION_INPUT_REQUIRED");
        }
        if (!template.templateId().equals(input.templateId())) {
            throw new IllegalArgumentException("EXECUTION_TEMPLATE_ID_MISMATCH");
        }

        Map<String, Object> configuration = new LinkedHashMap<>(template.defaultConfig());
        configuration.putAll(input.configuration());
        configuration.putAll(input.resolvedConfig());
        if (!input.allowedActions().isEmpty()) {
            configuration.put("allowedActions", input.allowedActions());
        }
        configuration.put("adapterTemplateId", template.templateId());
        configuration.put("templateRiskLevel", template.riskLevel().name());
        configuration.put(
                "approvalRequired",
                input.approvalRequired() == null
                        ? template.riskLevel() != ExecutionRiskLevel.LOW
                        : input.approvalRequired());

        return new ExecutionTargetSpecification(
                input.projectId(),
                input.targetId(),
                input.targetName(),
                input.workerId(),
                template.adapterType(),
                template.templateId(),
                input.environments(),
                configuration,
                input.status());
    }
}
