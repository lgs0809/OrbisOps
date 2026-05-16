package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpsAgentScopeOutcomeEnvelopeTest {

    private final OpsAgentScopeOutcomeEnvelope envelope = new OpsAgentScopeOutcomeEnvelope();

    @Test
    void shouldParseAndStripCompleteOutcomeEnvelope() {
        String output = "故障仍需继续处理。\n<ops_outcome>{\"requiresAction\":true,\"verificationStatus\":\"not_applicable\",\"abstained\":false,\"evidenceCompleteness\":\"partial\"}</ops_outcome>";

        OpsAgentScopeOutcomeEnvelope.Parsed parsed = envelope.parse(output);

        assertThat(parsed.present()).isTrue();
        assertThat(parsed.visibleOutput()).isEqualTo("故障仍需继续处理。");
        assertThat(parsed.outcome()).containsEntry("requiresAction", true)
                .containsEntry("verificationStatus", "NOT_APPLICABLE")
                .containsEntry("abstained", false)
                .containsEntry("evidenceCompleteness", "PARTIAL");
    }

    @Test
    void shouldParseKeyValueEnvelopeUsedByPromptTemplateSafeContract() {
        String output = "当前 Chat 不会绕过审批直接执行生产操作。\n<ops_outcome>\nrequiresAction=false\nverificationStatus=NOT_APPLICABLE\nabstained=true\nevidenceCompleteness=INSUFFICIENT\n</ops_outcome>";

        OpsAgentScopeOutcomeEnvelope.Parsed parsed = envelope.parse(output);

        assertThat(parsed.present()).isTrue();
        assertThat(parsed.visibleOutput()).isEqualTo("当前 Chat 不会绕过审批直接执行生产操作。");
        assertThat(parsed.outcome()).containsEntry("requiresAction", false)
                .containsEntry("verificationStatus", "NOT_APPLICABLE")
                .containsEntry("abstained", true)
                .containsEntry("evidenceCompleteness", "INSUFFICIENT");
    }

    @Test
    void verificationAndEvidenceRemainIndependentResultFacts() {
        String output = "无法确认当前是否已经恢复。\n<ops_outcome>\nrequiresAction=false\nverificationStatus=INSUFFICIENT\nabstained=false\nevidenceCompleteness=INSUFFICIENT\n</ops_outcome>";

        OpsAgentScopeOutcomeEnvelope.Parsed parsed = envelope.parse(output);

        assertThat(parsed.present()).isTrue();
        assertThat(parsed.outcome()).containsEntry("requiresAction", false)
                .containsEntry("verificationStatus", "INSUFFICIENT")
                .containsEntry("evidenceCompleteness", "INSUFFICIENT");
    }

    @Test
    void shouldParseStrictTrailingKeyValueBlockWhenModelDropsEnvelopeTags() {
        String output = "知识库没有命中正式 SOP。\n\nrequiresAction=false\nverificationStatus=NOT_APPLICABLE\nabstained=false\nevidenceCompleteness=INSUFFICIENT";

        OpsAgentScopeOutcomeEnvelope.Parsed parsed = envelope.parse(output);

        assertThat(parsed.present()).isTrue();
        assertThat(parsed.visibleOutput()).isEqualTo("知识库没有命中正式 SOP。");
        assertThat(parsed.outcome()).containsEntry("requiresAction", false)
                .containsEntry("verificationStatus", "NOT_APPLICABLE")
                .containsEntry("abstained", false)
                .containsEntry("evidenceCompleteness", "INSUFFICIENT");
    }

    @Test
    void shouldParseTrailingFieldsWhenModelEmitsOnlyClosingOutcomeMarkers() {
        String output = "我是 OrbisOps 的智能运维助手，负责协助你分析系统运行状态、定位问题并提供安全的运维建议。"
                + "</ops_outcome>\nrequiresAction=false\nverificationStatus=NOT_APPLICABLE\n"
                + "abstained=false\nevidenceCompleteness=NOT_APPLICABLE\n</ops_outcome>";

        OpsAgentScopeOutcomeEnvelope.Parsed parsed = envelope.parse(output);

        assertThat(parsed.present()).isTrue();
        assertThat(parsed.visibleOutput()).isEqualTo("我是 OrbisOps 的智能运维助手，负责协助你分析系统运行状态、定位问题并提供安全的运维建议。");
        assertThat(parsed.outcome()).containsEntry("requiresAction", false)
                .containsEntry("verificationStatus", "NOT_APPLICABLE")
                .containsEntry("abstained", false)
                .containsEntry("evidenceCompleteness", "NOT_APPLICABLE");
    }

    @Test
    void incompleteTrailingBlockMustRemainVisibleAndUntrusted() {
        String output = "正文\nrequiresAction=false";

        OpsAgentScopeOutcomeEnvelope.Parsed parsed = envelope.parse(output);

        assertThat(parsed.present()).isFalse();
        assertThat(parsed.outcome()).isEmpty();
        assertThat(parsed.visibleOutput()).isEqualTo(output);
    }

    @Test
    void ordinaryConversationUsesNotApplicableEvidenceFacts() {
        String output = "<ops_answer>我是 OrbisOps 的智能运维助手。\n<ops_outcome>\nrequiresAction=false\nverificationStatus=NOT_APPLICABLE\nabstained=false\nevidenceCompleteness=NOT_APPLICABLE\n</ops_outcome>";

        OpsAgentScopeOutcomeEnvelope.Parsed parsed = envelope.parse(output);

        assertThat(parsed.present()).isTrue();
        assertThat(parsed.visibleOutput()).isEqualTo("我是 OrbisOps 的智能运维助手。");
        assertThat(parsed.outcome()).containsEntry("requiresAction", false)
                .containsEntry("verificationStatus", "NOT_APPLICABLE")
                .containsEntry("abstained", false)
                .containsEntry("evidenceCompleteness", "NOT_APPLICABLE");
    }

    @Test
    void visibleAnswerMarkersAreNeverReturnedToUserWhenOutcomeIsMissing() {
        OpsAgentScopeOutcomeEnvelope.Parsed parsed = envelope.parse("<ops_answer>兼容回答</ops_answer>");

        assertThat(parsed.present()).isFalse();
        assertThat(parsed.visibleOutput()).isEqualTo("兼容回答");
    }

    @Test
    void incompleteEnvelopeMustRemainVisibleAndUntrusted() {
        String output = "正文\n<ops_outcome>{\"requiresAction\":true}</ops_outcome>";

        OpsAgentScopeOutcomeEnvelope.Parsed parsed = envelope.parse(output);

        assertThat(parsed.present()).isFalse();
        assertThat(parsed.outcome()).isEmpty();
        assertThat(parsed.visibleOutput()).isEqualTo(output);
    }
}
