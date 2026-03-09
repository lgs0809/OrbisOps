package cn.lgs.orbisops.application.config;

/** Audits one provider health-check result. */
public interface AiClientApiHealthAuditPort {

    void record(AiClientApiHealthCheckResult result);
}
