package cn.lgs.orbisops.trigger.ops.skill;

import org.springaicommunity.agent.tools.SkillsTool;
import org.springaicommunity.agent.utils.Skills;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;

/** Spring Resource and filesystem adapter for the Skills SDK. */
final class OpsSkillLocationLoader {

    private final ResourceLoader resourceLoader;

    OpsSkillLocationLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    List<SkillsTool.Skill> load(String location) {
        List<SkillsTool.Skill> discovered;
        if (location.startsWith("classpath:") || location.startsWith("file:")) {
            Resource resource = resourceLoader.getResource(location);
            discovered = Skills.loadResource(resource);
        } else {
            Path path = Path.of(location);
            if (!path.isAbsolute()) {
                path = Path.of(System.getProperty("user.dir")).resolve(path);
            }
            discovered = Skills.loadDirectory(path.toAbsolutePath().normalize().toString());
        }
        // The SDK 0.7 parser flattens YAML values to strings. Keep its discovery/base-path
        // semantics, but parse the original front matter before routing or editable bootstrap.
        return discovered.stream().map(this::withTypedFrontMatter).toList();
    }

    private SkillsTool.Skill withTypedFrontMatter(SkillsTool.Skill discovered) {
        String path = discovered.basePath() + "/SKILL.md";
        Resource resource;
        if (path.startsWith("/")) resource = new FileSystemResource(path);
        else if (path.startsWith("file:") || path.startsWith("jar:") || path.startsWith("classpath:")) {
            resource = resourceLoader.getResource(path);
        } else if (!path.contains(":")) {
            resource = resourceLoader.getResource("classpath:" + path.replaceFirst("^BOOT-INF/classes/", ""));
        } else {
            throw new IllegalArgumentException("SKILL_FILE_LOCATION_UNSUPPORTED");
        }
        try (var stream = resource.getInputStream()) {
            byte[] bytes = stream.readNBytes(256 * 1024 + 1);
            if (bytes.length > 256 * 1024) throw new IllegalArgumentException("SKILL_FILE_TOO_LARGE");
            String raw = new String(bytes, StandardCharsets.UTF_8).replace("\r\n", "\n").replace('\r', '\n').strip();
            if (!raw.startsWith("---\n")) throw new IllegalArgumentException("SKILL_FRONT_MATTER_REQUIRED");
            int end = raw.indexOf("\n---\n", 4);
            if (end < 0) throw new IllegalArgumentException("SKILL_FRONT_MATTER_UNCLOSED");
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setMaxAliasesForCollections(0);
            options.setNestingDepthLimit(20);
            options.setCodePointLimit(256 * 1024);
            Object value = new Yaml(new SafeConstructor(options)).load(raw.substring(4, end));
            if (!(value instanceof Map<?, ?> input)) throw new IllegalArgumentException("SKILL_FRONT_MATTER_INVALID");
            Map<String, Object> frontMatter = new LinkedHashMap<>();
            input.forEach((key, item) -> {
                if (!(key instanceof String text)) throw new IllegalArgumentException("SKILL_FRONT_MATTER_KEY_INVALID");
                frontMatter.put(text, item);
            });
            return new SkillsTool.Skill(discovered.basePath(), frontMatter, raw.substring(end + 5).strip());
        } catch (IOException failure) {
            throw new IllegalStateException("SKILL_FILE_READ_FAILED", failure);
        }
    }

    boolean sameLocation(String location, String target) {
        if (!StringUtils.hasText(location) || !StringUtils.hasText(target)) {
            return false;
        }
        if (location.startsWith("classpath:")) {
            return false;
        }
        String normalized = location.startsWith("file:")
                ? location.substring("file:".length())
                : location;
        Path path = Path.of(normalized);
        if (!path.isAbsolute()) {
            path = Path.of(System.getProperty("user.dir")).resolve(path);
        }
        return path.toAbsolutePath().normalize()
                .equals(Path.of(target).toAbsolutePath().normalize());
    }
}
