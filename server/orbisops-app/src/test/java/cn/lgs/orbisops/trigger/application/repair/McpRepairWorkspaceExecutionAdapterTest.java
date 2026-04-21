package cn.lgs.orbisops.trigger.application.repair;

import cn.lgs.orbisops.application.repair.RepairExecutionCommand;
import cn.lgs.orbisops.application.repair.RepairSourceCatalogPort;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspace;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceCandidate;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceStatus;
import cn.lgs.orbisops.domain.source.model.BuildProfile;
import cn.lgs.orbisops.domain.source.model.ProjectService;
import cn.lgs.orbisops.domain.source.model.SourceRepository;
import cn.lgs.orbisops.domain.source.model.SourceRepositoryAccessMode;
import cn.lgs.orbisops.trigger.ops.code.OpsCodeWorkspaceMcpClient;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpRepairWorkspaceExecutionAdapterTest {

    private static final String BASE = "a".repeat(40);
    private static final String REPAIR = "b".repeat(40);
    private static final String DIFF = "c".repeat(64);

    @Test
    void createAndVerifyPinsBaseRunsBuildAndCommitsSameDiff() {
        OpsCodeWorkspaceMcpClient client = mock(OpsCodeWorkspaceMcpClient.class);
        RepairSourceCatalogPort sources = mock(RepairSourceCatalogPort.class);
        SourceRepository repository = repository();
        ProjectService service = service();
        when(sources.findRepository("project-1", "repo-1")).thenReturn(Optional.of(repository));
        AtomicInteger diffCalls = new AtomicInteger();
        when(client.invoke(eq("project-1"), eq("code-mcp"), any(), any())).thenAnswer(invocation -> {
            String tool = invocation.getArgument(2);
            if ("code_enter_worktree".equals(tool)) return json("""
                    {"workspaceId":"rw-1","baseCommit":"%s","status":"ACTIVE"}
                    """.formatted(BASE));
            if ("code_apply_patch".equals(tool)) return json("""
                    {"status":"APPLIED","changedFiles":["src/App.java"],"patchHash":"%s"}
                    """.formatted("d".repeat(64)));
            if ("code_diff".equals(tool)) {
                diffCalls.incrementAndGet();
                return json("""
                        {"workspaceId":"rw-1","baseCommit":"%s","currentHead":"%s",
                         "changedFiles":["src/App.java"],"diffStat":"1 file changed","diffHash":"%s","diffBytes":42}
                        """.formatted(BASE, BASE, DIFF));
            }
            if ("code_bash".equals(tool)) return json("""
                    {"status":"SUCCEEDED","exitCode":0,"output":"tests ok","outputHash":"%s","durationMs":10,"cwd":"."}
                    """.formatted("e".repeat(64)));
            if ("code_commit".equals(tool)) return json("""
                    {"status":"COMMITTED","repairCommit":"%s","diffHash":"%s","changedFiles":["src/App.java"]}
                    """.formatted(REPAIR, DIFF));
            throw new AssertionError("unexpected tool: " + tool);
        });

        McpRepairWorkspaceExecutionAdapter adapter = new McpRepairWorkspaceExecutionAdapter(client, sources);
        RepairWorkspace result = adapter.createAndVerify(new RepairExecutionCommand(
                "rw-1",
                new RepairWorkspaceCandidate(
                        "project-1", "svc-1", "prod", "repair", "diff --git a/src/App.java b/src/App.java\n", BASE),
                service,
                repository,
                BASE,
                "agent"));

        assertEquals(RepairWorkspaceStatus.VERIFIED, result.status());
        assertEquals(BASE, result.baseCommit());
        assertEquals(REPAIR, result.verifiedCommit());
        assertEquals(List.of("src/App.java"), result.changedFiles());
        assertEquals(0, result.testExitCode());
        assertTrue(diffCalls.get() >= 2);
        verify(client).invoke(eq("project-1"), eq("code-mcp"), eq("code_commit"), any());
    }

    @Test
    void cleanupUsesRemoteWorkspaceAndNeverNeedsHostPath() {
        OpsCodeWorkspaceMcpClient client = mock(OpsCodeWorkspaceMcpClient.class);
        RepairSourceCatalogPort sources = mock(RepairSourceCatalogPort.class);
        SourceRepository repository = repository();
        when(sources.findRepository("project-1", "repo-1")).thenReturn(Optional.of(repository));
        when(client.invoke(eq("project-1"), eq("code-mcp"), eq("code_cleanup"), any()))
                .thenReturn(json("{" + "\"status\":\"CLEANED\",\"worktreeRemoved\":true}"));
        McpRepairWorkspaceExecutionAdapter adapter = new McpRepairWorkspaceExecutionAdapter(client, sources);
        RepairWorkspace workspace = new RepairWorkspace(
                "rw-1", "project-1", "svc-1", "repo-1", "prod", BASE, REPAIR,
                RepairWorkspaceStatus.COMMITTED, "repair", "", List.of("src/App.java"),
                "MAVEN_VERIFY", "mvn test", 0, "ok", "", "", 0L,
                "agent", "2026-08-14 00:00:00", "2026-08-14 00:00:00");

        assertEquals("CLEANED", adapter.cleanup(workspace, repository, true).status());
        assertTrue(adapter.cleanup(workspace, repository, true).worktreePath().startsWith("mcp://code-mcp/"));
    }

    private SourceRepository repository() {
        return new SourceRepository(
                "repo-1", "repo-1-readonly-git-mcp", "project-1", "repo", "",
                SourceRepositoryAccessMode.MCP, "code-mcp", "logical-repo",
                "prod", BASE, "READY", "owner", "2026-08-14 00:00:00", "2026-08-14 00:00:00");
    }

    private ProjectService service() {
        return new ProjectService(
                "svc-1", "project-1", "service", "repo-1", ".", BuildProfile.MAVEN_VERIFY,
                "", "runtime-1", "http://localhost/health", List.of(), "READY",
                "2026-08-14 00:00:00", "2026-08-14 00:00:00");
    }

    private JSONObject json(String value) {
        return JSON.parseObject(value);
    }
}
