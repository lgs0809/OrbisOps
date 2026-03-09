package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.application.skill.SkillEditableWorkspacePort;
import lombok.extern.slf4j.Slf4j;
import org.springaicommunity.agent.tools.SkillsTool;

import java.io.IOException;

/** Editable Skill command boundary over the workspace port and SKILL.md codec. */
@Slf4j
final class OpsSkillEditableService {

    private final SkillEditableWorkspacePort workspacePort;
    private final OpsSkillCatalog catalog;
    private final OpsSkillLocationLoader locationLoader;
    private final OpsSkillMarkdownCodec markdownCodec;

    OpsSkillEditableService(
            SkillEditableWorkspacePort workspacePort,
            OpsSkillCatalog catalog,
            OpsSkillLocationLoader locationLoader,
            OpsSkillMarkdownCodec markdownCodec) {
        this.workspacePort = workspacePort;
        this.catalog = catalog;
        this.locationLoader = locationLoader;
        this.markdownCodec = markdownCodec;
    }

    SkillsTool.Skill save(String name, String markdown) throws IOException {
        String skillName = markdownCodec.normalizeName(name);
        String content = markdownCodec.normalizeMarkdown(markdown);
        SkillsTool.Skill parsed = markdownCodec.validate(
                skillName,
                content,
                workspacePort,
                locationLoader);
        String root = catalog.editableRoot();
        workspacePort.save(root, skillName, content);
        log.info("保存 skill 成功，name={}, root={}", skillName, root);
        return parsed;
    }

    void delete(String name) throws IOException {
        String skillName = markdownCodec.normalizeName(name);
        String root = catalog.editableRoot();
        workspacePort.delete(root, skillName);
        log.info("删除 skill 成功，name={}, root={}", skillName, root);
    }
}
