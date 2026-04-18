package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.application.evidence.EvidenceApplicationService;
import cn.lgs.orbisops.application.evidence.ToolResultApplicationService;
import cn.lgs.orbisops.domain.evidence.model.EvidenceRecord;
import cn.lgs.orbisops.domain.evidence.model.ToolResult;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OpsStoredMcpPreparationEvidenceTest {
    private final EvidenceApplicationService evidence = mock(EvidenceApplicationService.class);
    private final ToolResultApplicationService results = mock(ToolResultApplicationService.class);
    private final OpsStoredMcpPreparationEvidence collector = new OpsStoredMcpPreparationEvidence(evidence, results);
    private final String output = "{\"orbisopsResultVersion\":1,\"isError\":false,\"normalizedContent\":{\"resourceKey\":\"service://orders/test\",\"version\":\"v2\"}}";

    @Test void arbitraryReviewedProviderNamesCanSupplyStoredEvidence() throws Exception {
        fixture("run", "MCP_REMOTE_TOOL", output, hash(output));
        var collected = collector.collect("project", "run");
        assertThat(collected).hasSize(1);
        assertThat(collected.get(0).evidenceId()).isEqualTo("evidence-1");
        assertThat(collected.get(0).sourceType()).isEqualTo("MCP_REMOTE");
        assertThat(collected.get(0).metadata()).containsEntry("remoteToolName", "arbitrary_observe")
                .containsEntry("outputHash", hash(output));
    }

    @Test void discoveryOrForeignRunCannotSupplyExecutionEvidence() throws Exception {
        fixture("run", "MCP_DISCOVERY", output, hash(output));
        assertThat(collector.collect("project", "run")).isEmpty();
        verifyNoInteractions(results);
        fixture("other-run", "MCP_REMOTE_TOOL", output, hash(output));
        assertThat(collector.collect("project", "run")).isEmpty();
    }

    @Test void corruptHashAndBusinessErrorEnvelopeRemainUntrusted() throws Exception {
        fixture("run", "MCP_REMOTE_TOOL", output.replace("v2", "forged"), hash(output));
        assertThat(collector.collect("project", "run")).isEmpty();
        String error = output.replace("false", "true");
        fixture("run", "MCP_REMOTE_TOOL", error, hash(error));
        assertThat(collector.collect("project", "run")).isEmpty();
    }

    @Test void declaredProtocolFixtureCannotBecomeBusinessProof() throws Exception {
        String fixtureOutput = "{\"_meta\":{\"fixture\":true}," + output.substring(1);
        fixture("run", "MCP_REMOTE_TOOL", fixtureOutput, hash(fixtureOutput));
        assertThat(collector.collect("project", "run")).isEmpty();
    }

    private void fixture(String runId, String source, String body, String hash) {
        var record = new EvidenceRecord("evidence-1", "project", "run", "MCP", "arbitrary",
                "result-1", hash, "db:result-1", "untrusted preview", true,
                Map.of("source", source, "mcpId", "opaque-provider", "toolName", "arbitrary_observe"),
                "a".repeat(64), "actor", "2026-09-10T00:00:00Z");
        when(evidence.listForRun("project", "run", 100)).thenReturn(List.of(record));
        when(results.require("result-1")).thenReturn(new ToolResult("result-1", "project", "session", runId,
                "actor", "mcp.opaque-provider", "arbitrary_observe", source, "SUCCEEDED", "{}",
                "b".repeat(64), "untrusted preview", body, "db:result-1", hash, false, 1, null,
                "actor", "2026-09-10T00:00:00Z"));
    }

    private String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
