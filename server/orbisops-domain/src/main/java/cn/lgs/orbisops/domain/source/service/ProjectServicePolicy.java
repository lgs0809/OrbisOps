package cn.lgs.orbisops.domain.source.service;

import cn.lgs.orbisops.domain.source.model.BuildProfile;
import cn.lgs.orbisops.domain.source.model.ProjectService;
import cn.lgs.orbisops.domain.source.model.ProjectServiceBuildCommand;
import cn.lgs.orbisops.domain.source.model.ProjectServiceCandidate;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ProjectServicePolicy {

    private static final Pattern ID = Pattern.compile("[A-Za-z0-9._-]{1,100}");

    public ProjectServiceCandidate normalize(ProjectServiceCandidate candidate) {
        if (candidate == null) throw new IllegalArgumentException("项目服务配置不能为空");
        String projectId = id(candidate.projectId(), "projectId");
        String serviceId = id(candidate.serviceId(), "serviceId");
        String repositoryId = id(candidate.repositoryId(), "repositoryId");
        String buildProfile = text(candidate.buildProfile()).toUpperCase();
        try {
            BuildProfile.require(buildProfile);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalArgumentException("不支持的构建配置：" + text(candidate.buildProfile()));
        }
        String name = text(candidate.name());
        if (name.isBlank()) name = serviceId;
        return new ProjectServiceCandidate(
                projectId,
                serviceId,
                name,
                repositoryId,
                relativePath(candidate.modulePath(), "modulePath", true),
                buildProfile,
                relativePath(candidate.artifactPath(), "artifactPath", false),
                text(candidate.deploymentResourceId()),
                httpUrl(candidate.healthUrl()),
                urls(candidate.smokeUrls()));
    }

    public ProjectServiceBuildCommand buildCommand(ProjectService service, Path repositoryRoot) {
        if (service == null) throw new IllegalArgumentException("SOURCE_PROJECT_SERVICE_REQUIRED");
        if (repositoryRoot == null) throw new IllegalArgumentException("SOURCE_REPOSITORY_ROOT_REQUIRED");
        Path normalizedRoot = repositoryRoot.normalize();
        Path moduleRoot = normalizedRoot.resolve(service.modulePath()).normalize();
        if (!moduleRoot.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("服务模块路径越界");
        }
        return switch (service.buildProfile()) {
            case MAVEN_VERIFY -> new ProjectServiceBuildCommand(
                    moduleRoot, List.of("mvn", "-q", "test", "package"));
            case NPM_TEST_BUILD -> new ProjectServiceBuildCommand(
                    moduleRoot, List.of("npm", "test"));
            case MAKE_CI -> new ProjectServiceBuildCommand(
                    normalizedRoot, List.of("make", "ci"));
        };
    }

    public String projectId(String value) {
        return id(value, "projectId");
    }

    public String serviceId(String value) {
        return id(value, "serviceId");
    }

    private String relativePath(String value, String field, boolean defaultRoot) {
        String normalized = text(value);
        if (normalized.isBlank()) return defaultRoot ? "." : "";
        Path path = Path.of(normalized).normalize();
        if (path.isAbsolute() || path.startsWith("..")) {
            throw new IllegalArgumentException(field + " 必须是仓库内相对路径");
        }
        String result = path.toString().replace('\\', '/');
        return result.isBlank() && defaultRoot ? "." : result;
    }

    private String httpUrl(String value) {
        String normalized = text(value);
        if (normalized.isBlank()) return "";
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            throw new IllegalArgumentException("健康检查地址必须使用 http/https");
        }
        return normalized;
    }

    private List<String> urls(List<String> values) {
        List<String> result = new ArrayList<>();
        if (values == null) return List.of();
        for (String value : values) {
            String normalized = httpUrl(value);
            if (!normalized.isBlank() && !result.contains(normalized)) result.add(normalized);
        }
        return List.copyOf(result);
    }

    private String id(String value, String field) {
        String normalized = text(value);
        if (!ID.matcher(normalized).matches()) throw new IllegalArgumentException(field + " 格式无效");
        return normalized;
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
