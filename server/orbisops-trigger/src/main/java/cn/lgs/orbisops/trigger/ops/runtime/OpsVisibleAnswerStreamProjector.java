package cn.lgs.orbisops.trigger.ops.runtime;

import java.util.ArrayList;
import java.util.List;

/**
 * Projects only the explicitly marked final user-visible answer from the full ReAct model stream.
 * Intermediate model rounds are discarded; split answer/outcome markers are withheld across transport chunks.
 */
final class OpsVisibleAnswerStreamProjector {

    private static final String VISIBLE_START = OpsAgentScopeOutcomeEnvelope.VISIBLE_START;
    private static final String VISIBLE_END = OpsAgentScopeOutcomeEnvelope.VISIBLE_END;
    private static final String OUTCOME_START = OpsAgentScopeOutcomeEnvelope.START;
    private static final String OUTCOME_END = OpsAgentScopeOutcomeEnvelope.END;
    private static final List<String> OUTCOME_KEY_PREFIXES = List.of(
            "requiresAction=", "verificationStatus=", "abstained=", "evidenceCompleteness=");

    private final StringBuilder pending = new StringBuilder();
    private boolean visibleStarted;
    private boolean outcomeStarted;

    List<String> accept(String chunk) {
        if (outcomeStarted || chunk == null || chunk.isEmpty()) return List.of();
        pending.append(chunk);

        if (!visibleStarted) {
            int visibleMarker = pending.indexOf(VISIBLE_START);
            if (visibleMarker < 0) {
                retainMarkerSuffix(VISIBLE_START);
                return List.of();
            }
            pending.delete(0, visibleMarker + VISIBLE_START.length());
            visibleStarted = true;
        }

        return drainVisible();
    }

    private List<String> drainVisible() {
        int outcomeMarker = pending.indexOf(OUTCOME_START);
        int outcomeEndMarker = pending.indexOf(OUTCOME_END);
        int visibleEndMarker = pending.indexOf(VISIBLE_END);
        int marker = firstMarker(
                firstMarker(outcomeMarker, outcomeEndMarker),
                firstMarker(visibleEndMarker, firstOutcomeKeyMarker()));
        if (marker >= 0) {
            String visible = pending.substring(0, marker);
            pending.setLength(0);
            outcomeStarted = true;
            return visible.isEmpty() ? List.of() : List.of(visible);
        }
        int keep = Math.max(
                Math.max(markerSuffixLength(OUTCOME_START), markerSuffixLength(OUTCOME_END)),
                Math.max(markerSuffixLength(VISIBLE_END), maxOutcomeKeyPrefixSuffixLength()));
        if (pending.length() <= keep) return List.of();
        int emitLength = pending.length() - keep;
        String visible = pending.substring(0, emitLength);
        pending.delete(0, emitLength);
        return visible.isEmpty() ? List.of() : List.of(visible);
    }

    private int firstMarker(int left, int right) {
        if (left < 0) return right;
        if (right < 0) return left;
        return Math.min(left, right);
    }

    private void retainMarkerSuffix(String marker) {
        int keep = markerSuffixLength(marker);
        if (pending.length() > keep) {
            pending.delete(0, pending.length() - keep);
        }
    }

    private int firstOutcomeKeyMarker() {
        int first = -1;
        for (String prefix : OUTCOME_KEY_PREFIXES) {
            int offset = pending.indexOf(prefix);
            while (offset >= 0) {
                if (offset == 0 || pending.charAt(offset - 1) == '\n' || pending.charAt(offset - 1) == '\r') {
                    first = first < 0 ? offset : Math.min(first, offset);
                    break;
                }
                offset = pending.indexOf(prefix, offset + 1);
            }
        }
        return first;
    }

    private int maxOutcomeKeyPrefixSuffixLength() {
        return OUTCOME_KEY_PREFIXES.stream().mapToInt(this::markerSuffixLength).max().orElse(0);
    }

    private int markerSuffixLength(String marker) {
        return Math.max(0, marker.length() - 1);
    }

    List<String> finish() {
        if (!visibleStarted || outcomeStarted || pending.isEmpty()) {
            pending.setLength(0);
            return List.of();
        }
        List<String> result = new ArrayList<>(1);
        result.add(pending.toString());
        pending.setLength(0);
        return List.copyOf(result);
    }
}
