package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;
import cn.lgs.orbisops.domain.memory.model.MemoryExtractionDraft;
import cn.lgs.orbisops.domain.memory.model.MemoryItemCandidate;
import cn.lgs.orbisops.domain.memory.service.MemoryContentHashPolicy;
import cn.lgs.orbisops.domain.memory.service.MemoryExtractionPolicy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Application orchestration for optional model drafts and deterministic memory extraction. */
public class MemoryExtractionApplicationService {

    private final MemoryExtractionPolicy extractionPolicy;
    private final MemoryContentHashPolicy hashPolicy;
    private final MemoryModelExtractionPort modelExtractionPort;
    private final MemoryExtractionFailurePort failurePort;
    private final Supplier<String> createdAtSupplier;

    public MemoryExtractionApplicationService(MemoryExtractionPolicy extractionPolicy,
                                              MemoryContentHashPolicy hashPolicy,
                                              MemoryModelExtractionPort modelExtractionPort,
                                              MemoryExtractionFailurePort failurePort,
                                              Supplier<String> createdAtSupplier) {
        this.extractionPolicy = extractionPolicy == null ? new MemoryExtractionPolicy() : extractionPolicy;
        this.hashPolicy = hashPolicy == null ? new MemoryContentHashPolicy() : hashPolicy;
        this.modelExtractionPort = modelExtractionPort;
        this.failurePort = failurePort;
        this.createdAtSupplier = createdAtSupplier == null ? () -> "" : createdAtSupplier;
    }

    public static MemoryExtractionApplicationService rulesOnly(Supplier<String> createdAtSupplier) {
        return new MemoryExtractionApplicationService(
                new MemoryExtractionPolicy(),
                new MemoryContentHashPolicy(),
                null,
                null,
                createdAtSupplier);
    }

    public List<MemoryItemCandidate> extract(MemoryExtractionCommand command) {
        if (command == null || command.message() == null || !hasText(command.message().content())) {
            return List.of();
        }
        ColdMemoryMessageSnapshot message = command.message();
        String sourceHash = hashPolicy.stableHash(value(message.role()) + ":" + message.content());
        String createdAt = value(createdAtSupplier.get());
        List<MemoryItemCandidate> candidates = new ArrayList<>();
        if (command.modelEnabled() && modelExtractionPort != null) {
            try {
                addDrafts(candidates,
                        message,
                        modelExtractionPort.extract(message, command.modelMaxInputChars()),
                        sourceHash,
                        createdAt);
            } catch (RuntimeException error) {
                observeFailure("model-extraction", error);
            }
        }
        addDrafts(candidates, message, extractionPolicy.ruleDrafts(message), sourceHash, createdAt);
        return extractionPolicy.distinctAndLimit(candidates, command.maxItems());
    }

    private void addDrafts(List<MemoryItemCandidate> candidates,
                           ColdMemoryMessageSnapshot message,
                           List<MemoryExtractionDraft> drafts,
                           String sourceHash,
                           String createdAt) {
        if (drafts == null || drafts.isEmpty()) {
            return;
        }
        for (MemoryExtractionDraft draft : drafts) {
            MemoryItemCandidate candidate = extractionPolicy.materialize(message, draft, sourceHash, createdAt);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
    }

    private void observeFailure(String operation, RuntimeException error) {
        if (failurePort == null) {
            return;
        }
        try {
            failurePort.onFailure(operation, error);
        } catch (RuntimeException ignored) {
        }
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
