package cn.lgs.orbisops.trigger.ops.runtime;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/** Reads and mutates per-loop round counters stored in graph state. */
final class OpsGraphLoopRoundState {

    int round(OverAllState state, String loopId) {
        return roundMap(state).getOrDefault(loopId, 0);
    }

    void increment(OverAllState state, String loopId) {
        if (state == null || !StringUtils.hasText(loopId)) {
            return;
        }
        Map<String, Integer> rounds = roundMap(state);
        rounds.put(loopId, rounds.getOrDefault(loopId, 0) + 1);
        state.updateState(Map.of(
                OpsGraphRuntimeStateManager.LOOP_ROUNDS_KEY,
                rounds));
    }

    int legacyReviewRound(OverAllState state) {
        if (state == null) {
            return 0;
        }
        Object value = state.value("mainReviewRound").orElse(null);
        if (value instanceof Number number) {
            return Math.max(0, number.intValue());
        }
        try {
            String text = value == null ? "" : String.valueOf(value).trim();
            return StringUtils.hasText(text)
                    ? Math.max(0, Integer.parseInt(text))
                    : 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private Map<String, Integer> roundMap(OverAllState state) {
        Map<String, Integer> rounds = new LinkedHashMap<>();
        Object value = state == null
                ? null
                : state.value(OpsGraphRuntimeStateManager.LOOP_ROUNDS_KEY)
                .orElse(null);
        if (!(value instanceof Map<?, ?> map)) {
            return rounds;
        }
        map.forEach((key, round) -> {
            String loopId = String.valueOf(key);
            if (!StringUtils.hasText(loopId)) {
                return;
            }
            if (round instanceof Number number) {
                rounds.put(loopId, Math.max(0, number.intValue()));
                return;
            }
            try {
                rounds.put(
                        loopId,
                        Math.max(0, Integer.parseInt(
                                String.valueOf(round).trim())));
            } catch (NumberFormatException ignored) {
                rounds.put(loopId, 0);
            }
        });
        return rounds;
    }
}
