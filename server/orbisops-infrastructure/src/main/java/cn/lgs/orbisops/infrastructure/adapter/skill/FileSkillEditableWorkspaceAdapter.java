package cn.lgs.orbisops.infrastructure.adapter.skill;

import cn.lgs.orbisops.application.skill.SkillEditableFile;
import cn.lgs.orbisops.application.skill.SkillEditableWorkspacePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Filesystem implementation of the editable Skill workspace. */
@Slf4j
@Repository
public class FileSkillEditableWorkspaceAdapter
        implements SkillEditableWorkspacePort {

    private static final String SKILL_FILE = "SKILL.md";
    private static final String DELETE_MARKER = ".deleted";
    private static final DateTimeFormatter BACKUP_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Override
    public String resolveRoot(String configuredLocation) {
        Path path = Path.of(configuredLocation == null ? "" : configuredLocation.trim());
        if (!path.isAbsolute()) {
            path = Path.of(System.getProperty("user.dir")).resolve(path);
        }
        return path.toAbsolutePath().normalize().toString();
    }

    @Override
    public void save(String root, String skillName, String markdown) {
        Path rootPath = root(root);
        Path skillFile = skillFile(rootPath, skillName);
        try {
            Files.createDirectories(skillFile.getParent());
            if (Files.exists(skillFile)) {
                Files.copy(
                        skillFile,
                        skillFile.resolveSibling(
                                SKILL_FILE + ".bak." + timestamp()));
            }
            Files.deleteIfExists(deleteMarker(rootPath, skillName));
            Files.writeString(skillFile, markdown, StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("保存 Skill 文件失败：" + error.getMessage(), error);
        }
    }

    @Override
    public void delete(String root, String skillName) {
        Path rootPath = root(root);
        Path skillFile = skillFile(rootPath, skillName);
        try {
            Files.createDirectories(skillFile.getParent());
            if (Files.exists(skillFile)) {
                Files.move(
                        skillFile,
                        skillFile.resolveSibling(
                                SKILL_FILE + ".deleted." + timestamp()));
            }
            Files.writeString(
                    deleteMarker(rootPath, skillName),
                    "deletedAt=" + LocalDateTime.now() + "\n",
                    StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("删除 Skill 文件失败：" + error.getMessage(), error);
        }
    }

    @Override
    public void initialize(String root, List<SkillEditableFile> files) {
        Path rootPath = root(root);
        try {
            Files.createDirectories(rootPath);
            if (files == null) return;
            for (SkillEditableFile file : files) {
                if (file == null || file.skillName().isBlank()) continue;
                Path target = skillFile(rootPath, file.skillName());
                Files.createDirectories(target.getParent());
                if (!Files.exists(target)) {
                    Files.writeString(
                            target,
                            file.markdown(),
                            StandardCharsets.UTF_8);
                }
            }
        } catch (IOException error) {
            throw new IllegalStateException(
                    "初始化可编辑 Skill 目录失败：" + error.getMessage(),
                    error);
        }
    }

    @Override
    public Set<String> deletedSkillNames(String root) {
        Path rootPath = root(root);
        if (!Files.isDirectory(rootPath)) return Set.of();
        try (var stream = Files.list(rootPath)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(path -> Files.exists(path.resolve(DELETE_MARKER)))
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IOException error) {
            log.warn("读取已删除 Skill 标记失败，root={}", rootPath, error);
            return Set.of();
        }
    }

    @Override
    public boolean isDeleted(String root, String skillName) {
        return Files.exists(deleteMarker(root(root), skillName));
    }

    @Override
    public String createValidationWorkspace(
            String skillName,
            String markdown) {
        try {
            Path tempRoot = Files.createTempDirectory("ops-skill-validate-");
            Path skillFile = skillFile(tempRoot, skillName);
            Files.createDirectories(skillFile.getParent());
            Files.writeString(skillFile, markdown, StandardCharsets.UTF_8);
            return tempRoot.toAbsolutePath().normalize().toString();
        } catch (IOException error) {
            throw new IllegalStateException(
                    "创建 Skill 校验工作区失败：" + error.getMessage(),
                    error);
        }
    }

    @Override
    public void deleteWorkspace(String path) {
        if (path == null || path.isBlank()) return;
        Path root = Path.of(path).toAbsolutePath().normalize();
        if (!Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (IOException error) {
                    log.debug("清理临时 Skill 文件失败，path={}", item, error);
                }
            });
        } catch (IOException error) {
            log.debug("清理临时 Skill 目录失败，path={}", root, error);
        }
    }

    private Path root(String value) {
        String resolved = resolveRoot(value);
        return Path.of(resolved);
    }

    private Path skillFile(Path root, String skillName) {
        Path file = root.resolve(skillName).resolve(SKILL_FILE).normalize();
        assertInside(root, file);
        return file;
    }

    private Path deleteMarker(Path root, String skillName) {
        Path marker = root.resolve(skillName).resolve(DELETE_MARKER).normalize();
        assertInside(root, marker);
        return marker;
    }

    private void assertInside(Path root, Path child) {
        if (!child.startsWith(root)) {
            throw new SecurityException("SKILL_EDITABLE_PATH_NOT_ALLOWED");
        }
    }

    private String timestamp() {
        return BACKUP_TIME_FORMATTER.format(LocalDateTime.now());
    }
}
