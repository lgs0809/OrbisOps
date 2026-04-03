package cn.lgs.orbisops.domain.runtime.llm.model;

/** Runtime behavior selected when an LLM capability is unavailable or fails. */
public enum LlmDegradationMode {
    FAIL_CLOSED,
    RULE_FALLBACK
}
