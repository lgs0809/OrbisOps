package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.application.skill.SkillEditableFile;
import cn.lgs.orbisops.application.skill.SkillEditableWorkspacePort;
import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Skill location aggregation, editable bootstrap, deletion masking, and SDK catalog loading. */
@Slf4j
final class OpsSkillCatalog {

    private final OpsSkillToolSettings settings;
    private final SkillEditableWorkspacePort workspacePort;
    private final OpsSkillLocationLoader locationLoader;
    private final OpsSkillMarkdownCodec markdownCodec;

    OpsSkillCatalog(
            OpsSkillToolSettings settings,
            SkillEditableWorkspacePort workspacePort,
            OpsSkillLocationLoader locationLoader,
            OpsSkillMarkdownCodec markdownCodec) {
        this.settings = settings;
        this.workspacePort = workspacePort;
        this.locationLoader = locationLoader;
        this.markdownCodec = markdownCodec;
    }

    List<SkillsTool.Skill> loadSkills() {
        if (!settings.enabled()) {
            return List.of();
        }
        initializeEditableSkillsIfNecessary();
        Map<String, SkillsTool.Skill> skillMap = new LinkedHashMap<>();
        Set<String> deletedSkillNames = deletedSkillNames();
        for (String location : allLocations()) {
            try {
                for (SkillsTool.Skill skill : locationLoader.load(location)) {
                    String skillName = markdownCodec.safeName(skill);
                    if (!StringUtils.hasText(skillName)) {
                        log.warn("跳过缺少 name front matter 的 skill: {}", skill.basePath());
                        continue;
                    }
                    if (!deletedSkillNames.contains(skillName)) {
                        skillMap.put(skillName, skill);
                    }
                }
            } catch (Exception error) {
                log.warn("加载 skill 失败，location={}", location, error);
            }
        }
        return new ArrayList<>(skillMap.values());
    }

    Optional<SkillsTool.Skill> findSkill(String name) {
        if (!StringUtils.hasText(name)) {
            return Optional.empty();
        }
        String normalized = name.trim();
        return loadSkills().stream()
                .filter(skill -> normalized.equals(markdownCodec.safeName(skill)))
                .findFirst();
    }

    String editableRoot() {
        return workspacePort.resolveRoot(settings.editableLocation());
    }

    private List<String> allLocations() {
        List<String> locations = new ArrayList<>(settings.configuredLocations());
        if (StringUtils.hasText(settings.editableLocation())) {
            String root = editableRoot();
            if (!locations.contains(root)) {
                locations.add(root);
            }
        }
        return locations;
    }

    private void initializeEditableSkillsIfNecessary() {
        if (!settings.editableAutoInit()
                || !StringUtils.hasText(settings.editableLocation())) {
            return;
        }
        String root = editableRoot();
        try {
            List<SkillEditableFile> files = new ArrayList<>();
            for (String location : settings.configuredLocations()) {
                if (locationLoader.sameLocation(location, root)) {
                    continue;
                }
                for (SkillsTool.Skill skill : locationLoader.load(location)) {
                    String skillName = markdownCodec.safeName(skill);
                    if (!StringUtils.hasText(skillName)
                            || workspacePort.isDeleted(root, skillName)) {
                        continue;
                    }
                    files.add(new SkillEditableFile(
                            skillName,
                            markdownCodec.toMarkdown(skill)));
                }
            }
            workspacePort.initialize(root, files);
        } catch (RuntimeException error) {
            log.warn("初始化可编辑 skill 目录失败，location={}", root, error);
        }
    }

    private Set<String> deletedSkillNames() {
        if (!StringUtils.hasText(settings.editableLocation())) {
            return Set.of();
        }
        return workspacePort.deletedSkillNames(editableRoot());
    }
}
