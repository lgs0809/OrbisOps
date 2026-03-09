package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.agentdefinition.AgentDefinitionTransactionPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/** Spring transaction implementation for Agent Definition mutations. */
@Repository
public class SpringAgentDefinitionTransactionAdapter
        implements AgentDefinitionTransactionPort {

    private final TransactionTemplate transactionTemplate;

    public SpringAgentDefinitionTransactionAdapter(
            @Qualifier("mysqlTransactionManager")
            ObjectProvider<PlatformTransactionManager> transactionManagerProvider) {
        PlatformTransactionManager transactionManager =
                transactionManagerProvider.getIfAvailable();
        this.transactionTemplate = transactionManager == null
                ? null
                : new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T required(Supplier<T> action) {
        if (action == null) {
            throw new IllegalArgumentException(
                    "AGENT_DEFINITION_TRANSACTION_ACTION_REQUIRED");
        }
        if (transactionTemplate == null) {
            throw new IllegalStateException(
                    "AGENT_DEFINITION_TRANSACTION_MANAGER_UNAVAILABLE");
        }
        T result = transactionTemplate.execute(status -> action.get());
        if (result == null) {
            throw new IllegalStateException(
                    "AGENT_DEFINITION_TRANSACTION_RETURNED_NULL");
        }
        return result;
    }
}
