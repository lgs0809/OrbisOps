package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.repair.RepairExecutionCommand;
import cn.lgs.orbisops.application.repair.RepairWorkspaceExecutionPort;
import cn.lgs.orbisops.application.repair.RepairWorktreeCommand;
import cn.lgs.orbisops.domain.repair.model.RepairArtifactValidation;
import cn.lgs.orbisops.domain.repair.model.RepairCleanupResult;
import cn.lgs.orbisops.domain.repair.model.RepairCommitResult;
import cn.lgs.orbisops.domain.repair.model.RepairDiffSnapshot;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspace;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceStatus;
import cn.lgs.orbisops.domain.repair.service.RepairWorkspacePolicy;
import cn.lgs.orbisops.domain.source.model.BuildProfile;
import cn.lgs.orbisops.domain.source.model.ProjectService;
import cn.lgs.orbisops.domain.source.model.ProjectServiceBuildCommand;
import cn.lgs.orbisops.domain.source.model.SourceRepository;
import cn.lgs.orbisops.domain.source.service.ProjectServicePolicy;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class LocalRepairWorkspaceExecutionAdapter implements RepairWorkspaceExecutionPort {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final boolean enabled;
    private final String worktreeRoot;
    private final String artifactRoot;
    private final int commandTimeoutSeconds;
    private final int maxLogBytes;
    private final String runner;
    private final String dockerBinary;
    private final String dockerMavenImage;
    private final String dockerNodeImage;
    private final String dockerMakeImage;
    private final RepairWorkspacePolicy repairPolicy = new RepairWorkspacePolicy();
    private final ProjectServicePolicy servicePolicy = new ProjectServicePolicy();

    public LocalRepairWorkspaceExecutionAdapter(
            @Value("${orbisops.repair.enabled:false}") boolean enabled,
            @Value("${orbisops.repair.worktree-root:${orbisops.repair.sandbox-root:${java.io.tmpdir}/orbisops-repair/worktrees}}") String worktreeRoot,
            @Value("${orbisops.repair.artifact-root:${java.io.tmpdir}/orbisops-repair/artifacts}") String artifactRoot,
            @Value("${orbisops.repair.command-timeout-seconds:900}") int commandTimeoutSeconds,
            @Value("${orbisops.repair.max-log-bytes:1048576}") int maxLogBytes,
            @Value("${orbisops.repair.runner:docker}") String runner,
            @Value("${orbisops.repair.docker.binary:docker}") String dockerBinary,
            @Value("${orbisops.repair.docker.maven-image:maven:3.9.9-eclipse-temurin-17}") String dockerMavenImage,
            @Value("${orbisops.repair.docker.node-image:node:20-bookworm-slim}") String dockerNodeImage,
            @Value("${orbisops.repair.docker.make-image:buildpack-deps:bookworm}") String dockerMakeImage) {
        this.enabled = enabled;
        this.worktreeRoot = worktreeRoot;
        this.artifactRoot = artifactRoot;
        this.commandTimeoutSeconds = commandTimeoutSeconds;
        this.maxLogBytes = maxLogBytes;
        this.runner = runner;
        this.dockerBinary = dockerBinary;
        this.dockerMavenImage = dockerMavenImage;
        this.dockerNodeImage = dockerNodeImage;
        this.dockerMakeImage = dockerMakeImage;
    }

    @PostConstruct
    public void initialize() {
        if (!enabled) return;
        try {
            Files.createDirectories(root(worktreeRoot));
            Files.createDirectories(root(artifactRoot));
        } catch (IOException e) {
            throw new IllegalStateException("初始化修复工作区目录失败", e);
        }
    }

    @Override
    public RepairWorkspace createAndVerify(RepairExecutionCommand command) {
        if (command == null || command.candidate() == null
                || command.service() == null || command.repository() == null) {
            throw new IllegalArgumentException("REPAIR_EXECUTION_COMMAND_REQUIRED");
        }
        ProjectService service = command.service();
        SourceRepository repository = command.repository();
        String workspaceId = command.workspaceId();
        Path worktree = worktreePath(workspaceId);
        Path patchFile = safeChild(worktree.getParent(), workspaceId + ".patch");
        RepairWorkspaceStatus status = RepairWorkspaceStatus.PREPARING;
        int exitCode = -1;
        String testLog = "";
        List<String> changedFiles = List.of();
        String artifactPathValue = "";
        String artifactSha = "";
        long artifactSize = 0L;
        String verifiedCommit = "";
        ProjectServiceBuildCommand build = null;
        try {
            Files.createDirectories(worktree.getParent());
            run(List.of("git", "-C", repository.localPath(), "worktree", "add", "--detach",
                            worktree.toString(), command.baseCommit()),
                    Path.of(repository.localPath()), Duration.ofSeconds(60));
            Files.writeString(patchFile, command.candidate().unifiedDiff(), StandardCharsets.UTF_8);
            run(List.of("git", "-C", worktree.toString(), "apply", "--check", patchFile.toString()),
                    worktree, Duration.ofSeconds(30));
            run(List.of("git", "-C", worktree.toString(), "apply", "--whitespace=error", patchFile.toString()),
                    worktree, Duration.ofSeconds(30));
            changedFiles = commandLines(run(
                    List.of("git", "-C", worktree.toString(), "diff", "--name-only"),
                    worktree, Duration.ofSeconds(30)).output());
            validateChangedFiles(changedFiles, service.modulePath(), worktree);
            String appliedDiff = run(List.of(
                            "git", "-C", worktree.toString(), "diff", "--binary", "--no-ext-diff"),
                    worktree, Duration.ofSeconds(30)).output();
            build = servicePolicy.buildCommand(service, worktree);
            CommandResult test = runBuild(service, worktree, build,
                    Duration.ofSeconds(Math.max(30, commandTimeoutSeconds)));
            exitCode = test.exitCode();
            testLog = truncate(test.output());
            status = exitCode == 0 ? RepairWorkspaceStatus.VERIFIED : RepairWorkspaceStatus.TEST_FAILED;
            String postBuildDiff = run(List.of(
                            "git", "-C", worktree.toString(), "diff", "--binary", "--no-ext-diff"),
                    worktree, Duration.ofSeconds(30)).output();
            if (!appliedDiff.equals(postBuildDiff)) {
                status = RepairWorkspaceStatus.BUILD_MUTATED_SOURCE;
                testLog = truncate(testLog + "\nBuild changed source files outside the approved patch.");
            }
            if (exitCode == 0 && !service.artifactPath().isBlank()) {
                Path produced = safeChild(worktree, service.artifactPath());
                if (!Files.isRegularFile(produced)) {
                    status = RepairWorkspaceStatus.ARTIFACT_MISSING;
                    testLog = truncate(testLog + "\nExpected artifact not found: " + service.artifactPath());
                } else {
                    Path artifactDirectory = safeChild(root(artifactRoot), workspaceId);
                    Files.createDirectories(artifactDirectory);
                    Path stored = artifactDirectory.resolve(produced.getFileName()).normalize();
                    if (!stored.startsWith(artifactDirectory)) {
                        throw new IllegalArgumentException("制品路径越界");
                    }
                    Files.copy(produced, stored, StandardCopyOption.REPLACE_EXISTING);
                    artifactPathValue = stored.toString();
                    artifactSha = sha256(stored);
                    artifactSize = Files.size(stored);
                }
            }
            if (status == RepairWorkspaceStatus.VERIFIED) {
                configureGitIdentity(worktree);
                run(List.of("git", "-C", worktree.toString(), "add", "-A", "--", service.modulePath()),
                        worktree, Duration.ofSeconds(30));
                List<String> stagedFiles = commandLines(run(
                        List.of("git", "-C", worktree.toString(), "diff", "--cached", "--name-only"),
                        worktree, Duration.ofSeconds(30)).output());
                if (!new java.util.LinkedHashSet<>(stagedFiles)
                        .equals(new java.util.LinkedHashSet<>(changedFiles))) {
                    throw new IllegalStateException("构建过程修改了补丁之外的文件：" + stagedFiles);
                }
                run(List.of("git", "-C", worktree.toString(), "commit",
                                "-m", "ops repair candidate " + workspaceId),
                        worktree, Duration.ofSeconds(60));
                verifiedCommit = run(List.of("git", "-C", worktree.toString(), "rev-parse", "HEAD"),
                        worktree, Duration.ofSeconds(10)).output().trim();
                run(List.of("git", "-C", repository.localPath(), "update-ref",
                                "refs/ops-repair/" + workspaceId, verifiedCommit),
                        Path.of(repository.localPath()), Duration.ofSeconds(10));
            }
        } catch (Exception e) {
            status = RepairWorkspaceStatus.FAILED;
            testLog = truncate((testLog + "\n" + concise(e)).trim());
        } finally {
            try { Files.deleteIfExists(patchFile); }
            catch (IOException ignored) { }
        }
        String now = now();
        return new RepairWorkspace(
                workspaceId,
                command.candidate().projectId(),
                command.candidate().serviceId(),
                repository.repositoryId(),
                command.candidate().environment(),
                command.baseCommit(),
                verifiedCommit,
                status,
                command.candidate().summary(),
                command.candidate().unifiedDiff(),
                changedFiles,
                service.buildProfile().name(),
                build == null ? "" : String.join(" ", build.command()),
                exitCode,
                testLog,
                artifactPathValue,
                artifactSha,
                artifactSize,
                command.actor(),
                now,
                now);
    }

    @Override
    public RepairWorkspace enterWorktree(RepairWorktreeCommand command) {
        if (command == null || command.service() == null || command.repository() == null) {
            throw new IllegalArgumentException("REPAIR_WORKTREE_COMMAND_REQUIRED");
        }
        SourceRepository repository = command.repository();
        try {
            run(List.of("git", "-C", repository.localPath(), "cat-file", "-e",
                            command.baseCommit() + "^{commit}"),
                    Path.of(repository.localPath()), Duration.ofSeconds(10));
        } catch (Exception e) {
            throw new IllegalArgumentException("baseCommit 不存在于登记仓库：" + command.baseCommit(), e);
        }
        String branch = "ops/repair/" + command.serviceId() + "/" + command.workspaceId();
        Path worktree = worktreePath(command.workspaceId());
        try {
            Files.createDirectories(worktree.getParent());
            run(List.of("git", "-C", repository.localPath(), "worktree", "add", "-b",
                            branch, worktree.toString(), command.baseCommit()),
                    Path.of(repository.localPath()), Duration.ofSeconds(60));
        } catch (Exception e) {
            throw new IllegalStateException("创建受控 repair worktree 失败：" + e.getMessage(), e);
        }
        String now = now();
        return new RepairWorkspace(
                command.workspaceId(), command.projectId(), command.serviceId(), repository.repositoryId(),
                command.environment(), command.baseCommit(), "", RepairWorkspaceStatus.ACTIVE,
                "受控 repair worktree: " + branch, "", List.of(), "", "", null, "", "", "", 0L,
                command.actor(), now, now);
    }

    @Override
    public Path worktreePath(String workspaceId) {
        return safeChild(root(worktreeRoot), workspaceId);
    }

    @Override
    public RepairDiffSnapshot computeDiff(RepairWorkspace workspace) {
        Path worktree = worktreePath(workspace.workspaceId());
        try {
            String diff = run(List.of("git", "-C", worktree.toString(), "diff", "--binary", "--no-ext-diff",
                            workspace.baseCommit()), worktree, Duration.ofSeconds(30)).output();
            List<String> files = commandLines(run(List.of(
                            "git", "-C", worktree.toString(), "diff", "--name-only", workspace.baseCommit()),
                    worktree, Duration.ofSeconds(30)).output());
            String stat = run(List.of(
                            "git", "-C", worktree.toString(), "diff", "--stat", workspace.baseCommit()),
                    worktree, Duration.ofSeconds(30)).output();
            String head = run(List.of("git", "-C", worktree.toString(), "rev-parse", "HEAD"),
                    worktree, Duration.ofSeconds(10)).output().trim();
            return new RepairDiffSnapshot(
                    workspace.workspaceId(), workspace.baseCommit(), head, files,
                    truncate(stat), sha256(diff), diff.getBytes(StandardCharsets.UTF_8).length);
        } catch (Exception e) {
            throw new IllegalStateException("计算 repair diff 失败：" + e.getMessage(), e);
        }
    }

    @Override
    public RepairCommitResult commit(RepairWorkspace workspace, String message, String actor) {
        Path worktree = worktreePath(workspace.workspaceId());
        String commitMessage = StringUtils.hasText(message) ? message.trim() : "ops repair " + workspace.workspaceId();
        if (!commitMessage.contains(workspace.workspaceId())) {
            commitMessage += " [" + workspace.workspaceId() + "]";
        }
        RepairDiffSnapshot before = computeDiff(workspace);
        try {
            configureGitIdentity(worktree);
            run(List.of("git", "-C", worktree.toString(), "add", "-A"),
                    worktree, Duration.ofSeconds(30));
            run(List.of("git", "-C", worktree.toString(), "commit", "-m", commitMessage),
                    worktree, Duration.ofSeconds(60));
            String commit = run(List.of("git", "-C", worktree.toString(), "rev-parse", "HEAD"),
                    worktree, Duration.ofSeconds(10)).output().trim();
            RepairDiffSnapshot after = computeDiff(workspace);
            RepairDiffSnapshot resultDiff = new RepairDiffSnapshot(
                    after.workspaceId(), after.baseCommit(), after.currentHead(), before.changedFiles(),
                    after.diffSummary(), after.diffHash(), after.diffBytes());
            return new RepairCommitResult(resultDiff, commit, RepairWorkspaceStatus.COMMITTED, actor);
        } catch (Exception e) {
            throw new IllegalStateException("提交 repair commit 失败：" + e.getMessage(), e);
        }
    }

    @Override
    public RepairArtifactValidation validateArtifact(
            RepairWorkspace workspace,
            String artifactPath,
            String artifactSha256) {
        Path artifact = Path.of(artifactPath).toAbsolutePath().normalize();
        Path allowedRoot = root(artifactRoot);
        if (!artifact.startsWith(allowedRoot) || !Files.isRegularFile(artifact)) {
            throw new IllegalArgumentException("制品不在受控归档目录或文件不存在");
        }
        try {
            String actual = sha256(artifact);
            if (!actual.equalsIgnoreCase(artifactSha256)) {
                throw new IllegalArgumentException("制品文件 SHA-256 已变化");
            }
            return new RepairArtifactValidation(
                    workspace.workspaceId(), artifact.toString(), actual, Files.size(artifact),
                    workspace.baseCommit(), workspace.verifiedCommit(), workspace.changedFiles(),
                    workspace.testProfile(), workspace.testExitCode());
        } catch (IOException e) {
            throw new IllegalStateException("读取修复制品失败", e);
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException invalid) throw invalid;
            throw new IllegalStateException("校验修复制品失败", e);
        }
    }

    @Override
    public RepairCleanupResult cleanup(
            RepairWorkspace workspace,
            SourceRepository repository,
            boolean forceRemoveWorktree) {
        Path worktree = worktreePath(workspace.workspaceId());
        boolean existed = Files.exists(worktree);
        try {
            if (repository != null && existed) {
                List<String> command = new ArrayList<>(List.of(
                        "git", "-C", repository.localPath(), "worktree", "remove"));
                if (forceRemoveWorktree) command.add("--force");
                command.add(worktree.toString());
                run(command, Path.of(repository.localPath()), Duration.ofSeconds(60));
            } else if (existed) {
                deleteDirectory(worktree);
            }
            return new RepairCleanupResult(
                    workspace.workspaceId(), existed ? "CLEANED" : "NO_TEMP_RESOURCE", existed,
                    worktree.toString());
        } catch (Exception e) {
            throw new IllegalStateException("清理修复工作区失败：" + e.getMessage(), e);
        }
    }

    private void validateChangedFiles(List<String> files, String modulePath, Path worktree) {
        repairPolicy.validateChangedFiles(files, modulePath);
        files.forEach(path -> {
            Path cursor = worktree.toAbsolutePath().normalize();
            for (Path part : Path.of(path).normalize()) {
                cursor = cursor.resolve(part);
                if (Files.isSymbolicLink(cursor)) {
                    throw new IllegalArgumentException("补丁不能创建或修改符号链接：" + path);
                }
            }
        });
    }

    private CommandResult runBuild(
            ProjectService service,
            Path worktree,
            ProjectServiceBuildCommand command,
            Duration timeout) throws IOException, InterruptedException {
        String mode = value(runner, "docker").toUpperCase();
        if ("HOST".equals(mode)) return run(command.command(), command.workingDirectory(), timeout);
        if (!"DOCKER".equals(mode)) throw new IllegalArgumentException("不支持的修复构建执行器：" + runner);
        Path normalizedWorktree = worktree.toAbsolutePath().normalize();
        Path workingDirectory = command.workingDirectory().toAbsolutePath().normalize();
        if (!workingDirectory.startsWith(normalizedWorktree)) {
            throw new IllegalArgumentException("构建目录不在修复工作区");
        }
        String relative = normalizedWorktree.relativize(workingDirectory).toString().replace('\\', '/');
        String containerWorkdir = "/workspace" + (relative.isBlank() ? "" : "/" + relative);
        List<String> docker = new ArrayList<>(List.of(
                value(dockerBinary, "docker"), "run", "--rm", "--network", "none", "--read-only",
                "--cap-drop", "ALL", "--security-opt", "no-new-privileges", "--pids-limit", "256",
                "--memory", "2g", "--cpus", "2", "--tmpfs", "/tmp:rw,nosuid,nodev,size=512m",
                "--volume", normalizedWorktree + ":/workspace:rw", "--workdir", containerWorkdir));
        List<String> buildCommand;
        if (service.buildProfile() == BuildProfile.MAVEN_VERIFY) {
            Path mavenRepository = Path.of(System.getProperty("user.home"), ".m2").toAbsolutePath().normalize();
            if (Files.isDirectory(mavenRepository)) {
                docker.addAll(List.of("--volume", mavenRepository + ":/root/.m2:ro"));
            }
            docker.add(value(dockerMavenImage, "maven:3.9.9-eclipse-temurin-17"));
            buildCommand = List.of("mvn", "-o", "-q", "test", "package");
        } else if (service.buildProfile() == BuildProfile.NPM_TEST_BUILD) {
            docker.addAll(List.of("--env", "HOME=/tmp/home", "--env", "npm_config_cache=/tmp/npm"));
            docker.add(value(dockerNodeImage, "node:20-bookworm-slim"));
            buildCommand = List.of("npm", "test");
        } else if (service.buildProfile() == BuildProfile.MAKE_CI) {
            docker.add(value(dockerMakeImage, "buildpack-deps:bookworm"));
            buildCommand = List.of("make", "ci");
        } else {
            throw new IllegalArgumentException("不支持的构建配置：" + service.buildProfile());
        }
        docker.addAll(buildCommand);
        return run(docker, normalizedWorktree, timeout);
    }

    private void configureGitIdentity(Path worktree) throws IOException, InterruptedException {
        run(List.of("git", "-C", worktree.toString(), "config", "user.name", "orbisops"),
                worktree, Duration.ofSeconds(10));
        run(List.of("git", "-C", worktree.toString(), "config", "user.email", "ops-agent@local"),
                worktree, Duration.ofSeconds(10));
    }

    private CommandResult run(List<String> command, Path cwd, Duration timeout)
            throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .directory(cwd.toFile())
                .redirectErrorStream(true)
                .start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Thread reader = new Thread(() -> copyLimited(process.getInputStream(), output),
                "ops-repair-command-output");
        reader.setDaemon(true);
        reader.start();
        boolean finished = process.waitFor(Math.max(1, timeout.toSeconds()), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
        reader.join(5000);
        int exit = finished ? process.exitValue() : 124;
        String text = output.toString(StandardCharsets.UTF_8);
        if (exit != 0 && command.stream().anyMatch("git"::equals)) {
            throw new IllegalStateException(
                    "命令失败：" + String.join(" ", command) + "\n" + truncate(text));
        }
        return new CommandResult(exit, text);
    }

    private void copyLimited(InputStream input, ByteArrayOutputStream output) {
        byte[] buffer = new byte[8192];
        int total = 0;
        try (input) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                int remaining = Math.max(0, Math.max(1024, maxLogBytes) - total);
                if (remaining > 0) {
                    int accepted = Math.min(read, remaining);
                    output.write(buffer, 0, accepted);
                    total += accepted;
                }
            }
        } catch (IOException ignored) { }
    }

    private List<String> commandLines(String output) {
        return output.lines().map(String::trim).filter(StringUtils::hasText).distinct().toList();
    }

    private void deleteDirectory(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
            for (Path path : stream.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(
                digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
    }

    private Path safeChild(Path root, String child) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path result = normalizedRoot.resolve(child).normalize();
        if (!result.startsWith(normalizedRoot)) throw new IllegalArgumentException("工作区路径越界");
        return result;
    }

    private Path root(String value) {
        return Path.of(value).toAbsolutePath().normalize();
    }

    private String truncate(String value) {
        String text = value == null ? "" : value;
        int max = Math.max(1024, maxLogBytes);
        return text.length() <= max ? text : text.substring(0, max) + "\n... output truncated ...";
    }

    private String concise(Exception error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private String value(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String now() {
        return TIME.format(LocalDateTime.now());
    }

    private record CommandResult(int exitCode, String output) {
    }
}
