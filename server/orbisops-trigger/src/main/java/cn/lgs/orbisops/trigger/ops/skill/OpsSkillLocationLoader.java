package cn.lgs.orbisops.trigger.ops.skill;

import org.springaicommunity.agent.tools.SkillsTool;
import org.springaicommunity.agent.utils.Skills;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.util.List;

/** Spring Resource and filesystem adapter for the Skills SDK. */
final class OpsSkillLocationLoader {

    private final ResourceLoader resourceLoader;

    OpsSkillLocationLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    List<SkillsTool.Skill> load(String location) {
        if (location.startsWith("classpath:") || location.startsWith("file:")) {
            Resource resource = resourceLoader.getResource(location);
            return Skills.loadResource(resource);
        }
        Path path = Path.of(location);
        if (!path.isAbsolute()) {
            path = Path.of(System.getProperty("user.dir")).resolve(path);
        }
        return Skills.loadDirectory(path.toAbsolutePath().normalize().toString());
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
