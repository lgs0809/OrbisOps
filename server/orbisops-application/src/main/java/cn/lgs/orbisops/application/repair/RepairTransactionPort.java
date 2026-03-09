package cn.lgs.orbisops.application.repair;

import java.util.function.Supplier;

public interface RepairTransactionPort {
    <T> T required(Supplier<T> action);
}
