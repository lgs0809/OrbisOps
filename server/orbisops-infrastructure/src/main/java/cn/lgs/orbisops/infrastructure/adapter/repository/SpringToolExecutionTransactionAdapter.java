package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.toolexecution.ToolExecutionTransactionPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Component
public final class SpringToolExecutionTransactionAdapter implements ToolExecutionTransactionPort {

    private final TransactionTemplate transactions;

    public SpringToolExecutionTransactionAdapter(
            @Qualifier("mysqlTransactionManager") PlatformTransactionManager transactionManager) {
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T required(Supplier<T> action) {
        if (action == null) {
            throw new IllegalArgumentException("TOOL_EXECUTION_TRANSACTION_ACTION_REQUIRED");
        }
        return transactions.execute(status -> action.get());
    }
}
