package cn.lgs.orbisops.application.changepackage;

import java.util.function.Supplier;

public interface ChangePackageTransactionPort {

    <T> T required(Supplier<T> action);
}
