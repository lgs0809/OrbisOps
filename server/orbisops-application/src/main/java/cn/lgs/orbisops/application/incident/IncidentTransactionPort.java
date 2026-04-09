package cn.lgs.orbisops.application.incident;

import java.util.function.Supplier;

public interface IncidentTransactionPort {
    <T> T required(Supplier<T> action);
}
