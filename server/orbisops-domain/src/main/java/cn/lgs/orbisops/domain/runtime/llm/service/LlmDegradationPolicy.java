package cn.lgs.orbisops.domain.runtime.llm.service;

import cn.lgs.orbisops.domain.runtime.llm.model.LlmDegradationMode;
import cn.lgs.orbisops.domain.runtime.llm.model.LlmRuntimeSettings;

/** Selects fail-closed or deterministic rule fallback when runtime LLM access degrades. */
public final class LlmDegradationPolicy {

    public LlmDegradationMode decide(LlmRuntimeSettings settings) {
        if (settings == null || settings.failOnLlmDegradation()) {
            return LlmDegradationMode.FAIL_CLOSED;
        }
        return LlmDegradationMode.RULE_FALLBACK;
    }
}
