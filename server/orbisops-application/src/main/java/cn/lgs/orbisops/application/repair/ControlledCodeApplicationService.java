package cn.lgs.orbisops.application.repair;

import cn.lgs.orbisops.domain.repair.model.ControlledCodeAction;
import cn.lgs.orbisops.domain.repair.model.ControlledCodeEffect;
import cn.lgs.orbisops.domain.repair.model.ControlledCodeResult;
import cn.lgs.orbisops.domain.repair.model.RepairCommitResult;
import cn.lgs.orbisops.domain.repair.model.RepairDiffSnapshot;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspace;
import cn.lgs.orbisops.domain.repair.model.RepairWriterLease;
import cn.lgs.orbisops.domain.repair.service.ControlledCodePolicy;
import cn.lgs.orbisops.domain.source.model.SourceFile;
import cn.lgs.orbisops.domain.source.model.SourceRepository;
import cn.lgs.orbisops.domain.source.model.SourceSearchHit;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ControlledCodeApplicationService {

    private final ControlledCodeSourcePort sources;
    private final RepairWorkspaceApplicationService workspaces;
    private final ControlledCodeFilePort files;
    private final ControlledCodeRemotePort remote;
    private final ControlledCodeAuditPort audit;
    private final ControlledCodeToolResultPort toolResults;
    private final ControlledCodeProofPort proofs;
    private final ControlledCodePolicy policy;
    private final ConcurrentHashMap<String, String> readObservations = new ConcurrentHashMap<>();

    public ControlledCodeApplicationService(
            ControlledCodeSourcePort sources,
            RepairWorkspaceApplicationService workspaces,
            ControlledCodeFilePort files,
            ControlledCodeAuditPort audit,
            ControlledCodeToolResultPort toolResults,
            ControlledCodeProofPort proofs) {
        this(sources, workspaces, files, null, audit, toolResults, proofs, new ControlledCodePolicy());
    }

    public ControlledCodeApplicationService(
            ControlledCodeSourcePort sources,
            RepairWorkspaceApplicationService workspaces,
            ControlledCodeFilePort files,
            ControlledCodeRemotePort remote,
            ControlledCodeAuditPort audit,
            ControlledCodeToolResultPort toolResults,
            ControlledCodeProofPort proofs) {
        this(sources, workspaces, files, remote, audit, toolResults, proofs, new ControlledCodePolicy());
    }

    ControlledCodeApplicationService(
            ControlledCodeSourcePort sources,
            RepairWorkspaceApplicationService workspaces,
            ControlledCodeFilePort files,
            ControlledCodeRemotePort remote,
            ControlledCodeAuditPort audit,
            ControlledCodeToolResultPort toolResults,
            ControlledCodeProofPort proofs,
            ControlledCodePolicy policy) {
        if (sources == null) throw new IllegalArgumentException("CONTROLLED_CODE_SOURCE_PORT_REQUIRED");
        if (workspaces == null) throw new IllegalArgumentException("CONTROLLED_CODE_WORKSPACE_APPLICATION_REQUIRED");
        if (files == null) throw new IllegalArgumentException("CONTROLLED_CODE_FILE_PORT_REQUIRED");
        if (audit == null) throw new IllegalArgumentException("CONTROLLED_CODE_AUDIT_PORT_REQUIRED");
        if (toolResults == null) throw new IllegalArgumentException("CONTROLLED_CODE_RESULT_PORT_REQUIRED");
        if (proofs == null) throw new IllegalArgumentException("CONTROLLED_CODE_PROOF_PORT_REQUIRED");
        if (policy == null) throw new IllegalArgumentException("CONTROLLED_CODE_POLICY_REQUIRED");
        this.sources = sources;
        this.workspaces = workspaces;
        this.files = files;
        this.remote = remote;
        this.audit = audit;
        this.toolResults = toolResults;
        this.proofs = proofs;
        this.policy = policy;
    }

    public ControlledCodeResult.Read read(ControlledCodeCommands.Read query, String actor) {
        if (query == null) throw new IllegalArgumentException("CONTROLLED_CODE_READ_REQUIRED");
        String path = policy.readablePath(query.path());
        int startLine = policy.readStart(query.startLine());
        int limit = policy.readLimit(query.limit());
        String workspaceId = policy.value(query.workspaceId());
        ControlledCodeResult.Read result;
        String projectId;
        if (!workspaceId.isBlank()) {
            RepairWorkspace workspace = workspaces.get(workspaceId);
            SourceRepository repository = repository(workspace);
            String content;
            String hash;
            if (isRemote(repository)) {
                ControlledCodeRemotePort.RemoteRead read = remote.read(repository, workspace, workspace.baseCommit(), path);
                content = read.content();
                hash = read.sha256();
            } else {
                content = files.read(workspaces.worktreePath(workspaceId), path, true);
                hash = policy.sha256(content);
            }
            readObservations.put(readKey(workspaceId, path), hash);
            result = page(
                    workspace.projectId(), "", workspaceId, "", path,
                    content, hash, startLine, limit);
            projectId = workspace.projectId();
        } else {
            projectId = policy.required(query.projectId(), "读取仓库文件必须提供 projectId");
            String repositoryId = policy.required(query.repositoryId(), "读取仓库文件必须提供 repositoryId");
            SourceFile source = sources.readFile(
                    projectId, repositoryId, policy.value(query.revision()), path);
            String masked = policy.maskSecrets(source.content());
            result = page(
                    projectId, repositoryId, "", source.commitSha(), source.path(),
                    masked, policy.sha256(masked), startLine, limit);
        }
        audit(projectId, actor, ControlledCodeAction.READ, path, "LOW", "SUCCESS", result);
        return result;
    }

    public ControlledCodeResult.Search grep(ControlledCodeCommands.Grep query, String actor) {
        if (query == null) throw new IllegalArgumentException("CONTROLLED_CODE_GREP_REQUIRED");
        String text = policy.required(query.query(), "code.grep 必须提供 query");
        int limit = policy.grepLimit(query.limit());
        String workspaceId = policy.value(query.workspaceId());
        List<ControlledCodeResult.SearchHit> hits = new ArrayList<>();
        String projectId;
        if (!workspaceId.isBlank()) {
            RepairWorkspace workspace = workspaces.get(workspaceId);
            projectId = workspace.projectId();
            SourceRepository repository = repository(workspace);
            if (isRemote(repository)) {
                for (ControlledCodeRemotePort.RemoteSearchHit hit : remote.search(
                        repository, workspace, workspace.baseCommit(), text, limit,
                        query.regex(), query.caseSensitive(), policy.value(query.glob()))) {
                    hits.add(new ControlledCodeResult.SearchHit(
                            hit.path(), hit.line(), policy.maskSecrets(hit.text()), "MCP_WORKTREE_SEARCH"));
                }
            } else {
                for (ControlledCodeFilePort.SearchHit hit : files.grep(
                    workspaces.worktreePath(workspaceId), policy.value(query.glob()).isBlank() ? "**/*" : query.glob(),
                    text, query.regex(), query.caseSensitive(), limit)) {
                    hits.add(new ControlledCodeResult.SearchHit(
                            hit.path(), hit.line(), policy.maskSecrets(hit.text()), "WORKTREE_GREP"));
                }
            }
        } else {
            projectId = policy.required(query.projectId(), "搜索仓库必须提供 projectId");
            String repositoryId = policy.required(query.repositoryId(), "搜索仓库必须提供 repositoryId");
            List<SourceSearchHit> sourceHits = sources.search(
                    projectId, repositoryId, policy.value(query.revision()), text, limit);
            for (SourceSearchHit hit : sourceHits) {
                hits.add(new ControlledCodeResult.SearchHit(
                        hit.path(), Math.max(1, hit.line()), policy.maskSecrets(hit.text()),
                        "REGISTERED_REPO_GREP"));
            }
        }
        ControlledCodeResult.Search result = new ControlledCodeResult.Search(hits);
        audit(projectId, actor, ControlledCodeAction.GREP, text, "LOW", "SUCCESS", result);
        return result;
    }

    public ControlledCodeResult.Glob glob(ControlledCodeCommands.Glob query, String actor) {
        if (query == null) throw new IllegalArgumentException("CONTROLLED_CODE_GLOB_REQUIRED");
        String pattern = policy.required(query.pattern(), "code.glob 必须提供 pattern");
        int limit = policy.globLimit(query.limit());
        String workspaceId = policy.value(query.workspaceId());
        String projectId;
        RepairWorkspace workspace = null;
        SourceRepository repository;
        if (!workspaceId.isBlank()) {
            workspace = workspaces.get(workspaceId);
            projectId = workspace.projectId();
            repository = repository(workspace);
        } else {
            projectId = policy.required(query.projectId(), "code.glob 必须提供 projectId");
            String repositoryId = policy.required(query.repositoryId(), "code.glob 必须提供 repositoryId");
            repository = sources.find(projectId, repositoryId)
                    .orElseThrow(() -> new IllegalArgumentException("代码仓库不存在：" + repositoryId));
        }
        List<ControlledCodeResult.FileEntry> entries;
        if (isRemote(repository)) {
            entries = remote.glob(repository, workspace, repository.defaultCommitSha(), pattern, limit).stream()
                    .map(item -> new ControlledCodeResult.FileEntry(
                            item.path(), item.sizeBytes(), item.modifiedAt()))
                    .toList();
        } else {
            Path root = workspace == null ? Path.of(repository.localPath()) : workspaces.worktreePath(workspaceId);
            entries = files.glob(root, pattern, limit).stream()
                    .map(item -> new ControlledCodeResult.FileEntry(
                            item.path(), item.sizeBytes(), item.modifiedAt()))
                    .toList();
        }
        ControlledCodeResult.Glob result = new ControlledCodeResult.Glob(entries);
        audit(projectId, actor, ControlledCodeAction.GLOB, pattern, "LOW", "SUCCESS", result);
        return result;
    }

    public ControlledCodeResult.Mutation edit(ControlledCodeCommands.Edit command, String actor) {
        if (command == null) throw new IllegalArgumentException("CONTROLLED_CODE_EDIT_REQUIRED");
        String workspaceId = policy.required(command.workspaceId(), "code.edit 只能在 repair workspace 内执行");
        String path = policy.writablePath(command.path());
        String oldString = policy.required(command.oldString(), "code.edit 必须提供 oldString");
        String newString = command.newString() == null ? "" : command.newString();
        RepairWorkspace workspace = workspaces.get(workspaceId);
        SourceRepository repository = repository(workspace);
        if (isRemote(repository)) {
            String expectedHash = policy.required(
                    readObservations.get(readKey(workspaceId, path)),
                    "写入前必须先通过 code.read 读取该文件");
            ControlledCodeRemotePort.RemoteRead current = remote.read(
                    repository, workspace, workspace.baseCommit(), path);
            if (!expectedHash.equalsIgnoreCase(policy.value(current.sha256()))) {
                throw new SecurityException("文件读取后已变化，当前写入已取消");
            }
            int remoteOccurrences = policy.occurrences(current.content(), oldString);
            if (remoteOccurrences == 0 || (remoteOccurrences > 1 && !command.replaceAll())) {
                throw new IllegalArgumentException("oldString 匹配次数不合法：" + remoteOccurrences);
            }
            String writerId = policy.writerIdentity(command.runId(), actor);
            workspaces.claimWriter(workspaceId, writerId);
            ControlledCodeRemotePort.RemoteMutation mutation = remote.edit(
                    repository, workspace, path, expectedHash, oldString, newString, command.replaceAll());
            workspaces.markDirty(workspaceId, writerId);
            readObservations.put(readKey(workspaceId, path), mutation.afterSha256());
            ControlledCodeResult.Mutation result = new ControlledCodeResult.Mutation(
                    workspaceId, path, mutation.beforeSha256(), mutation.afterSha256(),
                    policy.sha256(oldString), policy.sha256(newString),
                    mutation.replacements(), mutation.created());
            audit(workspace.projectId(), actor, ControlledCodeAction.EDIT,
                    workspaceId + ":" + path, "MEDIUM", "SUCCESS", result);
            return result;
        }
        Path root = workspaces.worktreePath(workspaceId);
        String before = files.read(root, path, true);
        policy.requireReadBeforeWrite(readObservations.get(readKey(workspaceId, path)), before);
        String writerId = policy.writerIdentity(command.runId(), actor);
        workspaces.claimWriter(workspaceId, writerId);
        before = files.read(root, path, true);
        policy.requireReadBeforeWrite(readObservations.get(readKey(workspaceId, path)), before);
        int occurrences = policy.occurrences(before, oldString);
        if (occurrences == 0 || (occurrences > 1 && !command.replaceAll())) {
            throw new IllegalArgumentException("oldString 匹配次数不合法：" + occurrences);
        }
        String after = command.replaceAll()
                ? before.replace(oldString, newString)
                : before.replaceFirst(Pattern.quote(oldString), Matcher.quoteReplacement(newString));
        files.write(root, path, after);
        workspaces.markDirty(workspaceId, writerId);
        readObservations.put(readKey(workspaceId, path), policy.sha256(after));
        ControlledCodeResult.Mutation result = new ControlledCodeResult.Mutation(
                workspaceId, path, policy.sha256(before), policy.sha256(after),
                policy.sha256(oldString), policy.sha256(newString),
                command.replaceAll() ? occurrences : 1, false);
        audit(projectId(workspaceId), actor, ControlledCodeAction.EDIT,
                workspaceId + ":" + path, "MEDIUM", "SUCCESS", result);
        return result;
    }

    public ControlledCodeResult.Mutation write(ControlledCodeCommands.Write command, String actor) {
        if (command == null) throw new IllegalArgumentException("CONTROLLED_CODE_WRITE_REQUIRED");
        String workspaceId = policy.required(command.workspaceId(), "code.write 只能在 repair workspace 内执行");
        String path = policy.writablePath(command.path());
        String content = policy.content(command.content());
        RepairWorkspace workspace = workspaces.get(workspaceId);
        SourceRepository repository = repository(workspace);
        if (isRemote(repository)) {
            return storeRemote(command, actor, workspace, repository, path, content);
        }
        Path root = workspaces.worktreePath(workspaceId);
        boolean existed = files.exists(root, path);
        String before = existed ? files.read(root, path, true) : "";
        if (existed) policy.requireReadBeforeWrite(readObservations.get(readKey(workspaceId, path)), before);
        String writerId = policy.writerIdentity(command.runId(), actor);
        workspaces.claimWriter(workspaceId, writerId);
        existed = files.exists(root, path);
        before = existed ? files.read(root, path, true) : "";
        if (existed) policy.requireReadBeforeWrite(readObservations.get(readKey(workspaceId, path)), before);
        files.write(root, path, content);
        workspaces.markDirty(workspaceId, writerId);
        readObservations.put(readKey(workspaceId, path), policy.sha256(content));
        ControlledCodeResult.Mutation result = new ControlledCodeResult.Mutation(
                workspaceId, path, policy.sha256(before), policy.sha256(content),
                "", "", 0, !existed);
        audit(projectId(workspaceId), actor, ControlledCodeAction.WRITE,
                workspaceId + ":" + path, "MEDIUM", "SUCCESS", result);
        return result;
    }

    public ControlledCodeResult.Bash bash(ControlledCodeCommands.Bash command, String actor) {
        if (command == null) throw new IllegalArgumentException("CONTROLLED_CODE_BASH_REQUIRED");
        ControlledCodeEffect effect = command.expectedEffect() == null
                ? ControlledCodeEffect.READ_ONLY : command.expectedEffect();
        String workspaceId = policy.value(command.workspaceId());
        String action = policy.value(command.action()).isBlank() ? "run" : command.action().trim().toLowerCase();
        if (!"run".equals(action)) {
            return manageRemoteBash(command, actor, workspaceId, effect, action);
        }
        List<String> tokens = policy.command(command.command(), effect, workspaceId);
        if (!workspaceId.isBlank()) {
            RepairWorkspace workspace = workspaces.get(workspaceId);
            SourceRepository repository = repository(workspace);
            if (isRemote(repository)) return executeRemoteBash(command, actor, workspace, repository, effect, tokens);
        }
        if (command.background()) {
            throw new IllegalStateException("LOCAL_CODE_BASH_BACKGROUND_NOT_SUPPORTED");
        }
        String writerId = policy.writerIdentity(command.runId(), actor);
        boolean writerBound = effect.writerBound() && !workspaceId.isBlank();
        boolean testCommand = effect == ControlledCodeEffect.TEST_OR_BUILD;
        if (writerBound) {
            workspaces.claimWriter(workspaceId, writerId);
            if (testCommand) workspaces.markTesting(workspaceId, writerId);
        }
        Path root = workspaceId.isBlank()
                ? repositoryRoot(command.projectId(), command.workspaceId(), command.repositoryId())
                : workspaces.worktreePath(workspaceId);
        Path cwd = files.directory(root, policy.value(command.cwd()));
        try {
            ControlledCodeFilePort.ProcessOutput process = files.execute(
                    cwd, tokens, policy.timeoutMs(command.timeoutMs()), ControlledCodePolicy.MAX_OUTPUT_BYTES);
            String masked = policy.maskSecrets(process.output());
            String preview = masked.length() > ControlledCodePolicy.MAX_OUTPUT_BYTES
                    ? masked.substring(0, ControlledCodePolicy.MAX_OUTPUT_BYTES) : masked;
            String outputHash = policy.sha256(masked);
            String resultId = "";
            Boolean storedTruncated = null;
            String fullOutputRef = "";
            if (testCommand) workspaces.recordTestOutcome(workspaceId, writerId, process.exitCode() == 0);
            if (toolResults.available()) {
                ControlledCodeToolResultPort.StoredToolResult stored = toolResults.record(
                        new ControlledCodeToolResultPort.ToolResultRequest(
                                projectId(command.projectId(), workspaceId),
                                policy.value(command.sessionId()), policy.value(command.runId()),
                                policy.value(actor), "code.repair", "code_bash", "CONTROLLED_BASH_EXECUTED",
                                command.command(), masked, 32 * 1024, 400, actor));
                resultId = policy.value(stored.resultId());
                storedTruncated = stored.truncated();
                fullOutputRef = policy.value(stored.fullOutputRef());
                outputHash = policy.value(stored.outputHash()).isBlank()
                        ? outputHash : stored.outputHash();
            }
            ControlledCodeResult.Bash result = new ControlledCodeResult.Bash(
                    policy.sha256(String.join("\u0000", tokens)), process.exitCode(),
                    process.exitCode() == 0 ? "SUCCEEDED" : "FAILED", preview,
                    process.truncated() || masked.length() > ControlledCodePolicy.MAX_OUTPUT_BYTES,
                    process.durationMs(), cwd.toString(), effect, outputHash,
                    resultId, storedTruncated, fullOutputRef);
            recordProof(command, result, actor, workspaceId);
            audit(projectId(command.projectId(), workspaceId), actor, ControlledCodeAction.BASH,
                    result.commandHash(), effect == ControlledCodeEffect.READ_ONLY ? "LOW" : "MEDIUM",
                    process.exitCode() == 0 ? "SUCCESS" : "FAILED", result);
            return result;
        } catch (Exception e) {
            if (testCommand && writerBound) {
                try {
                    workspaces.recordTestOutcome(workspaceId, writerId, false);
                } catch (Exception ignored) {
                    // Preserve original controlled command failure.
                }
            }
            throw new IllegalStateException("受控 Bash 执行失败：" + e.getMessage(), e);
        }
    }

    public ControlledCodeResult.Worktree enterWorktree(
            ControlledCodeCommands.EnterWorktree command,
            String actor) {
        if (command == null) throw new IllegalArgumentException("CONTROLLED_CODE_WORKTREE_REQUIRED");
        RepairWorkspace workspace = workspaces.enterWorktree(
                policy.required(command.projectId(), "code.enter_worktree 必须提供 projectId"),
                policy.required(command.serviceId(), "code.enter_worktree 必须提供 serviceId"),
                policy.value(command.repositoryId()),
                policy.value(command.environment()).isBlank() ? "dev" : command.environment(),
                policy.required(command.baseCommit(), "code.enter_worktree 必须提供 baseCommit"),
                actor);
        RepairWriterLease lease = workspaces.claimWriter(
                workspace.workspaceId(), policy.writerIdentity(command.runId(), actor));
        ControlledCodeResult.Worktree result = new ControlledCodeResult.Worktree(
                workspace.workspaceId(), workspace.projectId(), workspace.serviceId(), workspace.repositoryId(),
                workspace.environment(), workspace.baseCommit(), lease.fencingToken(), workspace.status().name());
        audit(workspace.projectId(), actor, ControlledCodeAction.ENTER_WORKTREE,
                workspace.workspaceId(), "MEDIUM", "SUCCESS", result);
        return result;
    }

    public ControlledCodeResult.Exit exitWorktree(
            ControlledCodeCommands.ExitWorktree command,
            String actor) {
        if (command == null) throw new IllegalArgumentException("CONTROLLED_CODE_EXIT_REQUIRED");
        String workspaceId = policy.required(command.workspaceId(), "code.exit_worktree 必须提供 workspaceId");
        workspaces.releaseWriter(workspaceId, policy.writerIdentity(command.runId(), actor));
        ControlledCodeResult.Exit result = new ControlledCodeResult.Exit(workspaceId, "EXITED", false);
        audit(projectId(workspaceId), actor, ControlledCodeAction.EXIT_WORKTREE,
                workspaceId, "LOW", "SUCCESS", result);
        return result;
    }

    public ControlledCodeResult.Lsp lsp(ControlledCodeCommands.Lsp command, String actor) {
        if (command == null) throw new IllegalArgumentException("CONTROLLED_CODE_LSP_REQUIRED");
        String workspaceId = policy.value(command.workspaceId());
        RepairWorkspace workspace = workspaceId.isBlank() ? null : workspaces.get(workspaceId);
        String projectId = workspace == null
                ? policy.required(command.projectId(), "code.lsp 必须提供 projectId")
                : workspace.projectId();
        String repositoryId = workspace == null
                ? policy.required(command.repositoryId(), "code.lsp 必须提供 repositoryId")
                : workspace.repositoryId();
        SourceRepository repository = sources.find(projectId, repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("代码仓库不存在：" + repositoryId));
        ControlledCodeResult.Lsp result;
        if (isRemote(repository)) {
            ControlledCodeRemotePort.RemoteLsp remoteResult = remote.lsp(
                    repository, workspace, policy.value(command.revision()),
                    policy.value(command.action()).isBlank() ? "symbols" : command.action(),
                    policy.value(command.path()), command.line(), command.character(), policy.value(command.query()));
            result = new ControlledCodeResult.Lsp(
                    remoteResult.status(), remoteResult.readOnly(), remoteResult.action(),
                    remoteResult.results() == null ? "" : String.valueOf(remoteResult.results()),
                    remoteResult.reasonCode());
        } else {
            result = new ControlledCodeResult.Lsp("LSP_NOT_CONFIGURED", true, command.action(), "", "LOCAL_LSP_NOT_CONFIGURED");
        }
        audit(projectId, actor, ControlledCodeAction.LSP,
                repositoryId + ":" + policy.value(command.action()), "LOW",
                "SUCCEEDED".equalsIgnoreCase(result.status()) ? "SUCCESS" : "SKIPPED", result);
        return result;
    }

    public ControlledCodeResult.Lsp lsp(String projectId, String actor) {
        ControlledCodeResult.Lsp result = new ControlledCodeResult.Lsp("LSP_NOT_CONFIGURED", true);
        audit(policy.value(projectId), actor, ControlledCodeAction.LSP,
                "lsp", "LOW", "SKIPPED", result);
        return result;
    }

    public RepairDiffSnapshot computeDiff(String workspaceId, String actor) {
        String id = policy.required(workspaceId, "computeRepairDiff 必须提供 workspaceId");
        RepairDiffSnapshot result = workspaces.computeDiff(id);
        audit(projectId(id), actor, ControlledCodeAction.DIFF, id, "LOW", "SUCCESS", result);
        return result;
    }

    public RepairCommitResult commit(ControlledCodeCommands.Commit command, String actor) {
        if (command == null) throw new IllegalArgumentException("CONTROLLED_CODE_COMMIT_REQUIRED");
        String workspaceId = policy.required(command.workspaceId(), "commitRepair 必须提供 workspaceId");
        RepairCommitResult result = workspaces.commitRepair(
                workspaceId, policy.value(command.message()), actor,
                policy.writerIdentity(command.runId(), actor));
        audit(projectId(workspaceId), actor, ControlledCodeAction.COMMIT,
                workspaceId, "MEDIUM", "SUCCESS", result);
        return result;
    }

    private ControlledCodeResult.Read page(
            String projectId,
            String repositoryId,
            String workspaceId,
            String commitSha,
            String path,
            String content,
            String hash,
            int startLine,
            int limit) {
        String[] lines = (content == null ? "" : content).split("\\R", -1);
        List<ControlledCodeResult.Line> page = new ArrayList<>();
        for (int index = Math.max(0, startLine - 1); index < lines.length && page.size() < limit; index++) {
            page.add(new ControlledCodeResult.Line(index + 1, policy.maskSecrets(lines[index])));
        }
        return new ControlledCodeResult.Read(
                projectId, repositoryId, workspaceId, commitSha, path, hash,
                startLine, limit, lines.length, page);
    }

    private Path repositoryRoot(String projectId, String workspaceId, String repositoryId) {
        if (!policy.value(workspaceId).isBlank()) return workspaces.worktreePath(workspaceId);
        String project = policy.required(projectId, "只读 Bash 必须提供 projectId");
        String repository = policy.required(repositoryId, "只读 Bash 必须提供 repositoryId");
        SourceRepository source = sources.find(project, repository)
                .orElseThrow(() -> new IllegalArgumentException("代码仓库不存在：" + repository));
        return Path.of(source.localPath());
    }

    private void recordProof(
            ControlledCodeCommands.Bash command,
            ControlledCodeResult.Bash result,
            String actor,
            String workspaceId) {
        if (!policy.proofRequired(
                result.expectedEffect(), result.exitCode(), command.packageId(),
                command.packageVersion(), command.packageHash())) return;
        if (!proofs.available()) {
            throw new IllegalStateException(
                    "TRUSTED_PROOF_STORE_UNAVAILABLE：包绑定的测试成功后无法写入可信 proof");
        }
        proofs.record(new ControlledCodeProofPort.ControlledCodeProof(
                projectId(command.projectId(), workspaceId), workspaceId,
                command.packageId(), command.packageVersion(), command.packageHash(),
                policy.value(command.riskLevel()).isBlank() ? "MEDIUM" : command.riskLevel(),
                result.resultId().isBlank() ? result.commandHash() : result.resultId(),
                result.commandHash(), result.expectedEffect(), result.resultId(),
                result.fullOutputRef(), result.outputHash(), result.exitCode()), actor);
    }

    private void audit(
            String projectId,
            String actor,
            ControlledCodeAction action,
            String targetId,
            String risk,
            String status,
            Object payload) {
        audit.record(new ControlledCodeAuditPort.ControlledCodeAuditEvent(
                policy.value(projectId), policy.value(actor), action,
                policy.value(targetId), risk, status, payload));
    }

    private ControlledCodeResult.Bash manageRemoteBash(
            ControlledCodeCommands.Bash command,
            String actor,
            String workspaceId,
            ControlledCodeEffect effect,
            String action) {
        String id = policy.required(workspaceId, "后台执行管理必须提供 workspaceId");
        if (!List.of("status", "logs", "stop").contains(action)) {
            throw new IllegalArgumentException("不支持的 code.bash action：" + action);
        }
        String executionId = policy.required(command.executionId(), "后台执行管理必须提供 executionId");
        RepairWorkspace workspace = workspaces.get(id);
        SourceRepository repository = repository(workspace);
        if (!isRemote(repository)) {
            throw new IllegalStateException("后台执行生命周期仅由 Code Workspace MCP 管理");
        }
        ControlledCodeRemotePort.RemoteBash remoteResult = remote.bash(
                repository, workspace, "", effect, "", policy.timeoutMs(command.timeoutMs()),
                false, action, executionId, command.limitBytes());
        String masked = policy.maskSecrets(remoteResult.output());
        String commandHash = policy.value(remoteResult.commandHash()).isBlank()
                ? policy.sha256(action + "\u0000" + executionId) : remoteResult.commandHash();
        String outputHash = policy.value(remoteResult.outputHash()).isBlank()
                ? policy.sha256(masked) : remoteResult.outputHash();
        ControlledCodeResult.Bash result = new ControlledCodeResult.Bash(
                commandHash, remoteResult.exitCode(), remoteResult.status(), masked,
                remoteResult.truncated(), remoteResult.durationMs(), remoteResult.cwd(), effect,
                outputHash, "", null, remoteResult.logRef(),
                remoteResult.executionId(), remoteResult.pid(), remoteResult.logRef());
        audit(workspace.projectId(), actor, ControlledCodeAction.BASH,
                commandHash, "LOW", "SUCCESS", result);
        return result;
    }

    private ControlledCodeResult.Bash executeRemoteBash(
            ControlledCodeCommands.Bash command,
            String actor,
            RepairWorkspace workspace,
            SourceRepository repository,
            ControlledCodeEffect effect,
            List<String> tokens) {
        String workspaceId = workspace.workspaceId();
        String writerId = policy.writerIdentity(command.runId(), actor);
        boolean writerBound = effect.writerBound();
        boolean testCommand = effect == ControlledCodeEffect.TEST_OR_BUILD && !command.background();
        if (writerBound) {
            workspaces.claimWriter(workspaceId, writerId);
            if (testCommand) workspaces.markTesting(workspaceId, writerId);
        }
        try {
            ControlledCodeRemotePort.RemoteBash remoteResult = remote.bash(
                    repository, workspace, command.command(), effect, command.cwd(),
                    policy.timeoutMs(command.timeoutMs()), command.background(), "run", "", command.limitBytes());
            String masked = policy.maskSecrets(remoteResult.output());
            String preview = masked.length() > ControlledCodePolicy.MAX_OUTPUT_BYTES
                    ? masked.substring(0, ControlledCodePolicy.MAX_OUTPUT_BYTES) : masked;
            String commandHash = policy.value(remoteResult.commandHash()).isBlank()
                    ? policy.sha256(String.join("\u0000", tokens)) : remoteResult.commandHash();
            String outputHash = policy.value(remoteResult.outputHash()).isBlank()
                    ? policy.sha256(masked) : remoteResult.outputHash();
            if (testCommand) workspaces.recordTestOutcome(workspaceId, writerId, remoteResult.exitCode() == 0);
            String resultId = "";
            Boolean storedTruncated = null;
            String fullOutputRef = "";
            if (toolResults.available()) {
                ControlledCodeToolResultPort.StoredToolResult stored = toolResults.record(
                        new ControlledCodeToolResultPort.ToolResultRequest(
                                workspace.projectId(), policy.value(command.sessionId()), policy.value(command.runId()),
                                policy.value(actor), "code.repair", "code_bash", "CONTROLLED_BASH_EXECUTED",
                                command.command(), masked, 32 * 1024, 400, actor));
                resultId = policy.value(stored.resultId());
                storedTruncated = stored.truncated();
                fullOutputRef = policy.value(stored.fullOutputRef());
                outputHash = policy.value(stored.outputHash()).isBlank() ? outputHash : stored.outputHash();
            }
            ControlledCodeResult.Bash result = new ControlledCodeResult.Bash(
                    commandHash, remoteResult.exitCode(), remoteResult.status(), preview,
                    remoteResult.truncated() || masked.length() > ControlledCodePolicy.MAX_OUTPUT_BYTES,
                    remoteResult.durationMs(), remoteResult.cwd(), effect, outputHash,
                    resultId, storedTruncated, fullOutputRef,
                    remoteResult.executionId(), remoteResult.pid(), remoteResult.logRef());
            if (!command.background()) recordProof(command, result, actor, workspaceId);
            audit(workspace.projectId(), actor, ControlledCodeAction.BASH,
                    result.commandHash(), effect == ControlledCodeEffect.READ_ONLY ? "LOW" : "MEDIUM",
                    remoteResult.exitCode() == 0 ? "SUCCESS" : "FAILED", result);
            return result;
        } catch (Exception error) {
            if (testCommand && writerBound) {
                try { workspaces.recordTestOutcome(workspaceId, writerId, false); } catch (Exception ignored) { }
            }
            throw new IllegalStateException("受控 Bash 执行失败：" + error.getMessage(), error);
        }
    }

    private ControlledCodeResult.Mutation storeRemote(
            ControlledCodeCommands.Write command,
            String actor,
            RepairWorkspace workspace,
            SourceRepository repository,
            String path,
            String content) {
        String workspaceId = workspace.workspaceId();
        String expectedHash = policy.value(readObservations.get(readKey(workspaceId, path)));
        String writerId = policy.writerIdentity(command.runId(), actor);
        workspaces.claimWriter(workspaceId, writerId);
        ControlledCodeRemotePort.RemoteMutation mutation = remote.write(
                repository, workspace, path, expectedHash, content);
        workspaces.markDirty(workspaceId, writerId);
        readObservations.put(readKey(workspaceId, path), mutation.afterSha256());
        ControlledCodeResult.Mutation result = new ControlledCodeResult.Mutation(
                workspaceId, path, mutation.beforeSha256(), mutation.afterSha256(),
                "", "", mutation.replacements(), mutation.created());
        audit(workspace.projectId(), actor, ControlledCodeAction.WRITE,
                workspaceId + ":" + path, "MEDIUM", "SUCCESS", result);
        return result;
    }

    private SourceRepository repository(RepairWorkspace workspace) {
        if (remote == null) return null;
        return sources.find(workspace.projectId(), workspace.repositoryId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "代码仓库不存在：" + workspace.repositoryId()));
    }

    private boolean isRemote(SourceRepository repository) {
        return remote != null && repository != null && remote.supports(repository);
    }

    private String projectId(String workspaceId) {
        return policy.value(workspaceId).isBlank()
                ? "" : workspaces.find(workspaceId).map(RepairWorkspace::projectId).orElse("");
    }

    private String projectId(String projectId, String workspaceId) {
        String project = policy.value(projectId);
        return project.isBlank() ? projectId(workspaceId) : project;
    }

    private String readKey(String workspaceId, String path) {
        return workspaceId + ":" + policy.safeRelativePath(path);
    }
}
