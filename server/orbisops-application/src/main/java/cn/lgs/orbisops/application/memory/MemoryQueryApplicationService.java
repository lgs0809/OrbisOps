package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.ColdMemoryItemSnapshot;
import cn.lgs.orbisops.domain.memory.model.MemoryItemCandidate;
import cn.lgs.orbisops.domain.memory.model.MemoryMessageCandidate;
import cn.lgs.orbisops.domain.memory.model.MemorySelectionConfiguration;
import cn.lgs.orbisops.domain.memory.model.MemorySelectionResult;
import cn.lgs.orbisops.domain.memory.service.MemorySceneClassificationPolicy;
import cn.lgs.orbisops.domain.memory.service.MemorySelectionPolicy;

import java.util.ArrayList;
import java.util.List;

/**
 * Application use case that assembles runtime memory through retrieval, selection, rendering and reference creation.
 */
public class MemoryQueryApplicationService {

    private final MemoryRetrievalApplicationService retrievalService;
    private final MemoryContextRenderingApplicationService renderingService;
    private final MemorySelectionReferenceApplicationService referenceService;
    private final MemorySelectionPolicy selectionPolicy;
    private final MemorySceneClassificationPolicy scenePolicy;
    private final MemoryQueryFailurePort failurePort;

    public MemoryQueryApplicationService(MemoryRetrievalApplicationService retrievalService,
                                         MemoryContextRenderingApplicationService renderingService,
                                         MemorySelectionReferenceApplicationService referenceService,
                                         MemorySelectionPolicy selectionPolicy,
                                         MemorySceneClassificationPolicy scenePolicy,
                                         MemoryQueryFailurePort failurePort) {
        this.retrievalService = retrievalService;
        this.renderingService = renderingService;
        this.referenceService = referenceService;
        this.selectionPolicy = selectionPolicy == null ? new MemorySelectionPolicy() : selectionPolicy;
        this.scenePolicy = scenePolicy == null ? new MemorySceneClassificationPolicy() : scenePolicy;
        this.failurePort = failurePort == null ? (operation, error) -> { } : failurePort;
    }

    public MemoryQueryResult query(MemoryQueryCommand command) {
        if (command == null || !command.valid()) return MemoryQueryResult.empty();
        try {
            MemoryRetrievalResult retrieval = retrieval(command);
            MemorySelectionResult selection = selectionPolicy.select(
                    itemCandidates(retrieval.coldItems()),
                    messageCandidates(retrieval),
                    new MemorySelectionConfiguration(
                            command.recencyAware(),
                            command.recencyHalfLifeTurns()));
            MemoryContextRenderingResult rendered = renderingService == null
                    ? MemoryContextRenderingResult.empty()
                    : renderingService.render(new MemoryContextRenderingRequest(
                            retrieval.contextMemories(),
                            selection.items(),
                            selection.messages(),
                            command.itemMatchLimit(),
                            command.itemMatchLimit() + command.semanticTopK(),
                            command.hotMessageLimit() + command.semanticTopK(),
                            command.contextMaxChars()));
            List<MemorySelectionReference> references = referenceService == null
                    ? List.of()
                    : referenceService.assemble(retrieval.contextMemories());
            return new MemoryQueryResult(rendered.context(), references);
        } catch (RuntimeException error) {
            observe("query", error);
            if (error instanceof MemoryContextIntegrityException) throw error;
            return MemoryQueryResult.empty();
        }
    }

    private MemoryRetrievalResult retrieval(MemoryQueryCommand command) {
        if (retrievalService == null) return MemoryRetrievalResult.empty();
        MemoryRetrievalResult retrieval = retrievalService.retrieve(new MemoryRetrievalQuery(
                command.sessionId(),
                command.userId(),
                command.query(),
                scenePolicy.classify(command.explicitScene(), command.taskType(), command.query()),
                command.projectId(),
                command.itemMatchLimit(),
                command.hotMessageLimit(),
                command.semanticTopK(),
                Math.min(8, Math.max(2, command.itemMatchLimit())),
                command.timeoutMillis()));
        return retrieval == null ? MemoryRetrievalResult.empty() : retrieval;
    }

    private List<MemoryItemCandidate> itemCandidates(List<ColdMemoryItemSnapshot> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) return List.of();
        return snapshots.stream()
                .filter(snapshot -> snapshot != null)
                .map(this::itemCandidate)
                .toList();
    }

    private MemoryItemCandidate itemCandidate(ColdMemoryItemSnapshot snapshot) {
        return new MemoryItemCandidate(
                snapshot.sessionId(),
                snapshot.userId(),
                snapshot.memoryType(),
                snapshot.content(),
                snapshot.importance(),
                snapshot.tagsJson(),
                snapshot.sourceMessageRole(),
                snapshot.sourceMessageHash(),
                snapshot.metadata(),
                snapshot.createdAt());
    }

    private List<MemoryMessageCandidate> messageCandidates(MemoryRetrievalResult retrieval) {
        List<MemoryMessageCandidate> candidates = new ArrayList<>();
        addMessages(candidates, retrieval.hotMessages());
        addMessages(candidates, retrieval.semanticMessages());
        return List.copyOf(candidates);
    }

    private void addMessages(List<MemoryMessageCandidate> candidates, List<MemoryMessageView> messages) {
        if (messages == null || messages.isEmpty()) return;
        messages.stream()
                .filter(message -> message != null)
                .map(this::messageCandidate)
                .forEach(candidates::add);
    }

    private MemoryMessageCandidate messageCandidate(MemoryMessageView message) {
        return new MemoryMessageCandidate(
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
            // Memory queries remain fail-open even if diagnostics are unavailable.
        }
    }
}
