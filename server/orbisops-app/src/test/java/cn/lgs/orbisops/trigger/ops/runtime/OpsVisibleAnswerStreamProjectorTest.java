package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OpsVisibleAnswerStreamProjectorTest {

    @Test
    void streamsOnlyMarkedFinalTextAndWithholdsMachineOutcomeAcrossSplitChunks() {
        OpsVisibleAnswerStreamProjector projector = new OpsVisibleAnswerStreamProjector();
        List<String> deltas = new ArrayList<>();

        deltas.addAll(projector.accept("我先检查一下工具。<ops_ans"));
        deltas.addAll(projector.accept("wer>结论正在逐步输出。<ops_out"));
        deltas.addAll(projector.accept("come>\nrequiresAction=false\nverificationStatus=NOT_APPLICABLE\nabstained=false\nevidenceCompleteness=PARTIAL\n</ops_outcome>"));
        deltas.addAll(projector.finish());

        String visible = String.join("", deltas);
        assertEquals("结论正在逐步输出。", visible);
        assertFalse(visible.contains("我先检查一下工具"));
        assertFalse(visible.contains("ops_answer"));
        assertFalse(visible.contains("ops_outcome"));
        assertFalse(visible.contains("requiresAction"));
    }

    @Test
    void withholdsUnmarkedIntermediateModelTextEvenWhenStreamFinishes() {
        OpsVisibleAnswerStreamProjector projector = new OpsVisibleAnswerStreamProjector();
        List<String> deltas = new ArrayList<>(projector.accept("我先调用工具确认一下。"));
        deltas.addAll(projector.finish());

        assertEquals("", String.join("", deltas));
    }

    @Test
    void suppressesOptionalClosingAnswerMarkerAcrossSplitChunks() {
        OpsVisibleAnswerStreamProjector projector = new OpsVisibleAnswerStreamProjector();
        List<String> deltas = new ArrayList<>();
        deltas.addAll(projector.accept("<ops_answer>普通回答</ops_ans"));
        deltas.addAll(projector.accept("wer><ops_outcome>ignored"));
        deltas.addAll(projector.finish());

        assertEquals("普通回答", String.join("", deltas));
    }

    @Test
    void flushesMarkedFinalAnswerWhenOutcomeEnvelopeIsMissing() {
        OpsVisibleAnswerStreamProjector projector = new OpsVisibleAnswerStreamProjector();
        List<String> deltas = new ArrayList<>(projector.accept("<ops_answer>普通回答"));
        deltas.addAll(projector.finish());

        assertEquals("普通回答", String.join("", deltas));
    }

    @Test
    void stopsBeforeMalformedClosingOutcomeMarkerAndTrailingFields() {
        OpsVisibleAnswerStreamProjector projector = new OpsVisibleAnswerStreamProjector();
        List<String> deltas = new ArrayList<>(projector.accept(
                "<ops_answer>普通回答\n</ops_outcome>\nrequiresAction=false\n"
                        + "verificationStatus=NOT_APPLICABLE\nabstained=false\n"
                        + "evidenceCompleteness=NOT_APPLICABLE\n</ops_outcome>"));
        deltas.addAll(projector.finish());

        assertEquals("普通回答\n", String.join("", deltas));
    }

    @Test
    void stopsBeforeUnwrappedOutcomeFields() {
        OpsVisibleAnswerStreamProjector projector = new OpsVisibleAnswerStreamProjector();
        List<String> deltas = new ArrayList<>(projector.accept(
                "<ops_answer>普通回答\nrequiresAction=false\nverificationStatus=NOT_APPLICABLE\n"
                        + "abstained=false\nevidenceCompleteness=NOT_APPLICABLE"));
        deltas.addAll(projector.finish());

        assertEquals("普通回答\n", String.join("", deltas));
    }
}
