package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.alert.AlertAggregationTransactionPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

@Repository
public class SpringAlertAggregationTransactionAdapter implements AlertAggregationTransactionPort {

    private final TransactionTemplate transactionTemplate;

    public SpringAlertAggregationTransactionAdapter(
            @Qualifier("mysqlTransactionManager")
            ObjectProvider<PlatformTransactionManager> transactionManagerProvider) {
        PlatformTransactionManager transactionManager = transactionManagerProvider.getIfAvailable();
        this.transactionTemplate = transactionManager == null ? null : new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T required(Supplier<T> action) {
        if (action == null) throw new IllegalArgumentException("ALERT_AGGREGATION_TRANSACTION_ACTION_REQUIRED");
        if (transactionTemplate == null) {
            throw new IllegalStateException("ALERT_AGGREGATION_TRANSACTION_MANAGER_UNAVAILABLE");
        }
        // Concurrent creation of a missing key can still race on InnoDB gap locks.
        // Retry only after this adapter's entire database transaction has rolled back.
        // A joined caller transaction cannot safely be replayed here.
        boolean joined = TransactionSynchronizationManager.isActualTransactionActive();
        for (int attempt = 0; ; attempt++) {
            try {
                T result = transactionTemplate.execute(status -> action.get());
                if (result == null) throw new IllegalStateException("ALERT_AGGREGATION_TRANSACTION_RETURNED_NULL");
                return result;
            } catch (CannotAcquireLockException error) {
                if (joined || attempt >= 2 || Thread.currentThread().isInterrupted()) throw error;
                try {
                    Thread.sleep(ThreadLocalRandom.current().nextLong(10L << attempt, 40L << attempt));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw error;
                }
            }
        }
    }
}
