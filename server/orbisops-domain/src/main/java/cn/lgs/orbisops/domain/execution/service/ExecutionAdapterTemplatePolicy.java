package cn.lgs.orbisops.domain.execution.service;

import cn.lgs.orbisops.domain.execution.model.ExecutionAdapterTemplate;

import java.util.regex.Pattern;

public final class ExecutionAdapterTemplatePolicy {

    private static final Pattern ID = Pattern.compile("[A-Za-z0-9._:-]{1,120}");

    public ExecutionAdapterTemplate create(ExecutionAdapterTemplate candidate) {
        if (candidate == null) throw new IllegalArgumentException("EXECUTION_TEMPLATE_REQUIRED");
        requiredId(candidate.templateId());
        return candidate;
    }

    public ExecutionAdapterTemplate update(ExecutionAdapterTemplate current,
                                           ExecutionAdapterTemplate candidate) {
        if (current == null) throw new IllegalArgumentException("EXECUTION_TEMPLATE_CURRENT_REQUIRED");
        if (candidate == null) throw new IllegalArgumentException("EXECUTION_TEMPLATE_REQUIRED");
        if (!current.templateId().equals(candidate.templateId())) {
            throw new IllegalArgumentException("EXECUTION_TEMPLATE_ID_IMMUTABLE");
        }
        requiredId(candidate.templateId());
        return candidate;
    }

    public String requiredId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException("EXECUTION_TEMPLATE_ID_INVALID");
        }
        return normalized;
    }
}
