package cn.lgs.orbisops.domain.repair.service;

import cn.lgs.orbisops.domain.repair.model.RepairDiffSnapshot;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspace;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceAggregate;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceCandidate;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceStatus;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RepairWorkspacePolicy {

    public static final int MAX_PATCH_BYTES = 256 * 1024;
    public static final int MAX_CHANGED_FILES = 40;
    private static final Pattern PATCH_PATH = Pattern.compile("^\\+\\+\\+ b/(.+)$", Pattern.MULTILINE);
    private static final Pattern PATCH_OLD_PATH = Pattern.compile("^--- a/(.+)$", Pattern.MULTILINE);

    public RepairWorkspaceCandidate normalize(RepairWorkspaceCandidate candidate) {
        if (candidate == null) throw new IllegalArgumentException("修复工作区请求不能为空");
        String patch = value(candidate.unifiedDiff());
        if (patch.isBlank()) throw new IllegalArgumentException("必须提供 unifiedDiff");
        validatePatch(patch, ".");
        return new RepairWorkspaceCandidate(
                required(candidate.projectId(), "projectId"),
                required(candidate.serviceId(), "serviceId"),
                value(candidate.environment()).isBlank() ? "dev" : value(candidate.environment()).toLowerCase(),
                value(candidate.summary()).isBlank() ? "Agent 生成的代码修复候选" : value(candidate.summary()),
                patch,
                optionalCommit(candidate.baseCommit()));
    }

    public void validatePatch(String patch, String modulePath) {
        String normalized = value(patch);
        byte[] bytes = normalized.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_PATCH_BYTES) {
            throw new IllegalArgumentException("补丁超过 " + MAX_PATCH_BYTES + " 字节限制");
        }
        if (normalized.contains("GIT binary patch") || normalized.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("不允许二进制补丁");
        }
        Set<String> paths = new LinkedHashSet<>();
        collect(PATCH_PATH.matcher(normalized), paths);
        collect(PATCH_OLD_PATH.matcher(normalized), paths);
        paths.forEach(path -> validateChangedPath(path, modulePath));
        if (paths.isEmpty() || paths.size() > MAX_CHANGED_FILES) {
            throw new IllegalArgumentException("补丁文件数量必须在 1-" + MAX_CHANGED_FILES + " 之间");
        }
    }

    public void validateChangedFiles(List<String> files, String modulePath) {
        List<String> safe = files == null ? List.of() : files.stream()
                .map(this::value).filter(item -> !item.isBlank()).distinct().toList();
        if (safe.isEmpty() || safe.size() > MAX_CHANGED_FILES) {
            throw new IllegalArgumentException("实际变更文件数量不合法");
        }
        safe.forEach(path -> validateChangedPath(path, modulePath));
    }

    public void requireCommitAllowed(RepairWorkspace workspace, RepairDiffSnapshot diff) {
        requireWorkspace(workspace);
        if (diff == null || diff.changedFiles().isEmpty()) {
            throw new IllegalStateException("没有可提交的 repair diff");
        }
        if (workspace.status() != RepairWorkspaceStatus.ACTIVE
                && workspace.status() != RepairWorkspaceStatus.DIRTY
                && workspace.status() != RepairWorkspaceStatus.TEST_PASSED
                && workspace.status() != RepairWorkspaceStatus.FAILED) {
            throw new IllegalStateException("REPAIR_WORKSPACE_COMMIT_FORBIDDEN:" + workspace.status().name());
        }
    }

    public void requireVerification(
            RepairWorkspace workspace,
            RepairDiffSnapshot diff,
            String expectedDiffHash,
            List<String> expectedChangedFiles,
            String testProofHash) {
        requireWorkspace(workspace);
        if (workspace.verifiedCommit().isBlank()) {
            throw new IllegalStateException("repair workspace 缺少 repairCommit，不能 VERIFIED");
        }
        if (value(testProofHash).isBlank()) {
            throw new IllegalStateException("repair workspace 缺少可信 testProofHash，不能 VERIFIED");
        }
        if (!value(expectedDiffHash).isBlank() && !value(expectedDiffHash).equals(diff.diffHash())) {
            throw new IllegalStateException("repair workspace diffHash 与 ChangePackage 不一致");
        }
        if (expectedChangedFiles != null && !expectedChangedFiles.isEmpty()
                && !new LinkedHashSet<>(expectedChangedFiles).equals(new LinkedHashSet<>(diff.changedFiles()))) {
            throw new IllegalStateException("repair workspace changedFiles 与 ChangePackage 不一致");
        }
    }

    public void requireArtifact(
            RepairWorkspace workspace,
            String projectId,
            String serviceId,
            String artifactPath,
            String artifactSha256) {
        requireWorkspace(workspace);
        if (!workspace.projectId().equals(value(projectId)) || !workspace.serviceId().equals(value(serviceId))) {
            throw new IllegalArgumentException("修复工作区与项目服务不匹配");
        }
        if (workspace.status() != RepairWorkspaceStatus.VERIFIED) {
            throw new IllegalArgumentException("修复工作区测试未通过");
        }
        if (!workspace.artifactPath().equals(value(artifactPath))
                || !workspace.artifactSha256().equalsIgnoreCase(value(artifactSha256))) {
            throw new IllegalArgumentException("制品路径或 SHA-256 与修复工作区快照不一致");
        }
    }

    public void requireCleanupAllowed(RepairWorkspace workspace) {
        requireWorkspace(workspace);
        RepairWorkspaceAggregate.rehydrate(
                workspace.workspaceId(), workspace.projectId(), workspace.serviceId(), workspace.status())
                .requireCleanupAllowed();
    }

    public String actor(String actor) {
        String normalized = value(actor);
        if (normalized.isBlank()) throw new IllegalArgumentException("操作者不能为空");
        return normalized;
    }

    public String workspaceId(String workspaceId) {
        return required(workspaceId, "workspaceId");
    }

    public String commit(String commit) {
        String normalized = value(commit).toLowerCase();
        if (!normalized.matches("[a-f0-9]{40}")) {
            throw new IllegalArgumentException("baseCommit 必须是完整 Git Commit SHA");
        }
        return normalized;
    }

    private void validateChangedPath(String value, String modulePath) {
        Path path = Path.of(value).normalize();
        if (path.isAbsolute() || path.startsWith("..")) {
            throw new IllegalArgumentException("补丁路径越界：" + value);
        }
        String module = this.value(modulePath);
        if (module.isBlank()) module = ".";
        if (!".".equals(module)) {
            Path moduleRoot = Path.of(module).normalize();
            if (!path.startsWith(moduleRoot)) {
                throw new IllegalArgumentException("补丁只能修改服务目录 " + module + "：" + value);
            }
        }
        String normalized = path.toString().replace('\\', '/');
        if (normalized.contains("/target/") || normalized.contains("/node_modules/")
                || normalized.startsWith(".git/") || normalized.endsWith(".class")) {
            throw new IllegalArgumentException("补丁包含禁止路径：" + value);
        }
    }

    private void collect(Matcher matcher, Set<String> paths) {
        while (matcher.find()) paths.add(matcher.group(1));
    }

    private void requireWorkspace(RepairWorkspace workspace) {
        if (workspace == null) throw new IllegalArgumentException("REPAIR_WORKSPACE_REQUIRED");
    }

    private String optionalCommit(String value) {
        String normalized = this.value(value);
        return normalized.isBlank() ? "" : commit(normalized);
    }

    private String required(String value, String field) {
        String normalized = this.value(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(field + " 不能为空");
        return normalized;
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
