package cn.lgs.orbisops.trigger.application.channel;

/** Typed limits for validating inbound channel protocol payloads. */
public record OpsChannelInboundProtocolSettings(
        long maxClockSkewSeconds,
        int maxMessageChars,
        int maxMetadataChars) {

    public OpsChannelInboundProtocolSettings {
        maxClockSkewSeconds = Math.max(30, maxClockSkewSeconds);
        maxMessageChars = Math.max(1000, maxMessageChars);
        maxMetadataChars = Math.max(1024, maxMetadataChars);
    }

    public static OpsChannelInboundProtocolSettings defaults() {
        return new OpsChannelInboundProtocolSettings(300, 12000, 8192);
    }
}
