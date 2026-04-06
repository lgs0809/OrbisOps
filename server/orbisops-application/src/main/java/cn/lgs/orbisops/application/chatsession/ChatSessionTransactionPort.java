package cn.lgs.orbisops.application.chatsession;

import java.util.function.Supplier;

/** Transaction boundary for multi-repository Chat Session use cases. */
public interface ChatSessionTransactionPort {

    <T> T required(Supplier<T> action);
}
