package cn.lgs.orbisops.application.knowledge;

import cn.lgs.orbisops.domain.knowledge.model.KnowledgeBaseCatalogEntry;

import java.util.List;

public interface KnowledgeWorkspaceCatalogPort {

    List<KnowledgeBaseCatalogEntry> listEnabledProject(String projectId);

    List<KnowledgeBaseCatalogEntry> listEnabledGlobalByIds(List<String> kbIds);
}
