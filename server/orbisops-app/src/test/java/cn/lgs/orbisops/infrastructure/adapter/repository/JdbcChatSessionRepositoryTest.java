package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.chatsession.model.ChatSessionParticipantDraft;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionParticipantReplacement;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionSnapshot;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionUpdate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcChatSessionRepositoryTest {

    @Test
    void insertPersistsTypedSessionAndOwnerParticipant() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcChatSessionRepository repository = repository(jdbc);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        repository.insert(session());

        List<String> updateSql = mockingDetails(jdbc).getInvocations().stream()
                .filter(invocation -> "update".equals(invocation.getMethod().getName()))
                .map(invocation -> {
                    Object sql = invocation.getArgument(0);
                    return sql == null ? "" : sql.toString();
                })
                .toList();
        assertTrue(updateSql.stream().anyMatch(sql ->
                sql.contains("INSERT INTO ai_ops_chat_session\n(")));
        assertTrue(updateSql.stream().anyMatch(sql ->
                sql.contains("INSERT INTO ai_ops_chat_session_participant")));
    }

    @Test
    void updateReturnsFalseWhenStateVersionCasDoesNotMatch() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcChatSessionRepository repository = repository(jdbc);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);

        boolean updated = repository.update(new ChatSessionUpdate(
                "session-1", "new title", "ACTIVE", Map.of(), 3L));

        assertFalse(updated);
    }

    @Test
    void participantReplacementUsesSessionCasBeforeReplacingRows() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcChatSessionRepository repository = repository(jdbc);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        boolean replaced = repository.replaceParticipants(new ChatSessionParticipantReplacement(
                "session-1",
                "project-1",
                "owner",
                2L,
                List.of(new ChatSessionParticipantDraft("editor", "EDITOR"))));

        assertEquals(true, replaced);
        verify(jdbc).update(argThat(sql -> sql.contains("state_version=state_version+1")),
                any(Object[].class));
        verify(jdbc).update(argThat(sql -> sql.contains("participant_role<>'OWNER'")),
                any(Object[].class));
    }

    @Test
    void limitedMessageReadSelectsNewestWindowAndReturnsChronologicalOrder() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        JdbcChatSessionRepository repository = repository(jdbc);
        when(jdbc.query(
                anyString(),
                any(org.springframework.jdbc.core.RowMapper.class),
                any(Object[].class))).thenReturn(List.of());

        repository.messages("session-1", 6);

        String querySql = mockingDetails(jdbc).getInvocations().stream()
                .filter(invocation -> "query".equals(invocation.getMethod().getName()))
                .map(invocation -> String.valueOf((Object) invocation.getArgument(0)))
                .filter(sql -> sql.contains("ai_ops_chat_message"))
                .findFirst()
                .orElseThrow();
        assertTrue(querySql.contains("ORDER BY id DESC"));
        assertTrue(querySql.contains("ORDER BY recent.id ASC"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingStoreFailsClosed() {
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        JdbcChatSessionRepository repository = new JdbcChatSessionRepository(provider, null);

        assertThrows(IllegalStateException.class, () -> repository.insert(session()));
    }

    @Test
    void writeMethodsDeclareMysqlTransactionBoundary() throws NoSuchMethodException {
        Transactional insert = JdbcChatSessionRepository.class
                .getMethod("insert", ChatSessionSnapshot.class)
                .getAnnotation(Transactional.class);
        Transactional insertIfAbsent = JdbcChatSessionRepository.class
                .getMethod("insertIfAbsent", ChatSessionSnapshot.class)
                .getAnnotation(Transactional.class);
        Transactional replaceParticipants = JdbcChatSessionRepository.class
                .getMethod("replaceParticipants", ChatSessionParticipantReplacement.class)
                .getAnnotation(Transactional.class);

        assertNotNull(insert);
        assertNotNull(insertIfAbsent);
        assertNotNull(replaceParticipants);
        assertEquals("mysqlTransactionManager", insert.transactionManager());
        assertEquals("mysqlTransactionManager", insertIfAbsent.transactionManager());
        assertEquals("mysqlTransactionManager", replaceParticipants.transactionManager());
    }

    private JdbcChatSessionRepository repository(JdbcTemplate jdbc) {
        @SuppressWarnings("unchecked")
        ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        return new JdbcChatSessionRepository(provider, null);
    }

    private ChatSessionSnapshot session() {
        return new ChatSessionSnapshot(
                "session-1",
                "owner",
                "project-1",
                "agent-1",
                "PINNED_VERSION",
                3,
                "a".repeat(64),
                "title",
                "AGENT",
                "GRAPH",
                false,
                "",
                "ACTIVE",
                1L,
                Map.of("favorite", true),
                "",
                "",
                0,
                "");
    }
}
