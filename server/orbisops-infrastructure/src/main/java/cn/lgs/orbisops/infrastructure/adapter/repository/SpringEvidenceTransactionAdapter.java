package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.evidence.EvidenceTransactionPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

@Component
public class SpringEvidenceTransactionAdapter implements EvidenceTransactionPort {

    private final TransactionTemplate transactions;

    public SpringEvidenceTransactionAdapter(
            @Qualifier("mysqlTransactionManager") PlatformTransactionManager transactionManager) {
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public <T> T required(Supplier<T> action) {
        if (action == null) throw new IllegalArgumentException("EVIDENCE_TRANSACTION_ACTION_REQUIRED");
        return transactions.execute(status -> action.get());
    }
}
