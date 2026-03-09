package cn.lgs.orbisops.domain.agentdefinition.model;

import java.util.Locale;

/** Product-level definition kind. Internal graph nodes do not change this classification. */
public enum AgentDefinitionKind {
    MAIN_ASSISTANT,
    SPECIALIZED_WORKFLOW;

    public static AgentDefinitionKind parse(String value) {
        if (value == null || value.isBlank()) return MAIN_ASSISTANT;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的 Agent 定义类型：" + value, exception);
        }
    }
}
