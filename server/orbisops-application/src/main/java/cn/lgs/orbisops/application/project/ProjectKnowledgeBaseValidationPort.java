package cn.lgs.orbisops.application.project;

/** Optional external knowledge-base validation used by project definition commands. */
public interface ProjectKnowledgeBaseValidationPort {

    void validateIfConfigured(String knowledgeBaseId);
}
