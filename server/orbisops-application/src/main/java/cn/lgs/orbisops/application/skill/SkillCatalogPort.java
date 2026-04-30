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

    /** Runtime reads must not reconstruct or fetch every package body. */
    default List<SkillCatalogSnapshot> listRuntimeGlobalEntries() { return listGlobalEntries(); }

    default List<SkillCatalogSnapshot> listRuntimeProjectEntries(String projectId) { return listProjectEntries(projectId); }

    /** A global catalog entry is not itself a project grant. */
    default List<String> configuredGlobalSkillIds(String projectId) { return List.of(); }
}
