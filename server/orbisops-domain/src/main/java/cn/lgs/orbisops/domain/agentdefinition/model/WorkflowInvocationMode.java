package cn.lgs.orbisops.domain.agentdefinition.model;

import java.util.Locale;

/** Product invocation mode for user-facing specialized workflows. */
public enum WorkflowInvocationMode {
    MANUAL_ONLY;

    public static WorkflowInvocationMode parse(String value) {
        if (value == null || value.isBlank()) return MANUAL_ONLY;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的 Workflow 调用模式：" + value, exception);
        }
    }
}
