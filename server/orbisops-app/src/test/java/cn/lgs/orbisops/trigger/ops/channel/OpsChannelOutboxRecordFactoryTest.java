package cn.lgs.orbisops.trigger.ops.channel;

import cn.lgs.orbisops.domain.channel.model.ChannelOutboxRecord;
import cn.lgs.orbisops.domain.channel.service.ChannelOutboundContentPolicy;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsChannelOutboxRecordFactoryTest {

    @Test
    void analysisDraftMustProduceStableRecordMessageMetadataAndHash() {
        OpsChannelOutboxRecordFactory factory = factory(12_000);

        ChannelOutboxRecord record = factory.analysisNotification(
                new OpsChannelOutboxRecordFactory.AnalysisNotificationDraft(
                        "project-1", "channel-1", "room-1", "run-1",
                        "analysis-1", "2026-07-27T14:00:00", "# report"));

        assertTrue(record.dedupKey().startsWith("channel-notify:"));
        assertEquals("project-1", record.projectId());
        assertEquals("analysis-1", record.analysisId());
        assertEquals("PENDING", record.status());
        assertTrue(record.messageText().contains("分析 ID：analysis-1"));
        assertTrue(record.messageText().contains("# report"));
        assertEquals(OpsChannelSignature.contentHash(record.messageText()), record.contentHash());
        Map<String, Object> metadata = JSON.parseObject(record.metadataJson());
        assertEquals("run-1", metadata.get("runId"));
        assertEquals("analysis-1", metadata.get("analysisId"));
    }

    @Test
    void analysisMessageMustPreserveNullResponseAndLengthBehavior() {
        OpsChannelOutboxRecordFactory factory = factory(500);

        ChannelOutboxRecord empty = factory.analysisNotification(
                new OpsChannelOutboxRecordFactory.AnalysisNotificationDraft(
                        "project", "channel", "target", "", null, null, null));
        ChannelOutboxRecord truncated = factory.analysisNotification(
                new OpsChannelOutboxRecordFactory.AnalysisNotificationDraft(
                        "project", "channel", "target", "", "analysis", "time", "x".repeat(700)));

        assertEquals("# 运维 Agent 分析结果\n\n", empty.messageText());
        assertEquals(503, truncated.messageText().length());
        assertTrue(truncated.messageText().endsWith("..."));
    }

    @Test
    void chatReplyMustSanitizeSecretsAndBuildReplyMetadata() {
        OpsChannelOutboxRecordFactory factory = factory(12_000);

        ChannelOutboxRecord record = factory.chatReply(
                new OpsChannelOutboxRecordFactory.ChatReplyDraft(
                        "project-1", "channel-1", "room-1", "token=secret",
                        "run-1", "session-1", "message-1"));

        assertTrue(record.dedupKey().startsWith("channel-reply:"));
        assertFalse(record.messageText().contains("secret"));
        assertEquals("message-1", record.analysisId());
        Map<String, Object> metadata = JSON.parseObject(record.metadataJson());
        assertEquals("CHAT_REPLY", metadata.get("messageType"));
        assertEquals("message-1", metadata.get("replyToMessageId"));
        assertEquals("run-1", metadata.get("runId"));
        assertEquals("session-1", metadata.get("sessionId"));
    }

    @Test
    void missingRequiredDraftFieldsMustKeepHistoricalValidationMessages() {
        OpsChannelOutboxRecordFactory factory = factory(12_000);

        IllegalArgumentException notification = assertThrows(
                IllegalArgumentException.class,
                () -> factory.analysisNotification(new OpsChannelOutboxRecordFactory.AnalysisNotificationDraft(
                        "", "channel", "target", "", "analysis", "time", "report")));
        IllegalArgumentException reply = assertThrows(
                IllegalArgumentException.class,
                () -> factory.chatReply(new OpsChannelOutboxRecordFactory.ChatReplyDraft(
                        "project", "channel", "target", "", "", "", "message")));

        assertEquals("Channel 通知必须绑定 projectId", notification.getMessage());
        assertEquals("Channel 回复内容不能为空", reply.getMessage());
    }

    private OpsChannelOutboxRecordFactory factory(int maxMessageChars) {
        return new OpsChannelOutboxRecordFactory(
                new ChannelOutboundContentPolicy(),
                new OpsChannelNotificationSettings(maxMessageChars, 8, 120));
    }
}
