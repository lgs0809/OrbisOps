package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.chatsession.model.ChatSessionSearchCriteria;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Synthetic catalog in a disposable native MySQL; no application business database is used. */
@Testcontainers(disabledWithoutDocker = true)
class ChatSessionProjectCatalogMySqlTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("chat_catalog").withUsername("fixture").withPassword("fixture");
    private JdbcChatSessionRepository repository;
    private JdbcTemplate jdbc;

    @BeforeEach void prepare() {
        var dataSource = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("mysqlJdbcTemplate", jdbc);
        var provider = beans.getBeanProvider(JdbcTemplate.class);
        var sessions = new JdbcChatSessionSchemaInitializer(provider);
        var messages = new JdbcConversationMemorySchemaInitializer(provider);
        sessions.initialize(); messages.initialize();
        // Only this disposable Testcontainer is reset between synthetic catalog scenarios.
        jdbc.update("DELETE FROM ai_ops_chat_session_participant");
        jdbc.update("DELETE FROM ai_ops_chat_session");
        new ResourceDatabasePopulator(new ClassPathResource("chat-project-catalog-capacity.sql")).execute(dataSource);
        repository = new JdbcChatSessionRepository(provider, sessions, messages);
    }

    @Test void sameActorOtherProjectOver100CannotEvictTheSelectedProjectSession() {
        var selected = repository.search(new ChatSessionSearchCriteria("catalog-owner", "", "", null, 100, "catalog-project-a"))
                .stream()
                .map(ChatSessionSnapshot::sessionId).toList();
        assertEquals(java.util.List.of("catalog-target"), selected);
    }

    @Test void omittedScopePreservesTheBoundedGlobalCatalog() {
        var old = repository.search(new ChatSessionSearchCriteria("catalog-owner", "", "", null, 100));
        var omitted = repository.search(new ChatSessionSearchCriteria("catalog-owner", "", "", null, 100, null));
        assertEquals(old, omitted);
        assertEquals(100, omitted.size());
        assertTrue(omitted.stream().allMatch(session -> "catalog-project-b".equals(session.projectId())));
    }

    @Test void projectFilteringPreservesActiveParticipantsAndNeverIncludesAnUnrelatedActor() {
        assertEquals(java.util.List.of("catalog-shared"), repository.search(
                new ChatSessionSearchCriteria("catalog-observer", "", "", null, 100, "catalog-project-a"))
                .stream().map(ChatSessionSnapshot::sessionId).toList());
        assertTrue(repository.search(new ChatSessionSearchCriteria("not-a-participant", "", "", null, 100,
                "catalog-project-a")).isEmpty());
        assertTrue(repository.search(new ChatSessionSearchCriteria("catalog-observer", "", "", null, 100,
                "catalog-project-b")).isEmpty());
    }

    @Test void projectScopeCombinesWithExistingKeywordAndFavoriteFiltersBeforeLimit() {
        assertEquals(java.util.List.of("catalog-target"), repository.search(
                new ChatSessionSearchCriteria("catalog-owner", "", "target", true, 1, " catalog-project-a "))
                .stream().map(ChatSessionSnapshot::sessionId).toList());
        assertTrue(repository.search(new ChatSessionSearchCriteria("catalog-owner", "", "target", false, 1,
                "catalog-project-a")).isEmpty());
    }

    @Test void boundedSameProjectCatalogDoesNotMeanAnOlderExplicitSessionIsMissing() {
        for (int i = 0; i < 111; i++) {
            jdbc.update("INSERT INTO ai_ops_chat_session(session_id,user_id,project_id,last_active_at) VALUES (?,?,?,?)",
                    "same-project-newer-" + i, "catalog-owner", "catalog-project-a", "2026-01-01 00:00:00");
        }
        var bounded = repository.search(new ChatSessionSearchCriteria("catalog-owner", "", "", null, 100,
                "catalog-project-a"));
        assertEquals(100, bounded.size());
        assertTrue(bounded.stream().noneMatch(session -> "catalog-target".equals(session.sessionId())));
        var explicit = repository.findById("catalog-target").orElseThrow();
        assertEquals("catalog-owner", explicit.userId());
        assertEquals("catalog-project-a", explicit.projectId());
    }
}
