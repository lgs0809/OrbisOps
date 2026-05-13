package cn.lgs.orbisops.application.rag;

import java.util.List;

/** Persistence boundary for the typed knowledge-base catalog. */
public interface RagOrderCatalogPort {

    boolean insert(RagOrderDefinition definition);

    boolean updateById(RagOrderDefinition definition);

    boolean updateByRagId(RagOrderDefinition definition);

    boolean deleteById(Long id);

    boolean deleteByRagId(String ragId);

    RagOrderDefinition queryById(Long id);

    RagOrderDefinition queryByRagId(String ragId);

    List<RagOrderDefinition> queryEnabled();

    List<RagOrderDefinition> queryByKnowledgeTag(String knowledgeTag);

    List<RagOrderDefinition> queryAll();
}
