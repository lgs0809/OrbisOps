package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.SkillBehaviorReplayArmRequest;
import cn.lgs.orbisops.application.skill.SkillBehaviorReplayPort;
import cn.lgs.orbisops.application.skill.SkillBehaviorToolCall;
import cn.lgs.orbisops.application.skill.SkillBehaviorToolExecutionPort;
import cn.lgs.orbisops.application.skill.SkillBehaviorToolExecutionRequest;
import cn.lgs.orbisops.application.skill.SkillBehaviorToolResult;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorMetrics;
import cn.lgs.orbisops.domain.skill.model.SkillBehaviorReplayResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Deterministic fixture replay adapter. Dynamic tools remain behind the supplied gated executor. */
@Component
public final class OpsSkillBehaviorReplayAdapter implements SkillBehaviorReplayPort {

    @Override
    public SkillBehaviorReplayResult replay(
            SkillBehaviorReplayArmRequest request,
            SkillBehaviorToolExecutionPort tools) {
        if (request == null || tools == null) {
            throw new IllegalArgumentException("SKILL_REPLAY_ADAPTER_INPUT_REQUIRED");
        }
        List<SkillBehaviorToolResult> toolResults = new ArrayList<>();
        List<SkillBehaviorToolCall> calls = toolCalls(request.fixture().get("toolCalls"));
        for (int index = 0; index < calls.size(); index++) {
            toolResults.add(tools.execute(new SkillBehaviorToolExecutionRequest(
                    request.replayId(), request.projectId(), request.actor(),
                    request.sessionId(), request.runId(), request.arm(), request.mode(),
                    index, calls.get(index))));
        }
        Map<String, Object> metrics = mapOrEmpty(request.fixture().get("metrics"));
        int invalidCalls = integer(metrics.get("invalidToolCallCount"), 0)
                + (int) toolResults.stream().filter(result -> !result.allowed()).count();
        SkillBehaviorMetrics behavior = new SkillBehaviorMetrics(
                decimal(metrics.get("successRate"), 0D),
                integer(metrics.get("safetyViolationCount"), 0),
                calls.size(),
                Math.min(calls.size(), invalidCalls),
                decimal(metrics.get("evidenceCompleteness"), 0D),
                decimal(metrics.get("hallucinationRate"), 0D),
                longValue(metrics.get("latencyMs"), 0L),
                longValue(metrics.get("tokenCount"), 0L),
                longValue(metrics.get("costMicros"), 0L),
                decimal(metrics.get("finalAnswerQuality"), 0D),
                decimal(metrics.get("routingAccuracy"), 0D));
        String finalAnswerHash = hashOrValue(
                request.fixture().get("finalAnswerHash"),
                request.fixture().get("finalAnswer"),
                request.arm().name() + ":answer");
        String evidenceHash = hashOrValue(
                request.fixture().get("evidenceHash"),
                request.fixture().get("evidence"),
                request.arm().name() + ":evidence");
        return new SkillBehaviorReplayResult(
                request.arm(), request.activeSkillHash(), behavior,
                finalAnswerHash, evidenceHash,
                toolResults.stream().map(SkillBehaviorToolResult::resultId).toList(),
                strings(request.fixture().get("reasonCodes")));
    }

    private List<SkillBehaviorToolCall> toolCalls(Object value) {
        if (!(value instanceof Iterable<?> iterable)) return List.of();
        List<SkillBehaviorToolCall> result = new ArrayList<>();
        for (Object item : iterable) {
            Map<String, Object> map = mapOrEmpty(item);
            result.add(new SkillBehaviorToolCall(
                    text(map.get("toolsetId")),
                    text(map.get("toolName")),
                    mapOrEmpty(map.get("arguments")),
                    bool(map.get("readOnly")),
                    bool(map.get("productionWrite")),
                    bool(map.get("changePackageProposal")),
                    bool(map.get("directLanding")),
                    text(map.get("frozenResultId"))));
        }
        return List.copyOf(result);
    }

    private String hashOrValue(Object hash, Object value, String fallback) {
        String supplied = text(hash).toLowerCase();
        if (supplied.matches("[a-f0-9]{64}")) return supplied;
        return CanonicalObjectHasher.sha256(value == null ? fallback : value);
    }

    private Map<String, Object> mapOrEmpty(Object value) {
        if (!(value instanceof Map<?, ?> map)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, entry) -> result.put(String.valueOf(key), entry));
        return Map.copyOf(result);
    }

    private List<String> strings(Object value) {
        if (!(value instanceof Iterable<?> iterable)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object item : iterable) {
            String text = text(item);
            if (!text.isBlank()) result.add(text);
        }
        return List.copyOf(result);
    }

    private double decimal(Object value, double fallback) {
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(text(value));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(text(value));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private long longValue(Object value, long fallback) {
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(text(value));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        return Boolean.parseBoolean(text(value));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
