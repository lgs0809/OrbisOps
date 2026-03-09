package cn.lgs.orbisops.trigger.application.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.trigger.application.agentdefinition.OpsAgentDefinitionQueryGateway;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentDefinition;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsAnalysisAgentDefinitionSnapshotResolverTest {

    @Test
    void shouldReturnAuthoritativeSnapshotWhenHashMatches() {
        OpsAgentDefinitionQueryGateway gateway = mock(OpsAgentDefinitionQueryGateway.class);
        OpsAgentDefinition authoritative = definition("payment", "hash-v7");
        when(gateway.resolveForProject("ops-agent", 7, false, "payment"))
                .thenReturn(authoritative);
        OpsAnalysisAgentDefinitionSnapshotResolver resolver =
                new OpsAnalysisAgentDefinitionSnapshotResolver(gateway);

        OpsAgentDefinition resolved = resolver.resolve(OpsAgentRunRequestDTO.builder()
                .agentDefinitionSnapshotJson(JSON.toJSONString(definition("payment", "hash-v7")))
                .build(), "payment");

        assertSame(authoritative, resolved);
        assertEquals("ops-agent", resolver.parse(resolver.serialize(resolved)).getAgentId());
    }

    @Test
    void shouldRejectForgedCrossProjectIncompleteAndMalformedSnapshots() {
        OpsAgentDefinitionQueryGateway gateway = mock(OpsAgentDefinitionQueryGateway.class);
        OpsAgentDefinition authoritative = definition("payment", "authoritative");
        when(gateway.resolveForProject("ops-agent", 7, false, "payment"))
                .thenReturn(authoritative);
        OpsAnalysisAgentDefinitionSnapshotResolver resolver =
                new OpsAnalysisAgentDefinitionSnapshotResolver(gateway);

        assertThrows(SecurityException.class, () -> resolver.resolve(OpsAgentRunRequestDTO.builder()
                .agentDefinitionSnapshotJson(JSON.toJSONString(definition("payment", "forged")))
                .build(), "payment"));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(OpsAgentRunRequestDTO.builder()
                .agentDefinitionSnapshotJson(JSON.toJSONString(definition("another-project", "authoritative")))
                .build(), "payment"));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(OpsAgentRunRequestDTO.builder()
                .agentDefinitionSnapshotJson(JSON.toJSONString(OpsAgentDefinition.builder()
                        .agentId("ops-agent")
                        .version(0)
                        .build()))
                .build(), "payment"));
        assertThrows(IllegalArgumentException.class, () -> resolver.parse("{not-json"));
    }

    private OpsAgentDefinition definition(String projectId, String hash) {
        return OpsAgentDefinition.builder()
                .agentId("ops-agent")
                .projectId(projectId)
                .version(7)
                .definitionHash(hash)
                .name("Ops Agent")
                .engine("GRAPH")
                .build();
    }
}
