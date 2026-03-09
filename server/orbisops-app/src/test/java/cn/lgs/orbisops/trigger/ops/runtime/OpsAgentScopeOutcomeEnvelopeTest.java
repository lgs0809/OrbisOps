package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpsAgentScopeOutcomeEnvelopeTest {

    private final OpsAgentScopeOutcomeEnvelope envelope = new OpsAgentScopeOutcomeEnvelope();

    @Test
    void shouldParseAndStripCompleteOutcomeEnvelope() {
        String output = "故障仍需受控修复。\n<ops_outcome>{\"actionClassification\":\"remediate\",\"requiresAction\":true,\"verificationStatus\":\"not_applicable\",\"abstained\":false,\"evidenceCompleteness\":\"partial\"}</ops_outcome>";

        OpsAgentScopeOutcomeEnvelope.Parsed parsed = envelope.parse(output);

        assertThat(parsed.present()).isTrue();
        assertThat(parsed.visibleOutput()).isEqualTo("故障仍需受控修复。");
        assertThat(parsed.outcome()).containsEntry("actionClassification", "REMEDIATE")
                .containsEntry("requiresAction", true)
                .containsEntry("verificationStatus", "NOT_APPLICABLE")
                .containsEntry("abstained", false)
                .containsEntry("evidenceCompleteness", "PARTIAL");
    }

    @Test
    void shouldParseKeyValueEnvelopeUsedByPromptTemplateSafeContract() {
        String output = "拒绝越权执行。\n<ops_outcome>\nactionClassification=ABSTAIN\nrequiresAction=false\nverificationStatus=NOT_APPLICABLE\nabstained=true\nevidenceCompleteness=INSUFFICIENT\n</ops_outcome>";

        OpsAgentScopeOutcomeEnvelope.Parsed parsed = envelope.parse(output);

        assertThat(parsed.present()).isTrue();
        assertThat(parsed.visibleOutput()).isEqualTo("拒绝越权执行。");
        assertThat(parsed.outcome()).containsEntry("actionClassification", "ABSTAIN")
                .containsEntry("requiresAction", false)
                .containsEntry("verificationStatus", "NOT_APPLICABLE")
                .containsEntry("abstained", true)
                .containsEntry("evidenceCompleteness", "INSUFFICIENT");
    }

    @Test
    void parserMustNotInferTaskClassificationFromVerificationStatus() {
        String output = "无法确认当前状态。\n<ops_outcome>\nactionClassification=DIAGNOSE\nrequiresAction=false\nverificationStatus=INSUFFICIENT\nabstained=false\nevidenceCompleteness=INSUFFICIENT\n</ops_outcome>";

        OpsAgentScopeOutcomeEnvelope.Parsed parsed = envelope.parse(output);

        assertThat(parsed.present()).isTrue();
        assertThat(parsed.outcome()).containsEntry("actionClassification", "DIAGNOSE")
                .containsEntry("requiresAction", false)
                .containsEntry("verificationStatus", "INSUFFICIENT");
    }

    @Test
    void remediationOutcomeKeepsRemediateEvenWhenVerificationIsInsufficient() {
        String output = "需要受控修复。\n<ops_outcome>\nactionClassification=REMEDIATE\nrequiresAction=true\nverificationStatus=INSUFFICIENT\nabstained=false\nevidenceCompleteness=PARTIAL\n</ops_outcome>";

        OpsAgentScopeOutcomeEnvelope.Parsed parsed = envelope.parse(output);

        assertThat(parsed.present()).isTrue();
        assertThat(parsed.outcome()).containsEntry("actionClassification", "REMEDIATE")
                .containsEntry("requiresAction", true)
                .containsEntry("verificationStatus", "INSUFFICIENT");
    }

    @Test
    void shouldParseStrictTrailingKeyValueBlockWhenModelDropsEnvelopeTags() {
        String output = "知识库没有命中正式 SOP。\n\nactionClassification=DIAGNOSE\nrequiresAction=false\nverificationStatus=NOT_APPLICABLE\nabstained=false\nevidenceCompleteness=INSUFFICIENT";

        OpsAgentScopeOutcomeEnvelope.Parsed parsed = envelope.parse(output);

        assertThat(parsed.present()).isTrue();
        assertThat(parsed.visibleOutput()).isEqualTo("知识库没有命中正式 SOP。");
        assertThat(parsed.outcome()).containsEntry("actionClassification", "DIAGNOSE")
                .containsEntry("requiresAction", false)
                .containsEntry("verificationStatus", "NOT_APPLICABLE")
                .containsEntry("abstained", false)
                .containsEntry("evidenceCompleteness", "INSUFFICIENT");
    }

    @Test
    void incompleteTrailingBlockMustRemainVisibleAndUntrusted() {
        String output = "正文\nactionClassification=DIAGNOSE\nrequiresAction=false";

        OpsAgentScopeOutcomeEnvelope.Parsed parsed = envelope.parse(output);

        assertThat(parsed.present()).isFalse();
        assertThat(parsed.outcome()).isEmpty();
        assertThat(parsed.visibleOutput()).isEqualTo(output);
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
