package cn.lgs.orbisops.application.config;

/** Provider target lookup boundary for model synchronization. */
public interface AiClientModelSyncTargetPort {

    AiClientModelSyncTarget find(String apiId);
}
