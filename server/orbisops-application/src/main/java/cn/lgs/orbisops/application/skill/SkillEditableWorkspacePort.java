package cn.lgs.orbisops.application.skill;

import java.util.List;
import java.util.Set;

/** Infrastructure boundary for editable Skill file workspace mechanics. */
public interface SkillEditableWorkspacePort {

    String resolveRoot(String configuredLocation);

    void save(String root, String skillName, String markdown);

    void delete(String root, String skillName);

    void initialize(String root, List<SkillEditableFile> files);

    Set<String> deletedSkillNames(String root);

    boolean isDeleted(String root, String skillName);

    String createValidationWorkspace(String skillName, String markdown);

    void deleteWorkspace(String path);
}
