package cn.lgs.orbisops.application.toolset;

import java.util.List;

/** Application entry point for custom Toolset persistence operations. */
public class CustomToolsetStoreApplicationService {

    private final CustomToolsetStorePort storePort;

    public CustomToolsetStoreApplicationService(CustomToolsetStorePort storePort) {
        if (storePort == null) {
            throw new IllegalArgumentException("CUSTOM_TOOLSET_STORE_PORT_REQUIRED");
        }
        this.storePort = storePort;
    }

    public List<CustomToolsetRecord> list(String projectId) {
        return storePort.list(required(projectId, "TOOLSET_PROJECT_ID_REQUIRED"));
    }

    public CustomToolsetRecord upsert(CustomToolsetRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("CUSTOM_TOOLSET_RECORD_REQUIRED");
        }
        return storePort.upsert(record);
    }

    public void setEnabled(
            String projectId,
            String toolsetId,
            boolean enabled,
            String actor) {
        storePort.setEnabled(
                required(projectId, "TOOLSET_PROJECT_ID_REQUIRED"),
                required(toolsetId, "TOOLSET_ID_REQUIRED"),
                enabled,
                required(actor, "TOOLSET_ACTOR_REQUIRED"));
    }

    private String required(String value, String error) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(error);
        return normalized;
    }
}
