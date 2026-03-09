package cn.lgs.orbisops.domain.repair.service;

import cn.lgs.orbisops.domain.repair.model.CodeDeliveryCandidate;
import cn.lgs.orbisops.domain.repair.model.CodeDeliveryMode;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspace;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceAggregate;

import java.util.regex.Pattern;

public final class CodeDeliveryPolicy {

    private static final Pattern BRANCH = Pattern.compile("[A-Za-z0-9._/-]{1,160}");

    public CodeDeliveryCandidate normalize(CodeDeliveryCandidate candidate) {
        CodeDeliveryCandidate value = candidate == null
                ? new CodeDeliveryCandidate(CodeDeliveryMode.LOCAL_BRANCH, "", "")
                : candidate;
        String title = text(value.title());
        if (title.length() > 240) throw new IllegalArgumentException("CODE_DELIVERY_TITLE_TOO_LONG");
        String baseBranch = text(value.baseBranch());
        if (value.mode() == CodeDeliveryMode.GITHUB_PR) {
            if (baseBranch.isBlank()) baseBranch = "main";
            requireBranch(baseBranch, "CODE_DELIVERY_BASE_BRANCH_INVALID");
        }
        return new CodeDeliveryCandidate(value.mode(), title, baseBranch);
    }

    public void requireDeliverable(RepairWorkspace workspace) {
        if (workspace == null) throw new IllegalArgumentException("REPAIR_WORKSPACE_REQUIRED");
        RepairWorkspaceAggregate.rehydrate(
                workspace.workspaceId(), workspace.projectId(), workspace.serviceId(), workspace.status())
                .requireDeliverable();
    }

    public void requireProvider(CodeDeliveryMode mode, boolean configured) {
        if (mode == CodeDeliveryMode.GITHUB_PR && !configured) {
            throw new IllegalStateException("GitHub PR Provider 未配置");
        }
    }

    public String branchName(RepairWorkspace workspace) {
        requireDeliverable(workspace);
        String workspaceId = workspace.workspaceId();
        String suffix = workspaceId.substring(Math.max(0, workspaceId.length() - 12));
        String branch = "ops-repair/" + workspace.serviceId() + "/" + suffix;
        requireBranch(branch, "CODE_DELIVERY_BRANCH_INVALID");
        return branch;
    }

    public String title(CodeDeliveryCandidate candidate, RepairWorkspace workspace) {
        CodeDeliveryCandidate normalized = normalize(candidate);
        String title = normalized.title();
        if (title.isBlank()) title = text(workspace.summary());
        if (title.isBlank()) title = "Repair " + workspace.workspaceId();
        if (title.length() > 240) throw new IllegalArgumentException("CODE_DELIVERY_TITLE_TOO_LONG");
        return title;
    }

    public String actor(String actor) {
        String normalized = text(actor);
        if (normalized.isBlank()) throw new IllegalArgumentException("REPAIR_ACTOR_REQUIRED");
        return normalized;
    }

    public String deliveryId(String deliveryId) {
        String normalized = text(deliveryId);
        if (normalized.isBlank()) throw new IllegalArgumentException("CODE_DELIVERY_ID_REQUIRED");
        return normalized;
    }

    private void requireBranch(String branch, String error) {
        if (!BRANCH.matcher(text(branch)).matches() || branch.contains("..") || branch.startsWith("-")) {
            throw new IllegalArgumentException(error);
        }
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
