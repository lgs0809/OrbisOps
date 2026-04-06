package cn.lgs.orbisops.application.chatsession;

/** Optional outer-layer observer for explicit Chat Session store fallback events. */
@FunctionalInterface
public interface ChatSessionStoreFailurePort {

    void onFallback(RuntimeException error);
}
