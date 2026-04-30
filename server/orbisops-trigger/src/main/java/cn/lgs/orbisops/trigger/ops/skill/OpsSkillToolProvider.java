package cn.lgs.orbisops.trigger.ops.skill;

import cn.lgs.orbisops.application.skill.SkillEditableWorkspacePort;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Public Skill catalog, editable workspace, context, and ToolCallback facade. */
@Service
public class OpsSkillToolProvider implements SkillRuntimeToolProvider, SkillCatalogReader, SkillCatalogEditor {

    private final OpsSkillCatalog catalog;
    private final OpsSkillEditableService editableService;
    private final OpsSkillMarkdownCodec markdownCodec;
    private final OpsSkillContextRenderer contextRenderer;
    private final OpsSkillToolCallbackFactory callbackFactory;

    public OpsSkillToolProvider(
            ResourceLoader resourceLoader,
            SkillEditableWorkspacePort workspacePort) {
        this(
                resourceLoader,
                workspacePort,
                OpsSkillToolSettings.legacyConstructorDefaults());
    }

    @Autowired
    public OpsSkillToolProvider(
            ResourceLoader resourceLoader,
            SkillEditableWorkspacePort workspacePort,
            OpsSkillToolSettings settings) {
        OpsSkillToolSettings effective = settings == null
                ? OpsSkillToolSettings.defaults()
                : settings;
        OpsSkillLocationLoader locationLoader =
                new OpsSkillLocationLoader(resourceLoader);
        this.markdownCodec = new OpsSkillMarkdownCodec();
        this.catalog = new OpsSkillCatalog(
                effective,
                workspacePort,
                locationLoader,
                markdownCodec);
        this.editableService = new OpsSkillEditableService(
                workspacePort,
                catalog,
                locationLoader,
                markdownCodec);
        this.contextRenderer = new OpsSkillContextRenderer(markdownCodec);
        this.callbackFactory = new OpsSkillToolCallbackFactory();
    }

    public Optional<ToolCallback> buildSkillToolCallback() {
        return callbackFactory.build(loadSkills());
    }

    public Optional<ToolCallback> buildSkillToolCallback(
            Collection<String> requiredSkillNames) {
        return callbackFactory.build(contextRenderer.filterRequired(
                loadSkills(),
                requiredSkillNames));
    }

    public List<SkillSummary> listSkillSummaries() {
        return contextRenderer.summaries(loadSkills());
    }

    public String renderSkillContext(int maxChars) {
        return contextRenderer.renderFull(loadSkills(), maxChars);
    }

    public String renderSkillContext(
            Collection<String> requiredSkillNames,
            int maxChars) {
        return contextRenderer.renderFull(
                contextRenderer.filterRequired(loadSkills(), requiredSkillNames),
                maxChars);
    }

    public String renderSkillSummaryContext(int maxChars) {
        return contextRenderer.renderSummary(loadSkills(), maxChars);
    }

    public String renderSkillSummaryContext(
            Collection<String> requiredSkillNames,
            int maxChars) {
        return contextRenderer.renderSummary(
                contextRenderer.filterRequired(loadSkills(), requiredSkillNames),
                maxChars);
    }

    public List<SkillsTool.Skill> loadSkills() {
        return catalog.loadSkills();
    }

    public Optional<SkillsTool.Skill> findSkill(String name) {
        return catalog.findSkill(name);
    }

    public SkillsTool.Skill saveSkill(
            String name,
            String markdown) throws IOException {
        return editableService.save(name, markdown);
    }

    public void deleteSkill(String name) throws IOException {
        editableService.delete(name);
    }

    public String toMarkdown(SkillsTool.Skill skill) {
        return markdownCodec.toMarkdown(skill);
    }

    public record SkillSummary(
            String name,
            String description,
            String basePath) {
    }
}
