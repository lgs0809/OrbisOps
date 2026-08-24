package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.SkillTransactionPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Repository
public class SpringSkillTransactionAdapter implements SkillTransactionPort {

    private final TransactionTemplate transactionTemplate;

    public SpringSkillTransactionAdapter(
            @Qualifier("mysqlTransactionManager")
            ObjectProvider<PlatformTransactionManager> transactionManagerProvider) {
        PlatformTransactionManager transactionManager = transactionManagerProvider.getIfAvailable();
        this.transactionTemplate = transactionManager == null ? null : new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T required(Supplier<T> action) {
        if (action == null) throw new IllegalArgumentException("SKILL_TRANSACTION_ACTION_REQUIRED");
        if (transactionTemplate == null) {
            throw new IllegalStateException("SKILL_TRANSACTION_MANAGER_UNAVAILABLE");
        }
        return transactionTemplate.execute(status -> {
            T result=action.get();
            if (result == null) throw new IllegalStateException("SKILL_TRANSACTION_RETURNED_NULL");
            return result;
        });
    }
}
