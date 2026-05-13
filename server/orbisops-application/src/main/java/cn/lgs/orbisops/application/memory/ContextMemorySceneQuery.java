package cn.lgs.orbisops.application.memory;

/** Typed request for scene-aware Context Memory selection. */
public record ContextMemorySceneQuery(
        String scene,
        String userId,
        String projectId,
        int limit) {

    public ContextMemorySceneQuery {
        scene = value(scene);
        userId = value(userId);
        projectId = value(projectId);
        limit = Math.max(1, Math.min(limit, 12));
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
