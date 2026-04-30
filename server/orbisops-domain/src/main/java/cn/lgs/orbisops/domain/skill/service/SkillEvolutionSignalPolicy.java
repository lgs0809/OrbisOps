package cn.lgs.orbisops.domain.skill.service;

import cn.lgs.orbisops.domain.skill.model.SkillEvolutionSignalDraft;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Domain rules for Skill Evolution signal/hint identity, lifecycle and query bounds. */
public class SkillEvolutionSignalPolicy {

    private static final int MAX_PENDING_HINTS = 100;

    public SkillEvolutionSignalDraft draft(
            String signalType,
            String projectId,
            String agentId,
            String runId,
            String sessionId,
            String payloadJson) {
        return new SkillEvolutionSignalDraft(
                signalType,
                projectId,
                agentId,
                runId,
                sessionId,
                payloadJson);
    }

    public String signalIdempotencyKey(SkillEvolutionSignalDraft draft) {
        if (draft == null) throw new IllegalArgumentException("SKILL_SIGNAL_DRAFT_REQUIRED");
        return sha256(draft.projectId()
                + ":" + draft.runId()
                + ":" + draft.signalType()
                + ":" + draft.payloadJson());
    }

    public String hintId(String signalId, String hintType, String contentJson) {
        String digest = sha256(value(signalId) + ":" + value(hintType) + ":" + json(contentJson));
        return "skill-hint-" + digest.substring(0, 32);
    }

    public String createdStatus() {
        return "CREATED";
    }

    public int pendingHintLimit(int limit) {
        return Math.max(1, Math.min(limit, MAX_PENDING_HINTS));
    }

    public List<String> consumableHintIds(List<String> hintIds) {
        if (hintIds == null || hintIds.isEmpty()) return List.of();
        return hintIds.stream()
                .map(this::value)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    public String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("Skill signal hash 计算失败", error);
        }
    }

    private String json(String value) {
        String normalized = value(value);
        return normalized.isBlank() ? "{}" : normalized;
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
