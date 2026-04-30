package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillLifecycleDecision;
import cn.lgs.orbisops.domain.skill.model.SkillLifecycleProposal;
import cn.lgs.orbisops.domain.skill.model.SkillLineageEdge;
import cn.lgs.orbisops.domain.skill.service.SkillLifecyclePolicy;

import java.time.Instant;
import java.util.List;

public final class SkillLifecycleApplicationService {

    private final SkillLifecyclePort port;
    private final SkillLifecyclePolicy policy;

    public SkillLifecycleApplicationService(SkillLifecyclePort port) {
        this(port, new SkillLifecyclePolicy());
    }

    SkillLifecycleApplicationService(
            SkillLifecyclePort port,
            SkillLifecyclePolicy policy) {
        if (port == null || policy == null) {
            throw new IllegalArgumentException("SKILL_LIFECYCLE_DEPENDENCY_REQUIRED");
        }
        this.port = port;
        this.policy = policy;
    }

    public SkillLifecycleDecision evaluate(
            SkillLifecycleProposal proposal,
            Instant decidedAt) {
        port.saveProposal(proposal);
        SkillLifecycleDecision decision = policy.evaluate(proposal, decidedAt);
        port.saveDecision(decision);
        if (decision.approved() && !decision.lineageEdges().isEmpty()) {
            port.saveLineage(decision.lineageEdges());
        }
        return decision;
    }

    public List<SkillLineageEdge> lineage(String skillId, int limit) {
        String id = skillId == null ? "" : skillId.trim();
        if (id.isBlank()) throw new IllegalArgumentException("SKILL_LINEAGE_SKILL_ID_REQUIRED");
        return List.copyOf(port.lineage(id, limit <= 0 ? 50 : Math.min(limit, 500)));
    }
}
