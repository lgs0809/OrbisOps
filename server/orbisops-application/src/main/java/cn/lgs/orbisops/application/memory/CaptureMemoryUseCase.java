package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.MemoryCandidate;
import cn.lgs.orbisops.domain.memory.service.MemoryClassificationPolicy;
import cn.lgs.orbisops.domain.memory.service.MemoryContentPolicy;

import java.util.List;

/** Process manager for explicit user-requested Memory capture. */
public final class CaptureMemoryUseCase {

    private final GovernedMemoryApplicationService memoryApplication;
    private final MemoryClassificationPolicy classificationPolicy;
    private final MemoryContentPolicy contentPolicy;

    public CaptureMemoryUseCase(GovernedMemoryApplicationService memoryApplication) {
        this(memoryApplication, new MemoryClassificationPolicy(), new MemoryContentPolicy());
    }

    CaptureMemoryUseCase(
            GovernedMemoryApplicationService memoryApplication,
            MemoryClassificationPolicy classificationPolicy,
            MemoryContentPolicy contentPolicy) {
        if (memoryApplication == null) {
            throw new IllegalArgumentException("GOVERNED_MEMORY_APPLICATION_REQUIRED");
        }
        if (classificationPolicy == null) throw new IllegalArgumentException("MEMORY_CLASSIFICATION_POLICY_REQUIRED");
        if (contentPolicy == null) throw new IllegalArgumentException("MEMORY_CONTENT_POLICY_REQUIRED");
        this.memoryApplication = memoryApplication;
        this.classificationPolicy = classificationPolicy;
        this.contentPolicy = contentPolicy;
    }

    public CaptureMemoryResult capture(CaptureMemoryCommand command) {
        if (command == null) throw new IllegalArgumentException("MEMORY_CAPTURE_COMMAND_REQUIRED");
        MemoryCandidate candidate = classificationPolicy.classify(
                command.query(),
                command.userId(),
                command.projectId(),
                command.sessionId());
        contentPolicy.requireAllowed(candidate.content());
        if (!candidate.type().persistable()) {
            throw new IllegalArgumentException(
                    "MEMORY_UNCLASSIFIED：无法归类为长期偏好、工作流、项目事实或会话上下文，未写入 Memory");
        }
        GovernedMemoryCreationResult memory = memoryApplication.create(new GovernedMemoryCreateCommand(
                candidate.scope().name(),
                candidate.scopeId(),
                candidate.type().name(),
                candidate.content(),
                candidate.normalizedContent(),
                candidate.logicalKey(),
                command.userId(),
                command.projectId(),
                command.agentId(),
                command.sessionId(),
                "USER_ASSERTED",
                command.runId(),
                candidate.verified(),
                candidate.confidence(),
                command.riskLevel().isBlank() ? "LOW" : command.riskLevel(),
                List.of(),
                command.userId()));
        return new CaptureMemoryResult(memory);
    }
}
