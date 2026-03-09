package cn.lgs.orbisops.application.skill;

import java.util.List;

/** Typed catalog read boundary; projections remain inside query/application facades. */
public interface SkillCatalogPort {

    List<SkillCatalogSnapshot> listGlobalEntries();

    SkillCatalogSnapshot getGlobalEntry(String skillId);

    List<SkillCatalogSnapshot> listProjectEntries(String projectId);

    SkillCatalogSnapshot getProjectEntry(String projectId, String skillId);

    List<String> projectCatalogSkillIds(String projectId);

    List<String> globalCatalogSkillIds();
}
