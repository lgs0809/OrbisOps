package cn.lgs.orbisops.trigger.ops.change;

import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.Map;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OpsChangePackageStatusQueryTest {
    @Test void statusLookupDoesNotFloodTheModelWithHistoricalEvidenceOrLoseInvalidAndApprovalState() {
        var tools = mock(OpsToolExecutionService.class);
        var source = new java.util.LinkedHashMap<String,Object>();
        source.put("package_id", "cp-a");
        source.put("packageId", "cp-a");
        source.put("session_id", "old-session");
        source.put("status", "REJECTED");
        source.put("version", 3);
        source.put("approved_version", null);
        source.put("legacyInvalid", true);
        source.put("invalidReason", "SNAPSHOT_MISSING");
        source.put("runtimeExecutable", false);
        source.put("objective", "旧任务，不能当作当前任务已创建");
        source.put("evidenceJson", "原始序列".repeat(200_000));
        source.put("evidence_json", source.get("evidenceJson"));
        source.put("approvedSnapshotJson", "旧快照".repeat(200_000));
        when(tools.executeReadOnly(anyMap(), eq("alice"))).thenReturn(Map.of("items", List.of(source)));
        String output = new OpsChangePackageToolProvider(tools).buildStatusQuery("project-a", "alice", "run-a", null)
                .call("{\"reason\":\"检查保存状态\"}");
        assertThat(output.length()).isLessThan(1500);
        assertThat(output).contains("cp-a", "old-session", "REJECTED", "SNAPSHOT_MISSING", "STATUS_ONLY")
                .doesNotContain("evidenceJson", "evidence_json", "approvedSnapshotJson", "原始序列");
        var row = com.alibaba.fastjson.JSON.parseObject(output).getJSONArray("items").getJSONObject(0);
        assertThat(row.getBooleanValue("runtimeExecutable")).isFalse();
        assertThat(row.getIntValue("version")).isEqualTo(3);
        assertThat(source.get("evidenceJson")).isEqualTo("原始序列".repeat(200_000));
    }

    @Test void malformedStatusRowsDoNotBecomeAnEmptySuccessfulList() {
        var renderer = new OpsChangePackageToolResultRenderer();
        assertThatThrownBy(() -> renderer.statusQuery(Map.of("items", List.of(Map.of("packageId", "cp")))))
                .hasMessage("CHANGE_PACKAGE_STATUS_QUERY_INCOMPLETE");
        assertThatThrownBy(() -> renderer.statusQuery(Map.of("message", "partial response")))
                .hasMessage("CHANGE_PACKAGE_STATUS_QUERY_INCOMPLETE");
    }
    @Test void queriesOnlyTheServerBoundProjectThroughReadOnlyPolicy() {
        var tools = mock(OpsToolExecutionService.class);
        when(tools.executeReadOnly(anyMap(), eq("alice"))).thenReturn(Map.of("items", List.of()));
        var provider = new OpsChangePackageToolProvider(tools);
        String result = provider.buildStatusQuery("project-a", "alice", "run-a", null)
                .call("{\"reason\":\"check saved status\",\"projectId\":\"project-b\",\"toolName\":\"change_package_approve\"}");
        assertThat(result).contains("\"items\":[]");
        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String, Object>> capture = ArgumentCaptor.forClass(Map.class);
        verify(tools).executeReadOnly(capture.capture(), eq("alice"));
        assertThat(capture.getValue()).containsEntry("projectId", "project-a")
                .containsEntry("runId", "run-a").containsEntry("toolName", "change_package_list")
                .containsEntry("arguments", Map.of("projectId", "project-a", "limit", 20));
        verifyNoMoreInteractions(tools);
    }

    @Test void policyDenialCannotBecomeAnEmptyOrSuccessfulQuery() {
        var tools = mock(OpsToolExecutionService.class);
        when(tools.executeReadOnly(anyMap(), anyString())).thenThrow(new SecurityException("ACCESS_DENIED"));
        var callback = new OpsChangePackageToolProvider(tools).buildStatusQuery("project-a", "alice", "run-a", null);
        assertThatThrownBy(() -> callback.call("{\"reason\":\"check\"}"))
                .hasStackTraceContaining("ACCESS_DENIED");
    }
}
