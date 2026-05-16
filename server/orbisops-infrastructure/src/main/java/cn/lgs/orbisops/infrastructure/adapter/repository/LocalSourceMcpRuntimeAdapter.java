package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.source.SourceMcpRuntimePort;
import cn.lgs.orbisops.domain.source.model.SourceMcpRuntimeSpec;
import cn.lgs.orbisops.domain.source.model.SourceRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LocalSourceMcpRuntimeAdapter implements SourceMcpRuntimePort {

    private final String gitBinary;
    private final int timeoutSeconds;
    private final long maxFileBytes;
    private final long maxOutputBytes;

    public LocalSourceMcpRuntimeAdapter(
            @Value("${orbisops.source-repository.git-binary:git}") String gitBinary,
            @Value("${orbisops.source-repository.command-timeout-seconds:8}") int timeoutSeconds,
            @Value("${orbisops.source-repository.max-file-bytes:1048576}") long maxFileBytes,
            @Value("${orbisops.source-repository.max-command-output-bytes:2097152}") long maxOutputBytes) {
        this.gitBinary = gitBinary;
        this.timeoutSeconds = timeoutSeconds;
        this.maxFileBytes = maxFileBytes;
        this.maxOutputBytes = maxOutputBytes;
    }

    @Override
    public SourceMcpRuntimeSpec describe(SourceRepository repository) {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("GIT_REPOSITORY_ROOT", repository.localPath());
        env.put("GIT_DEFAULT_COMMIT", repository.defaultCommitSha());
        env.put("GIT_BINARY", StringUtils.hasText(gitBinary) ? gitBinary.trim() : "git");
        env.put("GIT_TIMEOUT_MS", String.valueOf(Math.max(1, timeoutSeconds) * 1000));
        env.put("GIT_MAX_FILE_BYTES", String.valueOf(Math.max(1024, maxFileBytes)));
        env.put("GIT_MAX_OUTPUT_BYTES", String.valueOf(Math.max(1024, maxOutputBytes)));
        return new SourceMcpRuntimeSpec(
                repository.mcpId(),
                "项目只读 Git 仓库：" + repository.name(),
                "stdio",
                "node",
                List.of(mcpScriptPath().toString()),
                env,
                Math.max(1, timeoutSeconds),
                Map.of("*", "read_only"),
                List.of(
                        "git_repository_info",
                        "git_read_file",
                        "git_search_code",
                        "git_list_files",
                        "git_diff_summary"));
    }

    private Path mcpScriptPath() {
        List<Path> candidates = new ArrayList<>();
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (int depth = 0; current != null && depth < 6; depth++) {
            candidates.add(current.resolve(Path.of("scripts", "mcp", "git-readonly-mcp-server.mjs")));
            current = current.getParent();
        }
        return candidates.stream()
                .filter(Files::exists)
                .findFirst()
                .orElse(candidates.get(0))
                .toAbsolutePath()
                .normalize();
    }
}
