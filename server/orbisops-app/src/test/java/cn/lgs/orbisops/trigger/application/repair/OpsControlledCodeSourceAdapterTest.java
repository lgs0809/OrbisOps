package cn.lgs.orbisops.trigger.application.repair;

import cn.lgs.orbisops.application.source.SourceRepositoryApplicationService;
import cn.lgs.orbisops.domain.source.model.SourceFile;
import cn.lgs.orbisops.domain.source.model.SourceRepository;
import cn.lgs.orbisops.domain.source.model.SourceSearchHit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsControlledCodeSourceAdapterTest {

    private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";

    @Test
    void delegatesTypedSourceReadSearchAndRepositoryLookup() {
        SourceRepositoryApplicationService sources = mock(SourceRepositoryApplicationService.class);
        SourceFile file = new SourceFile("repo-1", COMMIT, "App.java", 10, "class App {}");
        SourceSearchHit hit = new SourceSearchHit("repo-1", COMMIT, "App.java", 1, "class App {}");
        SourceRepository repository = new SourceRepository(
                "repo-1", "repo-1-readonly-git-mcp", "project-1", "repo", "/tmp/repo",
                "HEAD", COMMIT, "READY", "alice", "now", "now");
        when(sources.readFile("project-1", "repo-1", "HEAD", "App.java")).thenReturn(file);
        when(sources.search("project-1", "repo-1", "HEAD", "App", 20)).thenReturn(List.of(hit));
        when(sources.find("project-1", "repo-1")).thenReturn(Optional.of(repository));
        OpsControlledCodeSourceAdapter adapter = new OpsControlledCodeSourceAdapter(sources);

        assertSame(file, adapter.readFile("project-1", "repo-1", "HEAD", "App.java"));
        assertSame(hit, adapter.search("project-1", "repo-1", "HEAD", "App", 20).get(0));
        assertSame(repository, adapter.find("project-1", "repo-1").orElseThrow());

        verify(sources).readFile("project-1", "repo-1", "HEAD", "App.java");
        verify(sources).search("project-1", "repo-1", "HEAD", "App", 20);
        verify(sources).find("project-1", "repo-1");
    }
}
