package cn.lgs.orbisops.application.repair;

import cn.lgs.orbisops.domain.repair.adapter.repository.ICodeDeliveryRepository;
import cn.lgs.orbisops.domain.repair.model.CodeDelivery;
import cn.lgs.orbisops.domain.repair.model.CodeDeliveryBranch;
import cn.lgs.orbisops.domain.repair.model.CodeDeliveryCandidate;
import cn.lgs.orbisops.domain.repair.model.CodeDeliveryCapabilities;
import cn.lgs.orbisops.domain.repair.model.CodeDeliveryCiSnapshot;
import cn.lgs.orbisops.domain.repair.model.CodeDeliveryMode;
import cn.lgs.orbisops.domain.repair.model.CodeDeliveryPullRequest;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspace;
import cn.lgs.orbisops.domain.repair.service.CodeDeliveryPolicy;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Supplier;

public final class CodeDeliveryApplicationService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RepairWorkspaceApplicationService workspaces;
    private final ICodeDeliveryRepository deliveries;
    private final CodeDeliveryGitPort git;
    private final CodeDeliveryProviderPort provider;
    private final Supplier<String> deliveryIdSupplier;
    private final Clock clock;
    private final RepairAuditPort audit;
    private final RepairTransactionPort transactions;
    private final CodeDeliveryPolicy policy;

    public CodeDeliveryApplicationService(
            RepairWorkspaceApplicationService workspaces,
            ICodeDeliveryRepository deliveries,
            CodeDeliveryGitPort git,
            CodeDeliveryProviderPort provider,
            Supplier<String> deliveryIdSupplier,
            Clock clock,
            RepairAuditPort audit,
            RepairTransactionPort transactions) {
        this(workspaces, deliveries, git, provider, deliveryIdSupplier, clock, audit, transactions,
                new CodeDeliveryPolicy());
    }

    CodeDeliveryApplicationService(
            RepairWorkspaceApplicationService workspaces,
            ICodeDeliveryRepository deliveries,
            CodeDeliveryGitPort git,
            CodeDeliveryProviderPort provider,
            Supplier<String> deliveryIdSupplier,
            Clock clock,
            RepairAuditPort audit,
            RepairTransactionPort transactions,
            CodeDeliveryPolicy policy) {
        if (workspaces == null) throw new IllegalArgumentException("REPAIR_WORKSPACE_APPLICATION_REQUIRED");
        if (deliveries == null) throw new IllegalArgumentException("CODE_DELIVERY_REPOSITORY_REQUIRED");
        if (git == null) throw new IllegalArgumentException("CODE_DELIVERY_GIT_PORT_REQUIRED");
        if (provider == null) throw new IllegalArgumentException("CODE_DELIVERY_PROVIDER_PORT_REQUIRED");
        if (deliveryIdSupplier == null) throw new IllegalArgumentException("CODE_DELIVERY_ID_SUPPLIER_REQUIRED");
        if (audit == null) throw new IllegalArgumentException("REPAIR_AUDIT_PORT_REQUIRED");
        if (transactions == null) throw new IllegalArgumentException("REPAIR_TRANSACTION_PORT_REQUIRED");
        if (policy == null) throw new IllegalArgumentException("CODE_DELIVERY_POLICY_REQUIRED");
        this.workspaces = workspaces;
        this.deliveries = deliveries;
        this.git = git;
        this.provider = provider;
        this.deliveryIdSupplier = deliveryIdSupplier;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
        this.audit = audit;
        this.transactions = transactions;
        this.policy = policy;
    }

    public CodeDeliveryCapabilities capabilities() {
        boolean github = provider.configured();
        return new CodeDeliveryCapabilities(true, github, github, false);
    }

    public synchronized CodeDelivery publish(
            String workspaceId,
            CodeDeliveryCandidate candidate,
            String actor) {
        RepairWorkspace workspace = workspaces.get(workspaceId);
        policy.requireDeliverable(workspace);
        CodeDeliveryCandidate normalized = policy.normalize(candidate);
        String operator = policy.actor(actor);
        policy.requireProvider(normalized.mode(), provider.configured());
        CodeDelivery existing = deliveries.findByWorkspaceAndMode(
                workspace.workspaceId(), normalized.mode()).orElse(null);
        if (existing != null) {
            audit.record(new RepairAuditEvent(
                    existing.projectId(), "publish-code", workspace.workspaceId(), workspace, existing));
            return existing;
        }

        String branchName = policy.branchName(workspace);
        String title = policy.title(normalized, workspace);
        Path worktree = workspaces.worktreePath(workspace.workspaceId());
        CodeDeliveryBranch branch = git.publishBranch(
                workspace, worktree, branchName, title,
                normalized.mode() == CodeDeliveryMode.GITHUB_PR);

        String pullRequestUrl = "";
        String ciStatus = "LOCAL_VERIFIED";
        if (normalized.mode() == CodeDeliveryMode.GITHUB_PR) {
            CodeDeliveryPullRequest pullRequest = provider.openPullRequest(
                    branch.branchName(), title, normalized.baseBranch(), workspace.workspaceId());
            pullRequestUrl = pullRequest.url();
            ciStatus = "QUEUED";
        }
        String now = now();
        CodeDelivery delivery = new CodeDelivery(
                deliveryIdSupplier.get(), workspace.workspaceId(), workspace.projectId(), workspace.serviceId(),
                normalized.mode(), branch.branchName(), branch.commitSha(), pullRequestUrl,
                ciStatus, "", operator, now, now);
        return transactions.required(() -> {
            CodeDelivery saved = deliveries.save(delivery);
            audit.record(new RepairAuditEvent(
                    saved.projectId(), "publish-code", workspace.workspaceId(), workspace, saved));
            return saved;
        });
    }

    public List<CodeDelivery> list(String workspaceId) {
        RepairWorkspace workspace = workspaces.get(workspaceId);
        return deliveries.list(workspace.workspaceId());
    }

    public CodeDelivery refreshCi(String deliveryId, String actor) {
        String id = policy.deliveryId(deliveryId);
        String operator = policy.actor(actor);
        CodeDelivery before = deliveries.find(id)
                .orElseThrow(() -> new IllegalArgumentException("代码交付记录不存在：" + id));
        if (before.mode() == CodeDeliveryMode.GITHUB_PR && provider.configured()) {
            CodeDeliveryCiSnapshot snapshot = provider.latestCi(before.branchName()).orElse(null);
            if (snapshot != null) {
                CodeDelivery updated = before.refreshed(snapshot.status(), snapshot.url(), now());
                return transactions.required(() -> {
                    CodeDelivery saved = deliveries.save(updated);
                    audit.record(new RepairAuditEvent(
                            saved.projectId(), "refresh-ci", saved.deliveryId(), before,
                            new CodeDeliveryRefreshAuditResult(saved, operator)));
                    return saved;
                });
            }
        }
        audit.record(new RepairAuditEvent(
                before.projectId(), "refresh-ci", before.deliveryId(), before,
                new CodeDeliveryRefreshAuditResult(before, operator)));
        return before;
    }

    private String now() {
        return TIME.format(LocalDateTime.now(clock));
    }

    public record CodeDeliveryRefreshAuditResult(CodeDelivery delivery, String actor) {
    }
}
