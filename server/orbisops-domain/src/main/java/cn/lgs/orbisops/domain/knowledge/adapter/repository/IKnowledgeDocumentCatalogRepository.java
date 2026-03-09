package cn.lgs.orbisops.domain.knowledge.adapter.repository;

import cn.lgs.orbisops.domain.knowledge.model.KnowledgeChunkCatalogEntry;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeDocumentCatalogEntry;

import java.util.List;

public interface IKnowledgeDocumentCatalogRepository {

    void saveSubmitted(List<KnowledgeDocumentCatalogEntry> documents);

    void synchronize(KnowledgeDocumentCatalogEntry document,
                     List<KnowledgeChunkCatalogEntry> chunks);
}
