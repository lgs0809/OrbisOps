package cn.lgs.orbisops.application.channel.provider;

import java.util.List;

public record ChannelRichContent(String plainText,
                                 String markdown,
                                 List<ChannelInteractiveAction> actions) {
    public ChannelRichContent {
        plainText = normalize(plainText);
        markdown = normalize(markdown);
        actions = actions == null || actions.isEmpty() ? List.of() : List.copyOf(actions);
        if (plainText.isBlank() && markdown.isBlank() && actions.isEmpty()) {
            throw new IllegalArgumentException("CHANNEL_CONTENT_REQUIRED");
        }
    }

    public static ChannelRichContent text(String value) {
        return new ChannelRichContent(value, "", List.of());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
