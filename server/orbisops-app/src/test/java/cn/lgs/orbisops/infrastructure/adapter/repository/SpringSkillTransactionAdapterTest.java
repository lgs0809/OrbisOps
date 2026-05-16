package cn.lgs.orbisops.infrastructure.adapter.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpringSkillTransactionAdapterTest {

    @Test
    @SuppressWarnings("unchecked")
    void requiredExecutesActionWithConfiguredTransactionManager() {
        ObjectProvider<PlatformTransactionManager> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(new ImmediateTransactionManager());
        SpringSkillTransactionAdapter adapter = new SpringSkillTransactionAdapter(provider);

        assertEquals("done", adapter.required(() -> "done"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void missingTransactionManagerFailsClosed() {
        ObjectProvider<PlatformTransactionManager> provider = mock(ObjectProvider.class);
        SpringSkillTransactionAdapter adapter = new SpringSkillTransactionAdapter(provider);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> adapter.required(() -> "done"));

        assertEquals("SKILL_TRANSACTION_MANAGER_UNAVAILABLE", error.getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void nullActionAndNullResultAreRejected() {
        ObjectProvider<PlatformTransactionManager> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(new ImmediateTransactionManager());
        SpringSkillTransactionAdapter adapter = new SpringSkillTransactionAdapter(provider);

        assertEquals("SKILL_TRANSACTION_ACTION_REQUIRED",
                assertThrows(IllegalArgumentException.class, () -> adapter.required(null)).getMessage());
        assertEquals("SKILL_TRANSACTION_RETURNED_NULL",
                assertThrows(IllegalStateException.class, () -> adapter.required(() -> null)).getMessage());
    }

    private static final class ImmediateTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
        }

        @Override
        public void rollback(TransactionStatus status) {
        }
    }
}
