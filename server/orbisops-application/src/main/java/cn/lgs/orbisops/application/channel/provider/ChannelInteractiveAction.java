package cn.lgs.orbisops.application.channel.provider;

public record ChannelInteractiveAction(String actionId,
                                       String label,
                                       String opaqueActionToken,
                                       ActionStyle style) {
    public ChannelInteractiveAction {
        actionId = required(actionId, "CHANNEL_ACTION_ID_REQUIRED");
        label = required(label, "CHANNEL_ACTION_LABEL_REQUIRED");
        opaqueActionToken = required(opaqueActionToken, "CHANNEL_ACTION_TOKEN_REQUIRED");
        style = style == null ? ActionStyle.DEFAULT : style;
    }

    public enum ActionStyle {
        DEFAULT,
        PRIMARY,
        DANGER
    }

    private static String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }
}
