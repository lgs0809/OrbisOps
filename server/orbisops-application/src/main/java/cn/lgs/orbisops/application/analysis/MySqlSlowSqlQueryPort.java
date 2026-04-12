package cn.lgs.orbisops.application.analysis;

/** Infrastructure boundary for collecting MySQL slow SQL evidence. */
public interface MySqlSlowSqlQueryPort {

    MySqlSlowSqlQueryResult query(MySqlSlowSqlQuery query);
}
