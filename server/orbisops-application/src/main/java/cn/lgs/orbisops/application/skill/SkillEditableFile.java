package cn.lgs.orbisops.application.skill;

/** Editable Skill file seed used by the filesystem workspace adapter. */
public record SkillEditableFile(String skillName, String markdown) {

    public SkillEditableFile {
        skillName = skillName == null ? "" : skillName.trim();
        markdown = markdown == null ? "" : markdown;
    }
}
