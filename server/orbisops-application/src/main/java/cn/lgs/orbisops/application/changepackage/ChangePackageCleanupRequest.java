package cn.lgs.orbisops.application.changepackage;

import java.util.Map;

public record ChangePackageCleanupRequest(String repairWorkspaceId) {

    public ChangePackageCleanupRequest {
        repairWorkspaceId = text(repairWorkspaceId);
    }

    public static ChangePackageCleanupRequest from(Map<String, ?> source) {
        Map<String, ?> safe = source == null ? Map.of() : source;
        return new ChangePackageCleanupRequest(text(safe.get("repairWorkspaceId")));
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
