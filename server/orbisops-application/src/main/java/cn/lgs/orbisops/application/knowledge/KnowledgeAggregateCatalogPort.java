package cn.lgs.orbisops.application.knowledge;

import java.util.List;

public interface KnowledgeAggregateCatalogPort {

    List<KnowledgeAggregateSnapshot> listAggregates();
}
