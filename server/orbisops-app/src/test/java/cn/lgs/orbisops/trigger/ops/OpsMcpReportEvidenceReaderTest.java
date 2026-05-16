package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.domain.evidence.adapter.repository.IToolResultRepository;
import cn.lgs.orbisops.domain.evidence.model.ToolResult;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.trigger.ops.runtime.OpsToolExecutionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsMcpReportEvidenceReaderTest {
    final IToolResultRepository results = mock(IToolResultRepository.class);
    final OpsMcpReportEvidenceReader reader = new OpsMcpReportEvidenceReader(results);

    @Test @SuppressWarnings("unchecked")
    void reconstructsActualReceiptsWithoutPromotingQuerySuccessToBusinessHealth() throws Exception {
        fixture("valid");
        var response = response();
        var service = new OpsStructuredReportService(mock(OpsToolExecutionPolicy.class), reader);
        var report = service.compose(response);
        var diagnosis = (Map<String, Object>) report.get("diagnosis");
        assertThat(diagnosis).containsEntry("evidenceCompleteness", "PARTIAL");
        assertThat((List<String>) diagnosis.get("unknowns")).noneMatch(s -> s.contains("尚未形成数据源查询结果"));
        assertThat((List<Map<String, Object>>) diagnosis.get("sourceStatus")).anySatisfy(source -> {
            assertThat(source).containsEntry("sourceId", "mcp.provider:query_anything")
                    .containsEntry("queryStatus", "SUCCEEDED").containsEntry("assessment", "UNKNOWN");
        });
        assertThat(reader.read(response)).singleElement().satisfies(ref -> {
            assertThat(ref).containsEntry("resultId", "tool-result-wrapper");
            assertThat(ref.get("outputHash")).isEqualTo(results.find("tool-result-wrapper").orElseThrow().outputHash());
        });
        // Rendering existing history must not mutate its original runtime step or approval facts.
        assertThat(response.getAgentExecutionSteps().get(0).getVerified()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong-run", "wrong-project", "wrong-provider", "hash-tampered", "remote-error", "missing-remote", "denied", "wrong-ref"})
    void rejectsUnrelatedDeniedOrCorruptReceipts(String failure) throws Exception {
        fixture(failure);
        assertThat(reader.read(response())).isEmpty();
    }

    private OpsAnalysisResponseDTO response() {
        var response = new OpsAnalysisResponseDTO();
        response.setAnalysisId("run-1");
        var step = OpsAnalysisResponseDTO.AgentExecutionStepDTO.builder().eventType("TOOL_CALL_FINISHED")
                .resultId("tool-result-wrapper").status("SUCCEEDED").verified(false).build();
        response.setAgentExecutionSteps(List.of(step, step));
        return response;
    }

    private void fixture(String failure) throws Exception {
        var envelope = CanonicalJson.stringify(Map.of("orbisopsResultVersion", 1, "isError", failure.equals("remote-error"),
                "normalizedContent", Map.of("observations", List.of())));
        var remote = record("tool-result-remote", failure.equals("wrong-project") ? "project-b" : "project-a",
                "run-1", "MCP_REMOTE_TOOL", envelope, hash(envelope));
        var wrapper = new java.util.LinkedHashMap<String, Object>(Map.of(
                "allowed", !failure.equals("denied"), "decision", failure.equals("denied") ? "DENIED" : "ALLOWED", "providerType", "MCP",
                "providerId", failure.equals("wrong-provider") ? "unrelated" : "provider", "remoteToolName", "query_anything",
                "providerResultId", remote.resultId(), "providerOutputHash", remote.outputHash(),
                "providerFullOutputRef", failure.equals("wrong-ref") ? "db:other" : remote.fullOutputRef()));
        String raw = CanonicalJson.stringify(wrapper);
        var saved = record("tool-result-wrapper", "project-a", failure.equals("wrong-run") ? "other-run" : "run-1",
                "PRE_APPROVAL_WORKFLOW:mcp.provider:query_anything", raw, failure.equals("hash-tampered") ? "a".repeat(64) : hash(raw));
        when(results.find(saved.resultId())).thenReturn(Optional.of(saved));
        when(results.find(remote.resultId())).thenReturn(failure.equals("missing-remote") ? Optional.empty() : Optional.of(remote));
    }

    private ToolResult record(String id, String project, String run, String source, String raw, String hash) {
        return new ToolResult(id, project, "session", run, "user", "", "query_anything", source, "SUCCEEDED", "", "b".repeat(64),
                "", raw, "db:" + id, hash, false, 1, null, "user", "2026-09-09 02:53:35");
    }
    private String hash(String raw) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)));
    }
}
