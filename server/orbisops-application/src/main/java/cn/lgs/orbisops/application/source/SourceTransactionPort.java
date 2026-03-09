package cn.lgs.orbisops.application.source;

import java.util.function.Supplier;

public interface SourceTransactionPort {
    <T> T required(Supplier<T> action);
}
