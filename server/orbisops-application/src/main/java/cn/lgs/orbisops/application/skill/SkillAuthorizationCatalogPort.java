package cn.lgs.orbisops.application.skill;

import java.util.List;

/** Skill catalog identities published to Project Workspace authorization. */
public interface SkillAuthorizationCatalogPort {

    List<String> projectCatalogSkillIds(String projectId);

    List<String> globalCatalogSkillIds();
}
