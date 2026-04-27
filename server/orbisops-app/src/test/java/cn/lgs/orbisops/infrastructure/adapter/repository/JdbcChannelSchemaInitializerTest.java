package cn.lgs.orbisops.infrastructure.adapter.repository;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JdbcChannelSchemaInitializerTest {

    @Test
    void disabledCompatibilityBootstrapDoesNotOwnProductionMigration() {
        OpsChannelRepository channels = mock(OpsChannelRepository.class);
        OpsChannelOutboxRepository outbox = mock(OpsChannelOutboxRepository.class);
        OpsChannelApprovalActionRepository approvalActions = mock(OpsChannelApprovalActionRepository.class);
        JdbcChannelSchemaInitializer initializer = new JdbcChannelSchemaInitializer(channels, outbox, approvalActions);

        initializer.initialize();

        verify(channels, never()).ensureSchema();
        verify(approvalActions, never()).ensureSchema();
        verify(outbox, never()).ensureSchema();
    }

    @Test
    void explicitDevBootstrapInitializesAvailableStores() {
        OpsChannelRepository channels = mock(OpsChannelRepository.class);
        OpsChannelOutboxRepository outbox = mock(OpsChannelOutboxRepository.class);
        OpsChannelApprovalActionRepository approvalActions = mock(OpsChannelApprovalActionRepository.class);
        when(outbox.available()).thenReturn(true);
        JdbcChannelSchemaInitializer initializer = new JdbcChannelSchemaInitializer(channels, outbox, approvalActions);
        ReflectionTestUtils.setField(initializer, "channelAutoInit", true);
        ReflectionTestUtils.setField(initializer, "outboxAutoInit", true);

        initializer.initialize();

        verify(channels).ensureSchema();
        verify(approvalActions).ensureSchema();
        verify(outbox).ensureSchema();
    }
}
