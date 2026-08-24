package cn.lgs.orbisops.domain.chatsession.model;

/** Stable query criteria for Chat Session catalog reads. */
public record ChatSessionSearchCriteria(
        String userId,
        String agentId,
        String keyword,
        Boolean favorite,
        int limit,
        String projectId) {

    public ChatSessionSearchCriteria(String userId, String agentId, String keyword, Boolean favorite, int limit) {
        this(userId, agentId, keyword, favorite, limit, "");
    }

    public ChatSessionSearchCriteria {
        userId = value(userId);
        agentId = value(agentId);
        keyword = value(keyword).toLowerCase();
        projectId = value(projectId);
        limit = Math.max(1, Math.min(limit, 200));
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
