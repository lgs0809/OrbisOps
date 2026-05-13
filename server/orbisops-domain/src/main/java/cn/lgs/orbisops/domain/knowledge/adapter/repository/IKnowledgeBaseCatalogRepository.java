package cn.lgs.orbisops.domain.knowledge.adapter.repository;

import cn.lgs.orbisops.domain.knowledge.model.KnowledgeBaseCatalogEntry;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeBaseCatalogKey;
import cn.lgs.orbisops.domain.knowledge.model.KnowledgeScope;

import java.util.List;
import java.util.Optional;

public interface IKnowledgeBaseCatalogRepository {

    List<KnowledgeBaseCatalogEntry> list(KnowledgeScope scope, String projectId);

    Optional<KnowledgeBaseCatalogEntry> find(KnowledgeBaseCatalogKey key);

    void save(KnowledgeBaseCatalogEntry entry);
}
