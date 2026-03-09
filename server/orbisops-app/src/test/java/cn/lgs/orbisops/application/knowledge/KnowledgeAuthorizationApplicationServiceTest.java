package cn.lgs.orbisops.application.knowledge;

import cn.lgs.orbisops.domain.knowledge.adapter.repository.IProjectKnowledgeAuthorizationRepository;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeAuthorizationUsageCount;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeStatus;
import cn.lgs.orbisops.domain.knowledge.model.ProjectKnowledgeAuthorization;
import cn.lgs.orbisops.domain.knowledge.model.ProjectKnowledgeAuthorizationUsage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KnowledgeAuthorizationApplicationServiceTest {

    @Test
    void validatesAndPersistsAuthenticatedAuthorization() {
        RecordingRepository repository = new RecordingRepository();
        KnowledgeAuthorizationApplicationService service =
                new KnowledgeAuthorizationApplicationService(repository);

        ProjectKnowledgeAuthorization result = service.enable(new KnowledgeGlobalAuthorizationCommand(
                " project-1 ", " global-kb ", KnowledgeStatus.ENABLED, "alice"));

        assertEquals("project-1", result.projectId());
        assertEquals("global-kb", result.globalKbId());
        assertEquals(KnowledgeStatus.ENABLED, result.status());
        assertEquals("alice", result.enabledBy());
        assertEquals(List.of("save:project-1:global-kb:alice"), repository.events);
    }

    @Test
    void rejectsAuthorizationWithoutAuthenticatedOperator() {
        KnowledgeAuthorizationApplicationService service =
                new KnowledgeAuthorizationApplicationService(new RecordingRepository());

        assertThrows(IllegalArgumentException.class,
                () -> new KnowledgeGlobalAuthorizationCommand(
                        "project-1", "global-kb", KnowledgeStatus.ENABLED, ""));
    }

    @Test
    void returnsDistinctEnabledIdsAndStableUsageViews() {
        RecordingRepository repository = new RecordingRepository();
        repository.enabledIds = List.of(" global-a ", "global-a", "global-b", " ");
        repository.usages = List.of(new ProjectKnowledgeAuthorizationUsage(
                "project-1", "Project One", "global-a", KnowledgeStatus.ENABLED,
                "alice", "2026-07-19 08:00:00", "2026-07-19 09:00:00"));
        repository.counts = List.of(
                new KnowledgeAuthorizationUsageCount("global-a", 2L),
                new KnowledgeAuthorizationUsageCount("global-b", 1L));
        KnowledgeAuthorizationApplicationService service =
                new KnowledgeAuthorizationApplicationService(repository);

        assertEquals(List.of("global-a", "global-b"),
                service.enabledKnowledgeBaseIds("project-1"));
        assertEquals("Project One", service.usageProjects("global-a").get(0).get("projectName"));
        assertEquals(Map.of("global-a", 2L, "global-b", 1L), service.enabledUsageCounts());
    }

    private static final class RecordingRepository
            implements IProjectKnowledgeAuthorizationRepository {
        private final List<String> events = new ArrayList<>();
        private List<String> enabledIds = List.of();
        private List<ProjectKnowledgeAuthorizationUsage> usages = List.of();
        private List<KnowledgeAuthorizationUsageCount> counts = List.of();

        @Override
        public ProjectKnowledgeAuthorization save(ProjectKnowledgeAuthorization authorization) {
            events.add("save:" + authorization.projectId() + ":"
                    + authorization.globalKbId() + ":" + authorization.enabledBy());
            return authorization;
        }

        @Override
        public List<String> listEnabledKnowledgeBaseIds(String projectId) {
            events.add("enabled:" + projectId);
            return enabledIds;
        }

        @Override
        public List<ProjectKnowledgeAuthorizationUsage> listUsageProjects(String globalKbId) {
            events.add("usage:" + globalKbId);
            return usages;
        }

        @Override
        public List<KnowledgeAuthorizationUsageCount> listEnabledUsageCounts() {
            events.add("counts");
            return counts;
        }
    }
}
