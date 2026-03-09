package cn.lgs.orbisops.domain.statistics.adapter.repository;

import cn.lgs.orbisops.domain.statistics.model.ExecutionStatisticsFacts;

public interface IDataStatisticsReadRepository {

    ExecutionStatisticsFacts loadExecutionFacts();
}
