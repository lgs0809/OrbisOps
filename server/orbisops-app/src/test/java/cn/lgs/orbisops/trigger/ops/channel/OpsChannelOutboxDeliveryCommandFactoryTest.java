package cn.lgs.orbisops.trigger.ops.channel;

import cn.lgs.orbisops.application.channel.ChannelModels;
import cn.lgs.orbisops.domain.channel.model.ChannelOutboxRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpsChannelOutboxDeliveryCommandFactoryTest {

    private final OpsChannelOutboxDeliveryCommandFactory factory =
            new OpsChannelOutboxDeliveryCommandFactory(new OpsChannelOutboxMetadataCodec());

    @Test
    void recordMustProjectToStableOutboundCommandWithOutboxMetadata() {
        ChannelModels.Send command = factory.create(record("message", "{\"runId\":\"run-1\"}"));

        assertEquals("project-1", command.projectId());
        assertEquals("channel-1", command.channelId());
        assertEquals("room-1", command.target());
        assertEquals("message", command.content());
        assertEquals("run-1", command.metadata().get("runId"));
        assertEquals(7L, command.metadata().get("outboxId"));
        assertEquals("channel-notification-outbox", command.actor());
    }

    @Test
    void missingMessageMustPreserveStableFailureCode() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> factory.create(record(" ", "{}")));

        assertEquals("CHANNEL_NOTIFICATION_MESSAGE_MISSING", error.getMessage());
    }

    private ChannelOutboxRecord record(String message, String metadata) {
        return new ChannelOutboxRecord(
                7L, "dedup", "project-1", "channel-1", "room-1", "analysis", "PENDING",
                message, metadata, "hash", 0, "", "",
                null, null, null, null, null, null);
    }
}
