package cn.lgs.orbisops.domain.evidence.service;

import cn.lgs.orbisops.domain.evidence.model.EvidenceDraft;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class EvidencePolicy {

    public String idempotencyKey(EvidenceDraft draft) {
        if (draft == null) throw new IllegalArgumentException("EVIDENCE_DRAFT_REQUIRED");
        return sha256(String.join(":",
                draft.projectId(), draft.runId(), draft.sourceType(), draft.sourceId(),
                draft.toolResultId(), draft.outputHash()));
    }

    public int listLimit(int limit) {
        return Math.max(1, Math.min(limit, 500));
    }

    public String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Evidence 幂等 hash 计算失败", e);
        }
    }
}
