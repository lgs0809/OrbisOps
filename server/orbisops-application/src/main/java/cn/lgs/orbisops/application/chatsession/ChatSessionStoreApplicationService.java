package cn.lgs.orbisops.application.chatsession;

import cn.lgs.orbisops.domain.chatsession.adapter.repository.IChatSessionRepository;
import cn.lgs.orbisops.domain.chatsession.model.ChatMessageSnapshot;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionParticipant;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionParticipantReplacement;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionSearchCriteria;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionSnapshot;
import cn.lgs.orbisops.domain.chatsession.model.ChatSessionUpdate;

import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Application use case that selects the authoritative Chat Session store,
 * enforces fail-closed production behavior and owns explicit fallback routing.
 */
public class ChatSessionStoreApplicationService {

    private final IChatSessionRepository repository;
    private final ChatSessionMemoryCatalog fallbackCatalog;
    private final BooleanSupplier fallbackAllowed;
    private final ChatSessionStoreFailurePort failurePort;

    public ChatSessionStoreApplicationService(IChatSessionRepository repository,
                                              ChatSessionMemoryCatalog fallbackCatalog,
                                              BooleanSupplier fallbackAllowed,
                                              ChatSessionStoreFailurePort failurePort) {
        this.repository = repository;
        this.fallbackCatalog = fallbackCatalog == null
                ? new ChatSessionMemoryCatalog()
                : fallbackCatalog;
        this.fallbackAllowed = fallbackAllowed == null ? () -> false : fallbackAllowed;
        this.failurePort = failurePort == null ? ignored -> { } : failurePort;
    }

    public ChatSessionSnapshot insert(ChatSessionSnapshot session) {
        return execute(
                () -> {
                    repository.insert(session);
                    return repository.findById(session.sessionId()).orElse(session);
                },
                () -> {
                    fallbackCatalog.save(session);
                    return session;
                });
    }

    public ChatSessionSnapshot insertIfAbsent(ChatSessionSnapshot session) {
        return execute(
                () -> {
                    repository.insertIfAbsent(session);
                    return repository.findById(session.sessionId()).orElse(session);
                },
                () -> {
                    fallbackCatalog.save(session);
                    return session;
                });
    }

    public Optional<ChatSessionSnapshot> find(String sessionId) {
        return execute(
                () -> repository.findById(sessionId),
                () -> fallbackCatalog.find(sessionId));
    }

    public List<ChatSessionSnapshot> search(ChatSessionSearchCriteria criteria) {
        return execute(
                () -> repository.search(criteria),
                () -> fallbackCatalog.search(criteria));
    }

    public List<ChatMessageSnapshot> messages(String sessionId, int limit) {
        return execute(
                () -> repository.messages(sessionId, limit),
                List::of);
    }

    public Optional<ChatSessionSnapshot> update(ChatSessionUpdate update) {
        if (!repositoryReady()) {
            return fallbackCatalog.update(update);
        }
        try {
            if (!repository.update(update)) {
                throw new SessionVersionConflict();
            }
            return repository.findById(update.sessionId());
        } catch (SessionVersionConflict conflict) {
            throw conflict;
        } catch (RuntimeException error) {
            handleFailure(error);
            return fallbackCatalog.update(update);
        }
    }

    public Optional<String> activeParticipantRole(String sessionId, String userId) {
        return execute(
                () -> repository.activeParticipantRole(sessionId, userId),
                () -> fallbackCatalog.activeParticipantRole(sessionId, userId));
    }

    public List<ChatSessionParticipant> participants(String sessionId) {
        return execute(
                () -> repository.participants(sessionId),
                () -> fallbackCatalog.participants(sessionId));
    }

    public boolean replaceParticipants(ChatSessionParticipantReplacement replacement) {
        return execute(
                () -> repository.replaceParticipants(replacement),
                () -> fallbackCatalog.replaceParticipants(replacement));
    }

    public boolean markDeleted(String sessionId) {
        fallbackCatalog.remove(sessionId);
        return execute(
                () -> repository.markDeleted(sessionId),
                () -> true);
    }

    public void touch(String sessionId, String title, String lastMessage) {
        execute(
                () -> {
                    repository.touch(sessionId, title);
                    return Boolean.TRUE;
                },
                () -> {
                    fallbackCatalog.touch(sessionId, title, lastMessage);
                    return Boolean.TRUE;
                });
    }

    public int fallbackSize() {
        return fallbackCatalog.size();
    }

    private <T> T execute(Supplier<T> repositoryAction, Supplier<T> fallbackAction) {
        if (!repositoryReady()) {
            return fallbackAction.get();
        }
        try {
            return repositoryAction.get();
        } catch (RuntimeException error) {
            handleFailure(error);
            return fallbackAction.get();
        }
    }

    private boolean repositoryReady() {
        if (repository == null) {
            requireFallbackAllowedForUnavailableStore();
            return false;
        }
        try {
            if (repository.available()) return true;
            requireFallbackAllowedForUnavailableStore();
            return false;
        } catch (RuntimeException error) {
            handleFailure(error);
            return false;
        }
    }

    private void requireFallbackAllowedForUnavailableStore() {
        if (!fallbackAllowed.getAsBoolean()) {
            throw new IllegalStateException("Chat Session Store 不可用，生产主链路禁止内存降级");
        }
    }

    private void handleFailure(RuntimeException error) {
        if (!fallbackAllowed.getAsBoolean()) {
            throw new IllegalStateException("Chat Session Store 写入或查询失败，已 fail closed", error);
        }
        failurePort.onFallback(error);
    }

    private static final class SessionVersionConflict extends IllegalStateException {
        private SessionVersionConflict() {
            super("SESSION_VERSION_CONFLICT：会话已被其他用户或请求更新");
        }
    }
}
