package cn.lgs.orbisops.application.incident;

import cn.lgs.orbisops.domain.incident.adapter.repository.IIncidentRepository;
import cn.lgs.orbisops.domain.incident.model.IncidentSnapshot;
import cn.lgs.orbisops.domain.incident.service.IncidentPolicy;

import java.util.LinkedHashMap;
import java.util.Map;

/** Owns recovery-verification facts. Callers can request verification, but cannot declare success. */
public final class IncidentVerificationApplicationService {

    private final IIncidentRepository incidents;
    private final IncidentVerificationPort verification;
    private final IncidentAuditPort audit;
    private final IncidentTransactionPort transactions;
    private final IncidentPolicy policy = new IncidentPolicy();

    public IncidentVerificationApplicationService(
            IIncidentRepository incidents,
            IncidentVerificationPort verification,
            IncidentAuditPort audit,
            IncidentTransactionPort transactions) {
        if (incidents == null) throw new IllegalArgumentException("INCIDENT_REPOSITORY_REQUIRED");
        if (verification == null) throw new IllegalArgumentException("INCIDENT_VERIFICATION_PORT_REQUIRED");
        if (audit == null) throw new IllegalArgumentException("INCIDENT_AUDIT_PORT_REQUIRED");
        if (transactions == null) throw new IllegalArgumentException("INCIDENT_TRANSACTION_PORT_REQUIRED");
        this.incidents = incidents;
        this.verification = verification;
        this.audit = audit;
        this.transactions = transactions;
    }

    public IncidentVerificationResult verify(String incidentId, String packageId, String actor) {
        String id = required(incidentId, "INCIDENT_ID_REQUIRED");
        String pkg = required(packageId, "CHANGE_PACKAGE_ID_REQUIRED");
        String operator = required(actor, "INCIDENT_ACTOR_REQUIRED");
        IncidentSnapshot incident = incidents.find(id)
                .orElseThrow(() -> new IllegalArgumentException("INCIDENT_NOT_FOUND:" + id));

        transactions.required(() -> {
            incidents.appendTimeline(policy.timeline(
                    id,
                    "VERIFICATION_STARTED",
                    "开始恢复验证",
                    "平台开始依据已审批验证条件和权威执行结果核验恢复状态。",
                    operator,
                    "CHANGE_PACKAGE",
                    pkg,
                    Map.of("packageId", pkg)));
            return Boolean.TRUE;
        });

        IncidentVerificationResult result;
        try {
            result = verification.verify(incident, pkg);
            if (result == null) {
                result = IncidentVerificationResult.insufficient(
                        "验证组件未返回权威结果。",
                        Map.of("reasonCode", "VERIFICATION_RESULT_MISSING"));
            }
        } catch (RuntimeException error) {
            result = IncidentVerificationResult.insufficient(
                    "恢复验证执行失败，不能据此宣告恢复。",
                    Map.of(
                            "reasonCode", "VERIFICATION_EXECUTION_FAILED",
                            "error", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
        }

        IncidentVerificationResult outcome = result;
        transactions.required(() -> {
            String eventType = switch (outcome.status()) {
                case PASSED -> "VERIFICATION_SUCCEEDED";
                case FAILED -> "VERIFICATION_FAILED";
                case INSUFFICIENT -> "VERIFICATION_INSUFFICIENT";
            };
            String title = switch (outcome.status()) {
                case PASSED -> "恢复验证通过";
                case FAILED -> "恢复验证失败";
                case INSUFFICIENT -> "恢复验证证据不足";
            };
            Map<String, Object> payload = new LinkedHashMap<>(outcome.evidence());
            payload.put("packageId", pkg);
            payload.put("verificationStatus", outcome.status().name());
            incidents.appendTimeline(policy.timeline(
                    id,
                    eventType,
                    title,
                    outcome.summary(),
                    operator,
                    "CHANGE_PACKAGE",
                    pkg,
                    payload));
            audit.record(new IncidentAuditEvent(
                    "verification",
                    id,
                    operator,
                    incident,
                    incident,
                    payload));
            return Boolean.TRUE;
        });
        return outcome;
    }

    private String required(String value, String reason) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reason);
        return normalized;
    }
}
