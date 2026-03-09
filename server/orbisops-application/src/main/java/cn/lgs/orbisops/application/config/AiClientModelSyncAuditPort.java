package cn.lgs.orbisops.application.config;

/** Single-operation audit boundary for Provider model synchronization. */
public interface AiClientModelSyncAuditPort {

    void succeeded(AiClientModelSyncResult result);

    void failed(AiClientModelSyncResult result);
}
