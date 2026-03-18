package cn.lgs.orbisops.domain.source.service;

import cn.lgs.orbisops.domain.source.model.DeploymentRevisionCandidate;
import cn.lgs.orbisops.domain.source.model.SourceRepositoryAccessMode;
import cn.lgs.orbisops.domain.source.model.SourceRepositoryCandidate;

import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

public final class SourceRepositoryPolicy {

    private static final Pattern ID = Pattern.compile("[A-Za-z0-9._-]{1,100}");
    private static final Pattern REVISION = Pattern.compile("[A-Za-z0-9._/@{}~^+\\-]{1,200}");
    private static final Pattern COMMIT_SHA = Pattern.compile("[a-fA-F0-9]{40}");

    public SourceRepositoryCandidate repository(SourceRepositoryCandidate candidate) {
        if (candidate == null) throw new IllegalArgumentException("代码仓库配置不能为空");
        String projectId = id(candidate.projectId(), "projectId");
        String repositoryId = text(candidate.repositoryId());
        if (repositoryId.isBlank()) repositoryId = id(projectId + "-source", "repositoryId");
        else repositoryId = id(repositoryId, "repositoryId");
        SourceRepositoryAccessMode accessMode = candidate.accessMode() == null
                ? SourceRepositoryAccessMode.LOCAL : candidate.accessMode();
        String localPath = text(candidate.localPath());
        String codeMcpId = text(candidate.codeMcpId());
        String logicalRoot = text(candidate.logicalRoot());
        if (accessMode == SourceRepositoryAccessMode.LOCAL) {
            if (localPath.isBlank() || !Path.of(localPath).normalize().isAbsolute()) {
                throw new IllegalArgumentException("localPath 必须是绝对路径");
            }
            localPath = Path.of(localPath).normalize().toString();
            codeMcpId = "";
            logicalRoot = "";
        } else {
            if (codeMcpId.isBlank()) throw new IllegalArgumentException("MCP 代码仓库必须配置 codeMcpId");
            if (logicalRoot.isBlank()) logicalRoot = repositoryId;
            codeMcpId = id(codeMcpId, "codeMcpId");
            logicalRoot = id(logicalRoot, "logicalRoot");
            localPath = "";
        }
        String revision = text(candidate.defaultRevision());
        if (revision.isBlank()) revision = "HEAD";
        revision = revision(revision);
        String name = text(candidate.name());
        if (name.isBlank()) name = repositoryId;
        return new SourceRepositoryCandidate(
                projectId, repositoryId, name, localPath, revision, accessMode, codeMcpId, logicalRoot);
    }

    public DeploymentRevisionCandidate deployment(DeploymentRevisionCandidate candidate) {
        if (candidate == null) throw new IllegalArgumentException("部署版本不能为空");
        return new DeploymentRevisionCandidate(
                id(candidate.projectId(), "projectId"),
                id(candidate.repositoryId(), "repositoryId"),
                id(candidate.environment(), "environment").toLowerCase(Locale.ROOT),
                id(candidate.serviceName(), "serviceName"),
                text(candidate.revision()).isBlank() ? "" : revision(candidate.revision()),
                text(candidate.imageRef()));
    }

    public String projectId(String value) { return id(value, "projectId"); }
    public String repositoryId(String value) { return id(value, "repositoryId"); }
    public String environment(String value) { return id(value, "environment").toLowerCase(Locale.ROOT); }
    public String serviceName(String value) { return id(value, "serviceName"); }

    public String sourcePath(String value) {
        String normalized = relativePath(value, "文件路径不安全");
        if (normalized.equals(".git") || normalized.startsWith(".git/")) {
            throw new IllegalArgumentException("文件路径不安全");
        }
        return normalized;
    }

    public String searchQuery(String value) {
        String normalized = text(value);
        if (normalized.isBlank() || normalized.length() > 200
                || normalized.contains("\n") || normalized.contains("\r")) {
            throw new IllegalArgumentException("检索词长度必须在 1-200 且不能换行");
        }
        return normalized;
    }

    public String revisionOrDefault(String revision, String defaultCommitSha) {
        String normalized = text(revision);
        return normalized.isBlank() ? commit(defaultCommitSha) : revision(normalized);
    }

    public String commit(String value) {
        String normalized = text(value);
        if (!COMMIT_SHA.matcher(normalized).matches()) {
            throw new IllegalArgumentException("无法解析 Git Commit：" + normalized);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    public int searchLimit(int value) { return Math.max(1, Math.min(value, 200)); }

    public String actor(String value) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException("操作者不能为空");
        return normalized;
    }

    public String mcpId(String repositoryId) { return id(repositoryId, "repositoryId") + "-readonly-git-mcp"; }

    public String deploymentId(String projectId, String environment, String serviceName) {
        return projectId(projectId) + ":" + environment(environment) + ":" + serviceName(serviceName);
    }

    private String revision(String value) {
        String normalized = text(value);
        if (normalized.startsWith("-") || normalized.contains("..") || normalized.contains(":")
                || !REVISION.matcher(normalized).matches()) {
            throw new IllegalArgumentException("revision 格式无效");
        }
        return normalized;
    }

    private String relativePath(String value, String error) {
        String normalized = text(value).replace('\\', '/');
        if (normalized.isBlank() || normalized.contains("\u0000")
                || normalized.contains("\n") || normalized.contains("\r")) {
            throw new IllegalArgumentException(error);
        }
        Path path = Path.of(normalized).normalize();
        if (path.isAbsolute() || normalized.startsWith("/") || ".".equals(path.toString()) || path.startsWith("..")) {
            throw new IllegalArgumentException(error);
        }
        return path.toString().replace('\\', '/');
    }

    private String id(String value, String field) {
        String normalized = text(value);
        if (!ID.matcher(normalized).matches()) throw new IllegalArgumentException(field + " 格式无效");
        return normalized;
    }

    private String text(String value) { return value == null ? "" : value.trim(); }
}
