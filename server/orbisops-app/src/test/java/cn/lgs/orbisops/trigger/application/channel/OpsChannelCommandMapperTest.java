package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.ChannelModels;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;
import cn.lgs.orbisops.types.execution.ExecutionType;
import cn.lgs.orbisops.types.execution.ExecutionVersionPolicy;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsChannelCommandMapperTest {

    private final OpsChannelCommandMapper mapper = new OpsChannelCommandMapper();

    @Test
    void configurationMapsProtocolAliasesAndPreservesFieldPresence() {
        ChannelModels.ConfigurationMutation command = mapper.configuration(
                "project-path", "channel-path", Map.of(
                        "projectId", "project-body",
                        "channelId", "channel-body",
                        "agentId", "agent-1",
                        "agentBindingMode", "pinned_version",
                        "agentVersion", "7",
                        "name", "Oncall",
                        "type", "generic_webhook",
                        "credentialRef", "secret-ref",
                        "config", Map.of("outboundUrl", "https://bridge.test"),
                        "status", "disabled"), "admin");

        assertEquals("project-path", command.projectId());
        assertEquals("channel-path", command.channelId());
        assertEquals("project-body", command.requestedProjectId().value());
        assertEquals("channel-body", command.requestedChannelId().value());
        assertEquals(ExecutionType.WORKFLOW, command.executionType().value());
        assertEquals("agent-1", command.workflowId().value());
        assertEquals(ExecutionVersionPolicy.PINNED_VERSION, command.workflowVersionPolicy().value());
        assertEquals(7, command.workflowVersion().value());
        assertEquals("generic_webhook", command.channelType().value());
        assertEquals(ChannelStatus.DISABLED, command.status().value());
        assertEquals(Map.of("outboundUrl", "https://bridge.test"), command.configuration().value());
    }

    @Test
    void omittedUpdateFieldsRemainAbsentRatherThanBecomingDefaults() {
        ChannelModels.ConfigurationMutation command = mapper.configuration(
                "project-1", "channel-1", Map.of("name", "Renamed"), "admin");

        assertTrue(command.name().supplied());
        assertEquals("Renamed", command.name().value());
        assertFalse(command.executionType().supplied());
        assertFalse(command.workflowId().supplied());
        assertFalse(command.workflowVersionPolicy().supplied());
        assertFalse(command.workflowVersion().supplied());
        assertFalse(command.status().supplied());
        assertFalse(command.configuration().supplied());
    }

    @Test
    void identityMapsStatusAndCasVersionAtProtocolBoundary() {
        ChannelModels.IdentityBinding command = mapper.identity("project-1", "channel-1", Map.of(
                "externalSenderId", "sender-1",
                "platformUserId", "user-1",
                "username", "alice",
                "status", "disabled",
                "expectedVersion", "4"), "admin");

        assertEquals(ChannelStatus.DISABLED, command.status());
        assertEquals(4L, command.expectedVersion());
        assertEquals("sender-1", command.externalSenderId());
    }

    @Test
    void unknownStableEnumFailsClosedBeforeApplication() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> mapper.configuration("", "", Map.of(
                        "agentBindingMode", "floating"), "admin"));

        assertEquals("EXECUTION_VERSION_POLICY_INVALID", failure.getMessage());
    }
}
