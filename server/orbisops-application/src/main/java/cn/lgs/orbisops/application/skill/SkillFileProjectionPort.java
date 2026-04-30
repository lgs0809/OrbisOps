package cn.lgs.orbisops.application.skill;
import java.util.Set;
/** Imports configured files into immutable packages, without replacing a user-owned catalog head. */
public interface SkillFileProjectionPort {
    void synchronize(SkillFileDefinition file);
    Set<String> managedIds(String scope, String projectId);
}
