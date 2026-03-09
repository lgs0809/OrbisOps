package cn.lgs.orbisops.trigger.application.statistics;

import cn.lgs.orbisops.application.statistics.DataCatalogStatisticsPort;
import cn.lgs.orbisops.application.statistics.DataStatisticsQueryApplicationService;
import cn.lgs.orbisops.domain.statistics.adapter.repository.IDataStatisticsReadRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataStatisticsApplicationConfiguration {

    @Bean
    public DataStatisticsQueryApplicationService dataStatisticsQueryApplicationService(
            DataCatalogStatisticsPort catalog,
            IDataStatisticsReadRepository executionStatistics) {
        return new DataStatisticsQueryApplicationService(catalog, executionStatistics);
    }
}
