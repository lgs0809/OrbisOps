package cn.lgs.orbisops.trigger.application.skill;

import cn.lgs.orbisops.application.skill.SkillEvolutionHintCommand;
import cn.lgs.orbisops.application.skill.SkillEvolutionSignalCommand;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionHintSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionSignalSnapshot;
import com.alibaba.fastjson.JSON;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Anti-corruption mapper for legacy Skill Evolution signal/hint Map contracts. */
@Component
public class OpsSkillEvolutionSignalMapper {

    public SkillEvolutionSignalCommand signalCommand(
            String signalType,
            String projectId,
            String agentId,
            String runId,
            String sessionId,
            Map<String, Object> payload) {
        return new SkillEvolutionSignalCommand(
                signalType,
                projectId,
                agentId,
                runId,
                sessionId,
                JSON.toJSONString(payload == null ? Map.of() : payload));
    }

    public SkillEvolutionHintCommand hintCommand(
            String signalId,
            String projectId,
            String runId,
            String hintType,
            Map<String, Object> content) {
        return new SkillEvolutionHintCommand(
                signalId,
                projectId,
                runId,
                hintType,
                JSON.toJSONString(content == null ? Map.of() : content));
    }

    public Map<String, Object> signalView(SkillEvolutionSignalSnapshot signal) {
        if (signal == null) return Map.of();
        return Map.of(
                "signalId", signal.signalId(),
                "signalType", signal.signalType(),
                "status", signal.status(),
                "idempotencyKey", signal.idempotencyKey());
    }

    public Map<String, Object> hintView(SkillEvolutionHintSnapshot hint) {
        if (hint == null) return Map.of();
        return Map.of(
                "hintId", hint.hintId(),
                "signalId", hint.signalId(),
                "status", hint.status());
    }

    public List<Map<String, Object>> pendingHintViews(List<SkillEvolutionHintSnapshot> hints) {
        if (hints == null || hints.isEmpty()) return List.of();
        return hints.stream().filter(hint -> hint != null).map(this::pendingHintView).toList();
    }

    private Map<String, Object> pendingHintView(SkillEvolutionHintSnapshot hint) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("hint_id", hint.hintId());
        row.put("signal_id", hint.signalId());
        row.put("project_id", hint.projectId());
        row.put("run_id", hint.runId());
        row.put("hint_type", hint.hintType());
        row.put("content_json", hint.contentJson());
        row.put("status", hint.status());
        row.put("created_at", hint.createdAt());
        try {
            row.put("content", StringUtils.hasText(hint.contentJson())
                    ? JSON.parseObject(hint.contentJson())
                    : Map.of());
        } catch (RuntimeException ignored) {
            row.put("content", Map.of());
        }
        return row;
    }
}
