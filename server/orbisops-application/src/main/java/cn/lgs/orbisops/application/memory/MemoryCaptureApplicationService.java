package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.CapturedMemoryMessage;
import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;
import cn.lgs.orbisops.domain.memory.model.MemoryCaptureDraft;
import cn.lgs.orbisops.domain.memory.service.MemoryCapturePolicy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/** Application use case for preparing and capturing one runtime conversation message. */
public class MemoryCaptureApplicationService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final HotMemoryWritePort hotMemoryWritePort;
    private final ColdMemoryStoreApplicationService coldMemoryStore;
    private final MemoryPostProcessingApplicationService postProcessingService;
    private final MemoryCapturePolicy capturePolicy;
    private final Clock clock;
    private final MemoryCaptureFailurePort failurePort;
    private final ConcurrentMap<String, AtomicLong> sessionTurnCounters = new ConcurrentHashMap<>();

    public MemoryCaptureApplicationService(HotMemoryWritePort hotMemoryWritePort,
                                           ColdMemoryStoreApplicationService coldMemoryStore,
                                           MemoryPostProcessingApplicationService postProcessingService,
                                           MemoryCapturePolicy capturePolicy,
                                           Clock clock,
                                           MemoryCaptureFailurePort failurePort) {
        this.hotMemoryWritePort = hotMemoryWritePort;
        this.coldMemoryStore = coldMemoryStore;
        this.postProcessingService = postProcessingService;
        this.capturePolicy = capturePolicy == null ? new MemoryCapturePolicy() : capturePolicy;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
        this.failurePort = failurePort == null ? (operation, error) -> { } : failurePort;
    }

    public MemoryCaptureResult capture(MemoryCaptureCommand command) {
        if (command == null || !command.valid()) return MemoryCaptureResult.skipped();
        long nextTurnIndex = sessionTurnCounters
                .computeIfAbsent(command.sessionId().trim(), ignored -> new AtomicLong())
                .incrementAndGet();
        try {
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
                    nextTurnIndex,
                    createdAtEpochMillis,
                    createdAt);
            MemoryMessageView message = view(captured);
            if (hotMemoryWritePort == null) {
                throw new IllegalStateException("Hot memory write port is unavailable");
            }
            hotMemoryWritePort.append(message, command.bufferSize());
            if (coldMemoryStore != null) {
                coldMemoryStore.appendMessage(snapshot(captured));
            }
            if (postProcessingService != null) {
                postProcessingService.submit(new MemoryPostProcessingCommand(
                        message,
                        command.bufferSize(),
                        command.extractionAsyncEnabled()));
            }
            return MemoryCaptureResult.captured(message);
        } catch (RuntimeException error) {
            observe("capture", error);
            return MemoryCaptureResult.skipped();
        }
    }

    public void clearSessionState(String sessionId) {
        if (sessionId == null || sessionId.trim().isBlank()) return;
        sessionTurnCounters.remove(sessionId.trim());
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
