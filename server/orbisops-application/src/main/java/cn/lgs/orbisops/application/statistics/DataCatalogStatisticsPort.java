package cn.lgs.orbisops.application.statistics;

import cn.lgs.orbisops.domain.statistics.model.DataCatalogCounts;

public interface DataCatalogStatisticsPort {

    DataCatalogCounts loadCatalogCounts();
}
