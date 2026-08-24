package cn.lgs.orbisops.application.skill;
public interface SkillExperienceEmbeddingPort {
    String modelIdentity();
    float[] embed(String text,boolean query);
}
