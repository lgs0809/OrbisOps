package cn.lgs.orbisops.trigger.application.source;

import cn.lgs.orbisops.application.project.ProjectMcpProjectionApplicationService;
import cn.lgs.orbisops.domain.source.model.SourceRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OpsSourceMcpProjectionAdapterTest {

    @Test
    @SuppressWarnings("unchecked")
    void publishesReadOnlyGitProjectionWithConfiguredTimeout() {
        ProjectMcpProjectionApplicationService projections = mock(ProjectMcpProjectionApplicationService.class);
        OpsSourceMcpProjectionAdapter adapter = new OpsSourceMcpProjectionAdapter(
                projections, new OpsSourceMcpProjectionSettings(13));
        SourceRepository repository = new SourceRepository(
                "repo-1", "repo-1-readonly-git-mcp", "project-1", "repo", "/tmp/repo", "HEAD",
                "0123456789abcdef0123456789abcdef01234567", "READY", "admin", "now", "now");

        adapter.publish(repository);

        ArgumentCaptor<Map<String, Object>> view = ArgumentCaptor.forClass(Map.class);
        verify(projections).publish(view.capture());
        assertEquals("repo-1-readonly-git-mcp", view.getValue().get("mcpId"));
        assertEquals(13, view.getValue().get("requestTimeout"));
        assertEquals(true, view.getValue().get("readOnly"));
        assertTrue(((Map<String, Object>) view.getValue().get("transportConfig")).containsKey("defaultCommitSha"));
    }
}
