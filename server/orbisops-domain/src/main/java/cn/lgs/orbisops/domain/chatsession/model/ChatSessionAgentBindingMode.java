package cn.lgs.orbisops.domain.chatsession.model;

import java.util.Locale;
import java.util.Map;

/** Supported version binding modes for durable Chat Sessions. */
public enum ChatSessionAgentBindingMode {
    LATEST_PUBLISHED,
    PINNED_VERSION;

    public static ChatSessionAgentBindingMode resolve(String explicitMode,
                                                      Map<String, Object> metadata,
                                                      Integer requestedVersion) {
        String candidate = text(explicitMode);
        if (candidate.isBlank() && metadata != null) {
            candidate = text(metadata.get("agentBindingMode"));
        }
        if (candidate.isBlank()) {
            candidate = requestedVersion != null && requestedVersion > 0
                    ? PINNED_VERSION.name()
                    : LATEST_PUBLISHED.name();
        }
        ChatSessionAgentBindingMode mode;
        try {
            mode = valueOf(candidate.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(
                    "Agent 绑定模式只允许 LATEST_PUBLISHED 或 PINNED_VERSION", error);
        }
        if (mode == PINNED_VERSION && (requestedVersion == null || requestedVersion <= 0)) {
            throw new IllegalArgumentException("PINNED_VERSION 会话必须选择 Agent 版本");
        }
        return mode;
    }

    public Integer selectedVersion(Integer requestedVersion) {
        return this == PINNED_VERSION ? requestedVersion : null;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
