package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.CapturedMemoryMessage;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;
import cn.lgs.orbisops.domain.memory.model.MemoryCaptureDraft;
import cn.lgs.orbisops.domain.memory.service.MemoryCapturePolicy;
import cn.lgs.orbisops.domain.memory.adapter.repository.IConversationMemoryRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Application use case for preparing and capturing one runtime conversation message. */
public class MemoryCaptureApplicationService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final HotMemoryWritePort hotMemoryWritePort;
    private final ColdMemoryStoreApplicationService coldMemoryStore;
    private final MemoryPostProcessingApplicationService postProcessingService;
    private final MemoryCapturePolicy capturePolicy;
    private final Clock clock;
    private final MemoryCaptureFailurePort failurePort;
    private final IConversationMemoryRepository conversationRepository;

    public MemoryCaptureApplicationService(HotMemoryWritePort hotMemoryWritePort,
                                           ColdMemoryStoreApplicationService coldMemoryStore,
                                           MemoryPostProcessingApplicationService postProcessingService,
                                           MemoryCapturePolicy capturePolicy,
                                           Clock clock,
                                           MemoryCaptureFailurePort failurePort) {
        this(hotMemoryWritePort, coldMemoryStore, postProcessingService, capturePolicy, clock, failurePort, null);
    }

    public MemoryCaptureApplicationService(HotMemoryWritePort hotMemoryWritePort,
                                           ColdMemoryStoreApplicationService coldMemoryStore,
                                           MemoryPostProcessingApplicationService postProcessingService,
                                           MemoryCapturePolicy capturePolicy, Clock clock,
                                           MemoryCaptureFailurePort failurePort,
                                           IConversationMemoryRepository conversationRepository) {
        this.hotMemoryWritePort = hotMemoryWritePort;
        this.coldMemoryStore = coldMemoryStore;
        this.postProcessingService = postProcessingService;
        this.capturePolicy = capturePolicy == null ? new MemoryCapturePolicy() : capturePolicy;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
        this.failurePort = failurePort == null ? (operation, error) -> { } : failurePort;
        this.conversationRepository = conversationRepository;
    }

    public MemoryCaptureResult capture(MemoryCaptureCommand command) {
        if (command == null || !command.valid()) return MemoryCaptureResult.skipped();
        try {
            if (conversationRepository == null) {
                throw new IllegalStateException("CONVERSATION_MEMORY_DATABASE_UNAVAILABLE");
            }
            Instant createdAtInstant = clock.instant();
            long createdAtEpochMillis = createdAtInstant.toEpochMilli();
            String createdAt = FORMATTER.format(LocalDateTime.ofInstant(createdAtInstant, clock.getZone()));
            CapturedMemoryMessage captured = capturePolicy.prepare(
                    new MemoryCaptureDraft(
                            command.sessionId(),
                            command.userId(),
                            command.role(),
                            command.content(),
                            command.metadata()),
                    1L,
                    createdAtEpochMillis,
                    createdAt);
            // Allocate the sequence, append the source and enqueue replay facts atomically, before caches/dispatch.
            ColdMemoryMessageSnapshot persisted = conversationRepository.capture(snapshot(captured), command.bufferSize());
            MemoryMessageView message = new MemoryMessageView(persisted.sessionId(), persisted.userId(), persisted.role(),
                    persisted.content(), persisted.createdAt(), persisted.metadata());
            try {
                if (hotMemoryWritePort != null) hotMemoryWritePort.append(message, command.bufferSize());
            } catch (RuntimeException cacheFailure) {
                observe("hot-projection", cacheFailure);
            }
            if (postProcessingService != null) {
                try {
                    postProcessingService.submit(new MemoryPostProcessingCommand(
                            message, command.bufferSize(), command.extractionAsyncEnabled()));
                } catch (RuntimeException dispatchFailure) {
                    observe("post-processing-submit", dispatchFailure);
                }
            }
            return MemoryCaptureResult.captured(message);
        } catch (RuntimeException error) {
            observe("capture", error);
            return MemoryCaptureResult.skipped();
        }
    }

    public void clearSessionState(String sessionId) {
        // Sequence allocation is persistent and must never reset with an in-process cache.
    }

    private MemoryMessageView view(CapturedMemoryMessage message) {
        return new MemoryMessageView(
                message.sessionId(),
                message.userId(),
                message.role(),
                message.content(),
                message.createdAt(),
                message.metadata());
    }

    private ColdMemoryMessageSnapshot snapshot(CapturedMemoryMessage message) {
        return new ColdMemoryMessageSnapshot(
                message.sessionId(),
                message.userId(),
                message.role(),
                message.content(),
                message.createdAt(),
                message.metadata());
    }

    private void observe(String operation, RuntimeException error) {
        try {
            failurePort.onFailure(operation, error);
        } catch (RuntimeException ignored) {
            // Capture remains fail-open even if diagnostics are unavailable.
        }
    }
}
