package cn.lgs.orbisops.application.config;

/** Persists one provider health-check result. */
public interface AiClientApiHealthRecordPort {

    void save(AiClientApiHealthCheckResult result);
}
