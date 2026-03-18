package cn.lgs.orbisops.trigger.application.evidence;

import cn.lgs.orbisops.application.evidence.EvidenceStoreReadiness;
import cn.lgs.orbisops.domain.evidence.model.TrustedProof;
import cn.lgs.orbisops.domain.evidence.model.TrustedProofCriteria;
import cn.lgs.orbisops.domain.evidence.model.TrustedProofDraft;
import cn.lgs.orbisops.domain.evidence.model.TrustedProofSource;
import cn.lgs.orbisops.domain.evidence.model.TrustedProofStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class OpsTrustedProofMapper {

    @SuppressWarnings("unchecked")
    public TrustedProofDraft draft(Map<String, Object> request, String actor) {
        Map<String, Object> safe = request == null ? Map.of() : request;
        Object metadata = safe.get("metadata");
        return new TrustedProofDraft(
                text(safe.get("proofId")),
                text(safe.get("projectId")),
                text(safe.get("packageId")),
                integer(safe.get("packageVersion"), 0),
                text(safe.get("packageHash")),
                text(safe.get("proofType")),
                TrustedProofSource.require(text(safe.get("source"))),
                text(safe.get("externalRunId")),
                text(safe.get("commandHash")),
                text(safe.get("scriptHash")),
                TrustedProofStatus.require(firstText(safe.get("resultStatus"), safe.get("status"))),
                defaultText(safe.get("riskLevel"), "MEDIUM"),
                metadata instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of(),
                actor);
    }

    public TrustedProofCriteria criteria(
            String projectId,
            String packageId,
            int packageVersion,
            String packageHash,
            String riskLevel,
            String proofType,
            String externalRunIdOrProofId) {
        return new TrustedProofCriteria(
                projectId, packageId, packageVersion, packageHash,
                riskLevel, proofType, externalRunIdOrProofId);
    }

    public Map<String, Object> view(TrustedProof proof) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("proofId", proof.proofId());
        data.put("projectId", proof.projectId());
        data.put("packageId", proof.packageId());
        data.put("packageVersion", proof.packageVersion());
        data.put("packageHash", proof.packageHash());
        data.put("proofType", proof.proofType());
        data.put("source", proof.source().name());
        data.put("externalRunId", proof.externalRunId());
        data.put("commandHash", proof.commandHash());
        data.put("scriptHash", proof.scriptHash());
        data.put("resultStatus", proof.resultStatus().name());
        data.put("riskLevel", proof.riskLevel());
        data.put("metadata", proof.metadata());
        data.put("createdBy", proof.createdBy());
        data.put("createdAt", proof.createdAt());
        return data;
    }

    public Map<String, Object> view(EvidenceStoreReadiness readiness) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("store", readiness.store());
        data.put("autoInit", readiness.autoInit());
        data.put("memoryFallbackAllowed", readiness.memoryFallbackAllowed());
        data.put("status", readiness.status());
        if (!readiness.reason().isBlank()) data.put("reason", readiness.reason());
        data.put("recordCount", readiness.recordCount());
        return data;
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try { return Integer.parseInt(text(value)); }
        catch (Exception ignored) { return fallback; }
    }

    private String firstText(Object first, Object second) {
        String value = text(first);
        return value.isBlank() ? text(second) : value;
    }

    private String defaultText(Object value, String fallback) {
        String normalized = text(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
