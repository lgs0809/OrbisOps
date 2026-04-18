package cn.lgs.orbisops.domain.changepackage.service;

import cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus.APPROVED;
import static cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus.DRAFT;
import static cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus.LANDED;
import static cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus.LANDING_FAILED;
import static cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus.LANDING_RUNNING;
import static cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus.NEEDS_REPLAN;
import static cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus.READY_FOR_REVIEW;
import static cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus.REJECTED;
import static cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus.REVIEWING;
import static cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus.REVISING;
import static cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus.VALIDATING;
import static cn.lgs.orbisops.domain.changepackage.model.ChangePackageStatus.VALIDATION_FAILED;

public final class ChangePackageStateMachine {

    private final Map<ChangePackageStatus, Set<ChangePackageStatus>> transitions;

    public ChangePackageStateMachine() {
        EnumMap<ChangePackageStatus, Set<ChangePackageStatus>> allowed = new EnumMap<>(ChangePackageStatus.class);
        allowed.put(DRAFT, EnumSet.of(REVISING, READY_FOR_REVIEW, VALIDATION_FAILED));
        allowed.put(VALIDATING, EnumSet.of(READY_FOR_REVIEW, VALIDATION_FAILED));
        allowed.put(VALIDATION_FAILED, EnumSet.of(REVISING, READY_FOR_REVIEW, VALIDATION_FAILED));
        allowed.put(REVISING, EnumSet.of(REVISING, READY_FOR_REVIEW, VALIDATION_FAILED));
        allowed.put(READY_FOR_REVIEW, EnumSet.of(REVIEWING, READY_FOR_REVIEW, VALIDATION_FAILED));
        allowed.put(REVIEWING, EnumSet.of(APPROVED, REJECTED));
        allowed.put(APPROVED, EnumSet.of(LANDING_RUNNING));
        allowed.put(LANDING_RUNNING, EnumSet.of(LANDED, LANDING_FAILED, NEEDS_REPLAN));
        allowed.put(LANDING_FAILED, EnumSet.of(LANDING_RUNNING, REVISING));
        allowed.put(NEEDS_REPLAN, EnumSet.of(REVISING));
        transitions = Map.copyOf(allowed);
    }

    public void requireTransition(String from, String to) {
        requireTransition(ChangePackageStatus.require(from), ChangePackageStatus.require(to));
    }

    public void requireTransition(ChangePackageStatus from, ChangePackageStatus to) {
        if (from == null || to == null || !transitions.getOrDefault(from, Set.of()).contains(to)) {
            throw new IllegalStateException("CHANGE_PACKAGE_TRANSITION_REJECTED:"
                    + (from == null ? "UNKNOWN" : from.name()) + "->"
                    + (to == null ? "UNKNOWN" : to.name()));
        }
    }

    public boolean canTransition(ChangePackageStatus from, ChangePackageStatus to) {
        return from != null && to != null && transitions.getOrDefault(from, Set.of()).contains(to);
    }

    public Set<ChangePackageStatus> allowedTargets(ChangePackageStatus from) {
        return Set.copyOf(transitions.getOrDefault(from, Set.of()));
    }
}
