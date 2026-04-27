package cn.lgs.orbisops.trigger.ops.channel;

import cn.lgs.orbisops.domain.channel.model.ChannelOutboxRecord;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsChannelOutboxViewMapperTest {

    private final OpsChannelOutboxViewMapper mapper = new OpsChannelOutboxViewMapper();

    @Test
    void analysisRecordMustMapStableAdministrativeViewAndAllowNullTimestamps() {
        Map<String, Object> view = mapper.toView(record("{}", "analysis-1"));

        assertEquals(7L, view.get("id"));
        assertEquals("ANALYSIS_NOTIFICATION", view.get("messageType"));
        assertEquals("analysis-1", view.get("referenceId"));
        assertEquals("PENDING", view.get("status"));
        assertNull(view.get("nextRetryAt"));
        assertNull(view.get("deadLetterAt"));
        assertThrows(UnsupportedOperationException.class, () -> view.put("changed", true));
    }

    @Test
    void chatReplyMustUseReplyMessageReferenceFromMetadata() {
        Map<String, Object> view = mapper.toView(record(
                "{\"messageType\":\"CHAT_REPLY\",\"replyToMessageId\":\"message-9\"}",
                "legacy-reference"));

        assertEquals("CHAT_REPLY", view.get("messageType"));
        assertEquals("message-9", view.get("referenceId"));
    }

    @Test
    void invalidMetadataMustDegradeToAnalysisNotification() {
        Map<String, Object> view = mapper.toView(record("not-json", "analysis-1"));

        assertEquals("ANALYSIS_NOTIFICATION", view.get("messageType"));
        assertEquals("analysis-1", view.get("referenceId"));
        assertTrue(mapper.metadata("not-json").isEmpty());
    }

    private ChannelOutboxRecord record(String metadata, String reference) {
        return new ChannelOutboxRecord(
                7L, "dedup", "project", "channel", "target", reference, "PENDING",
                "message", metadata, "hash", 0, "", "",
                null, null, null, null, null, null);
    }
}
