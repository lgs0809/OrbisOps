package cn.lgs.orbisops.application.evidence;

import java.util.function.Supplier;

public interface EvidenceTransactionPort {

    <T> T required(Supplier<T> action);
}
