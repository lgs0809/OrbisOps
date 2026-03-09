package cn.lgs.orbisops.trigger.application.repair;

import cn.lgs.orbisops.application.repair.RepairExecutionCommand;
import cn.lgs.orbisops.application.repair.RepairSourceCatalogPort;
import cn.lgs.orbisops.application.repair.RepairWorkspaceExecutionPort;
import cn.lgs.orbisops.application.repair.RepairWorktreeCommand;
import cn.lgs.orbisops.domain.repair.model.RepairArtifactValidation;
import cn.lgs.orbisops.domain.repair.model.RepairCleanupResult;
import cn.lgs.orbisops.domain.repair.model.RepairCommitResult;
import cn.lgs.orbisops.domain.repair.model.RepairDiffSnapshot;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspace;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceStatus;
import cn.lgs.orbisops.domain.source.model.BuildProfile;
import cn.lgs.orbisops.domain.source.model.SourceRepository;
import cn.lgs.orbisops.trigger.ops.code.OpsCodeWorkspaceMcpClient;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** RepairWorkspace execution backed by the Code Workspace MCP. */
@Component
public final class McpRepairWorkspaceExecutionAdapter implements RepairWorkspaceExecutionPort {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final OpsCodeWorkspaceMcpClient client;
    private final RepairSourceCatalogPort sources;

    public McpRepairWorkspaceExecutionAdapter(
            OpsCodeWorkspaceMcpClient client,
            RepairSourceCatalogPort sources) {
        if (client == null) throw new IllegalArgumentException("CODE_MCP_CLIENT_REQUIRED");
        if (sources == null) throw new IllegalArgumentException("REPAIR_SOURCE_CATALOG_REQUIRED");
        this.client = client;
        this.sources = sources;
    }

    @Override
    public RepairWorkspace createAndVerify(RepairExecutionCommand command) {
        if (command == null || command.candidate() == null
                || command.service() == null || command.repository() == null) {
            throw new IllegalArgumentException("REPAIR_EXECUTION_COMMAND_REQUIRED");
        }
        SourceRepository repository = command.repository();
        RepairWorkspace active = enterWorktree(new RepairWorktreeCommand(
                command.workspaceId(), command.candidate().projectId(), command.candidate().serviceId(),
                command.candidate().environment(), command.baseCommit(), command.actor(),
                command.service(), repository));
        RepairWorkspaceStatus status = RepairWorkspaceStatus.PREPARING;
        int exitCode = -1;
        String testLog = "";
        String verifiedCommit = "";
        String artifactRef = "";
        String artifactSha = "";
        long artifactSize = 0L;
        String buildCommand = "";
        List<String> changedFiles = List.of();
        try {
            JSONObject patch = client.invoke(active.projectId(), repository.codeMcpId(), "code_apply_patch",
                    Map.of(
                            "workspaceId", active.workspaceId(),
                            "mode", "patch",
                            "unifiedDiff", command.candidate().unifiedDiff()));
            JSONArray patchFiles = patch.getJSONArray("changedFiles");
            changedFiles = patchFiles == null ? List.of() : patchFiles.toJavaList(String.class);
            RepairDiffSnapshot beforeBuild = computeDiff(active);
            List<String> commands = buildCommands(command.service().buildProfile());
            buildCommand = String.join(" -> ", commands);
            StringBuilder output = new StringBuilder();
            exitCode = 0;
            for (String build : commands) {
                JSONObject execution = client.invoke(active.projectId(), repository.codeMcpId(), "code_bash",
                        Map.of(
                                "workspaceId", active.workspaceId(),
                                "action", "run",
                                "command", build,
                                "expectedEffect", "TEST_OR_BUILD",
                                "cwd", moduleCwd(command.service().modulePath()),
                                "timeoutMs", 300_000,
                                "background", false));
                int currentExit = execution.getIntValue("exitCode");
                if (output.length() > 0) output.append('\n');
                output.append(value(execution.getString("output")));
                if (currentExit != 0) {
                    exitCode = currentExit;
                    break;
                }
            }
            testLog = truncate(output.toString(), 1024 * 1024);
            status = exitCode == 0 ? RepairWorkspaceStatus.VERIFIED : RepairWorkspaceStatus.TEST_FAILED;
            RepairDiffSnapshot afterBuild = computeDiff(active);
            if (!beforeBuild.diffHash().equalsIgnoreCase(afterBuild.diffHash())) {
                status = RepairWorkspaceStatus.BUILD_MUTATED_SOURCE;
                testLog = truncate(testLog + "\nBuild changed source files outside the approved patch.", 1024 * 1024);
            }
            if (status == RepairWorkspaceStatus.VERIFIED && !value(command.service().artifactPath()).isBlank()) {
                ArtifactEvidence artifact = artifactEvidence(active, repository, command.service().artifactPath());
                artifactRef = artifact.reference();
                artifactSha = artifact.sha256();
                artifactSize = artifact.sizeBytes();
            }
            if (status == RepairWorkspaceStatus.VERIFIED) {
                JSONObject committed = client.invoke(active.projectId(), repository.codeMcpId(), "code_commit",
                        Map.of(
                                "workspaceId", active.workspaceId(),
                                "message", "ops repair candidate " + active.workspaceId(),
                                "actor", command.actor()));
                verifiedCommit = commit(committed.getString("repairCommit"), "CODE_MCP_REPAIR_COMMIT_INVALID");
            }
        } catch (Exception error) {
            status = RepairWorkspaceStatus.FAILED;
            testLog = truncate((testLog + "\n" + error.getMessage()).trim(), 1024 * 1024);
        }
        String now = now();
        return new RepairWorkspace(
                active.workspaceId(), active.projectId(), active.serviceId(), active.repositoryId(),
                active.environment(), active.baseCommit(), verifiedCommit, status,
                command.candidate().summary(), command.candidate().unifiedDiff(), changedFiles,
                command.service().buildProfile().name(), buildCommand, exitCode, testLog,
                artifactRef, artifactSha, artifactSize, command.actor(), now, now);
    }

    @Override
    public RepairWorkspace enterWorktree(RepairWorktreeCommand command) {
        if (command == null || command.repository() == null || command.service() == null) {
            throw new IllegalArgumentException("REPAIR_WORKTREE_COMMAND_REQUIRED");
        }
        SourceRepository repository = command.repository();
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("repositoryId", repository.logicalRoot());
        args.put("baseCommit", command.baseCommit());
        args.put("workspaceId", command.workspaceId());
        args.put("projectId", command.projectId());
        args.put("serviceId", command.serviceId());
        args.put("runId", command.actor());
        JSONObject result = client.invoke(command.projectId(), repository.codeMcpId(), "code_enter_worktree", args);
        String baseCommit = commit(result.getString("baseCommit"), "CODE_MCP_BASE_COMMIT_INVALID");
        if (!baseCommit.equalsIgnoreCase(command.baseCommit())) {
            throw new IllegalStateException("CODE_MCP_BASE_COMMIT_MISMATCH");
        }
        String now = now();
        return new RepairWorkspace(
                command.workspaceId(), command.projectId(), command.serviceId(), repository.repositoryId(),
                command.environment(), baseCommit, "", RepairWorkspaceStatus.ACTIVE,
                "Code Workspace MCP repair worktree", "", List.of(), "", "", null, "", "", "", 0L,
                command.actor(), now, now);
    }

    @Override
    public Path worktreePath(String workspaceId) {
        throw new IllegalStateException("MCP_REPAIR_WORKSPACE_HAS_NO_LOCAL_HOST_PATH");
    }

    @Override
    public RepairDiffSnapshot computeDiff(RepairWorkspace workspace) {
        SourceRepository repository = repositoryRequired(workspace);
        JSONObject result = client.invoke(workspace.projectId(), repository.codeMcpId(), "code_diff",
                Map.of("workspaceId", workspace.workspaceId()));
        return diff(workspace, result);
    }

    @Override
    public RepairCommitResult commit(RepairWorkspace workspace, String message, String actor) {
        SourceRepository repository = repositoryRequired(workspace);
        RepairDiffSnapshot before = computeDiff(workspace);
        JSONObject result = client.invoke(workspace.projectId(), repository.codeMcpId(), "code_commit",
                Map.of(
                        "workspaceId", workspace.workspaceId(),
                        "message", value(message).isBlank() ? "ops repair " + workspace.workspaceId() : message,
                        "actor", value(actor).isBlank() ? "orbisops" : actor));
        String repairCommit = commit(result.getString("repairCommit"), "CODE_MCP_REPAIR_COMMIT_INVALID");
        String diffHash = required(result.getString("diffHash"), "CODE_MCP_DIFF_HASH_MISSING").toLowerCase();
        if (!diffHash.equalsIgnoreCase(before.diffHash())) {
            throw new IllegalStateException("CODE_MCP_COMMIT_DIFF_HASH_MISMATCH");
        }
        RepairDiffSnapshot committed = new RepairDiffSnapshot(
                workspace.workspaceId(), workspace.baseCommit(), repairCommit,
                before.changedFiles(), before.diffSummary(), before.diffHash(), before.diffBytes());
        return new RepairCommitResult(committed, repairCommit, RepairWorkspaceStatus.COMMITTED, actor);
    }

    @Override
    public RepairArtifactValidation validateArtifact(
            RepairWorkspace workspace,
            String artifactPath,
            String artifactSha256) {
        SourceRepository repository = repositoryRequired(workspace);
        String prefix = "mcp://" + repository.codeMcpId() + "/workspace/"
                + workspace.workspaceId() + "/artifact/";
        String relative = value(artifactPath).startsWith(prefix)
                ? value(artifactPath).substring(prefix.length())
                : value(artifactPath);
        ArtifactEvidence evidence = artifactEvidence(workspace, repository, relative);
        if (!evidence.sha256().equalsIgnoreCase(required(artifactSha256, "REPAIR_ARTIFACT_SHA_REQUIRED"))) {
            throw new IllegalArgumentException("REPAIR_ARTIFACT_SHA_CHANGED");
        }
        return new RepairArtifactValidation(
                workspace.workspaceId(), evidence.reference(), evidence.sha256(), evidence.sizeBytes(),
                workspace.baseCommit(), workspace.verifiedCommit(), workspace.changedFiles(),
                workspace.testProfile(), workspace.testExitCode());
    }

    @Override
    public RepairCleanupResult cleanup(
            RepairWorkspace workspace,
            SourceRepository repository,
            boolean forceRemoveWorktree) {
        if (workspace == null) throw new IllegalArgumentException("REPAIR_WORKSPACE_REQUIRED");
        SourceRepository source = repository == null ? repositoryRequired(workspace) : repository;
        JSONObject result = client.invoke(workspace.projectId(), source.codeMcpId(), "code_cleanup",
                Map.of("workspaceId", workspace.workspaceId()));
        String status = required(result.getString("status"), "CODE_MCP_CLEANUP_STATUS_MISSING");
        boolean removed = result.getBooleanValue("worktreeRemoved");
        return new RepairCleanupResult(
                workspace.workspaceId(), status, removed,
                "mcp://" + source.codeMcpId() + "/workspace/" + workspace.workspaceId());
    }

    private RepairDiffSnapshot diff(RepairWorkspace workspace, JSONObject result) {
        String baseCommit = commit(result.getString("baseCommit"), "CODE_MCP_BASE_COMMIT_INVALID");
        String currentHead = commit(result.getString("currentHead"), "CODE_MCP_HEAD_COMMIT_INVALID");
        JSONArray raw = result.getJSONArray("changedFiles");
        List<String> changedFiles = raw == null ? List.of() : raw.toJavaList(String.class);
        return new RepairDiffSnapshot(
                workspace.workspaceId(), baseCommit, currentHead, changedFiles,
                value(result.getString("diffStat")),
                required(result.getString("diffHash"), "CODE_MCP_DIFF_HASH_MISSING"),
                Math.max(0, result.getIntValue("diffBytes")));
    }

    private List<String> buildCommands(BuildProfile profile) {
        if (profile == BuildProfile.MAVEN_VERIFY) return List.of("mvn -q test package");
        if (profile == BuildProfile.NPM_TEST_BUILD) return List.of("npm test", "npm run build");
        if (profile == BuildProfile.MAKE_CI) return List.of("make ci");
        throw new IllegalArgumentException("SOURCE_BUILD_PROFILE_UNKNOWN:" + profile);
    }

    private String moduleCwd(String modulePath) {
        String normalized = value(modulePath);
        return normalized.isBlank() || ".".equals(normalized) ? "" : normalized;
    }

    private ArtifactEvidence artifactEvidence(
            RepairWorkspace workspace,
            SourceRepository repository,
            String artifactPath) {
        String relative = required(artifactPath, "REPAIR_ARTIFACT_PATH_REQUIRED");
        JSONObject hash = client.invoke(workspace.projectId(), repository.codeMcpId(), "code_bash",
                Map.of(
                        "workspaceId", workspace.workspaceId(),
                        "action", "run",
                        "command", "shasum -a 256 " + relative,
                        "expectedEffect", "READ_ONLY",
                        "cwd", "",
                        "timeoutMs", 30_000,
                        "background", false));
        String output = value(hash.getString("output"));
        String sha = output.split("\\s+", 2)[0].trim().toLowerCase();
        if (!sha.matches("[a-f0-9]{64}")) throw new IllegalStateException("CODE_MCP_ARTIFACT_HASH_INVALID");
        JSONObject listing = client.invoke(workspace.projectId(), repository.codeMcpId(), "code_search",
                Map.of(
                        "workspaceId", workspace.workspaceId(),
                        "mode", "glob",
                        "pattern", relative,
                        "limit", 5));
        JSONArray files = listing.getJSONArray("files");
        if (files == null || files.isEmpty()) throw new IllegalStateException("CODE_MCP_ARTIFACT_NOT_FOUND");
        JSONObject first = files.getJSONObject(0);
        long size = Math.max(0L, first.getLongValue("sizeBytes"));
        String reference = "mcp://" + repository.codeMcpId() + "/workspace/"
                + workspace.workspaceId() + "/artifact/" + relative;
        return new ArtifactEvidence(reference, sha, size, relative);
    }

    private String truncate(String value, int maxBytes) {
        String normalized = value(value);
        if (normalized.length() <= maxBytes) return normalized;
        return normalized.substring(0, maxBytes);
    }

    private SourceRepository repositoryRequired(RepairWorkspace workspace) {
        if (workspace == null) throw new IllegalArgumentException("REPAIR_WORKSPACE_REQUIRED");
        return sources.findRepository(workspace.projectId(), workspace.repositoryId())
                .orElseThrow(() -> new IllegalStateException(
                        "MCP_REPAIR_REPOSITORY_NOT_FOUND:" + workspace.repositoryId()));
    }

    private String commit(String value, String code) {
        String normalized = required(value, code).toLowerCase();
        if (!normalized.matches("[a-f0-9]{40}")) throw new IllegalStateException(code);
        return normalized;
    }

    private String required(String value, String code) {
        String normalized = value(value);
        if (normalized.isBlank()) throw new IllegalStateException(code);
        return normalized;
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }

    private String now() {
        return TIME.format(LocalDateTime.now());
    }

    private record ArtifactEvidence(String reference, String sha256, long sizeBytes, String relativePath) {
    }
}
