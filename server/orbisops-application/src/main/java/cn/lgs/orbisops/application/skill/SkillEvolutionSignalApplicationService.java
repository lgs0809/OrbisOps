package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.adapter.repository.ISkillEvolutionSignalRepository;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionHintSnapshot;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionSignalDraft;
import cn.lgs.orbisops.domain.skill.model.SkillEvolutionSignalSnapshot;
import cn.lgs.orbisops.domain.skill.service.SkillEvolutionSignalPolicy;

import java.util.List;

/** Application orchestration for Skill Evolution signal and authoring-hint lifecycle. */
public class SkillEvolutionSignalApplicationService {

    private final ISkillEvolutionSignalRepository repository;
    private final SkillEvolutionSignalPolicy policy;
    private final SkillEvolutionSignalIdPort idPort;
    private final SkillEvolutionSignalAuditPort auditPort;

    public SkillEvolutionSignalApplicationService(
            ISkillEvolutionSignalRepository repository,
            SkillEvolutionSignalPolicy policy,
            SkillEvolutionSignalIdPort idPort,
            SkillEvolutionSignalAuditPort auditPort) {
        if (repository == null) throw new IllegalArgumentException("SKILL_SIGNAL_REPOSITORY_REQUIRED");
        if (idPort == null) throw new IllegalArgumentException("SKILL_SIGNAL_ID_PORT_REQUIRED");
        this.repository = repository;
        this.policy = policy == null ? new SkillEvolutionSignalPolicy() : policy;
        this.idPort = idPort;
        this.auditPort = auditPort;
    }

    public SkillEvolutionSignalSnapshot record(SkillEvolutionSignalCommand command) {
        if (command == null) throw new IllegalArgumentException("SKILL_SIGNAL_COMMAND_REQUIRED");
        requireRepository("Skill Evolution Signal Store 未配置");
        SkillEvolutionSignalDraft draft = policy.draft(
                command.signalType(),
                command.projectId(),
                command.agentId(),
                command.runId(),
                command.sessionId(),
                command.payloadJson());
        String signalId = requireSignalId(idPort.newSignalId());
        SkillEvolutionSignalSnapshot stored = repository.saveIdempotent(
                new SkillEvolutionSignalSnapshot(
                        signalId,
                        policy.signalIdempotencyKey(draft),
                        draft.projectId(),
                        draft.agentId(),
                        draft.runId(),
                        draft.sessionId(),
                        draft.signalType(),
                        draft.payloadJson(),
                        policy.createdStatus(),
                        null));
        if (auditPort != null) auditPort.recordSignalCreated(stored);
        return stored;
    }

    public SkillEvolutionHintSnapshot createHint(SkillEvolutionHintCommand command) {
        if (command == null) throw new IllegalArgumentException("SKILL_HINT_COMMAND_REQUIRED");
        requireRepository("Skill Evolution Hint Store 未配置");
        SkillEvolutionHintSnapshot hint = new SkillEvolutionHintSnapshot(
                policy.hintId(command.signalId(), command.hintType(), command.contentJson()),
                command.signalId(),
                command.projectId(),
                command.runId(),
                command.hintType(),
                command.contentJson(),
                policy.createdStatus(),
                null);
        repository.saveHintIdempotent(hint);
        return hint;
    }

    public List<SkillEvolutionHintSnapshot> pendingHints(String projectId, int limit) {
        requireRepository("Skill Evolution Hint Store 未配置");
        String project = value(projectId);
        if (project.isBlank()) return List.of();
        return repository.findPendingHints(project, policy.pendingHintLimit(limit));
    }

    public List<String> markHintsConsumed(List<String> hintIds, String candidateId) {
        requireRepository("Skill Evolution Hint Store 未配置");
        List<String> ids = policy.consumableHintIds(hintIds);
        if (ids.isEmpty()) return List.of();
        for (String hintId : ids) repository.markHintConsumed(hintId);
        if (auditPort != null) auditPort.recordHintsConsumed(value(candidateId), ids);
        return ids;
    }

    private void requireRepository(String error) {
        if (!repository.available()) throw new IllegalStateException(error);
    }

    private String requireSignalId(String value) {
        String id = value(value);
        if (id.isBlank()) throw new IllegalStateException("SKILL_SIGNAL_ID_EMPTY");
        return id;
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
