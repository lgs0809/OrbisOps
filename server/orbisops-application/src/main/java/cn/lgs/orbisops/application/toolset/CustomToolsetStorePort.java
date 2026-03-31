package cn.lgs.orbisops.application.toolset;

import java.util.List;

/** Infrastructure boundary for custom Toolset persistence and memory fallback. */
public interface CustomToolsetStorePort {

    List<CustomToolsetRecord> list(String projectId);

    CustomToolsetRecord upsert(CustomToolsetRecord record);

    void setEnabled(String projectId, String toolsetId, boolean enabled, String actor);
}
