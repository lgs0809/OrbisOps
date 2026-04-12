package cn.lgs.orbisops.application.analysis;

import java.util.function.Supplier;

public interface AnalysisTaskTransactionPort {

    <T> T required(Supplier<T> action);
}
