package cn.lgs.orbisops.application.alert;

import java.util.function.Supplier;

public interface AlertRuleTransactionPort {
    <T> T required(Supplier<T> action);
}
