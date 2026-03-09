package cn.lgs.orbisops.application.config;

/** Resolves one provider health target from the typed API catalog. */
public interface AiClientApiHealthTargetPort {

    AiClientApiHealthTarget find(String apiId);
}
