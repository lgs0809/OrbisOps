package cn.lgs.orbisops.application.channel;

/** Typed notification request emitted after one Agent analysis completes. */
public record ChannelAnalysisNotificationCommand(
        boolean requested,
        String projectId,
        String channelId,
        String target,
        String runId,
        String analysisId,
        String generatedAt,
        String markdownReport) {
}
