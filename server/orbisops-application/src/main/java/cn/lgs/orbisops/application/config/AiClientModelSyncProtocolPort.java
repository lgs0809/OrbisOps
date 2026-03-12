package cn.lgs.orbisops.application.config;

/** External `/models` protocol boundary. */
public interface AiClientModelSyncProtocolPort {

    String resolveEndpoint(AiClientModelSyncTarget target);

    AiClientModelSyncFetchResult fetch(AiClientModelSyncTarget target, String endpoint);
}
