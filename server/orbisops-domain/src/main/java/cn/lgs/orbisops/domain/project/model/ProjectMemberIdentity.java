package cn.lgs.orbisops.domain.project.model;

import java.util.LinkedHashSet;
import java.util.List;

public record ProjectMemberIdentity(String userId, String username) {

    public ProjectMemberIdentity {
        userId = value(userId);
        username = value(username);
    }

    public List<String> keys() {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (!userId.isBlank()) {
            keys.add(userId);
        }
        if (!username.isBlank()) {
            keys.add(username);
        }
        return List.copyOf(keys);
    }

    public boolean empty() {
        return keys().isEmpty();
    }

    public boolean matchesOwner(String owner) {
        String normalized = value(owner);
        return !normalized.isBlank()
                && ((!username.isBlank() && normalized.equalsIgnoreCase(username))
                || (!userId.isBlank() && normalized.equalsIgnoreCase(userId)));
    }

    private static String value(String input) {
        return input == null ? "" : input.trim();
    }
}
