package cn.lgs.orbisops.application.analysis;

/** Application entry point for bounded MySQL slow SQL evidence collection. */
public class MySqlSlowSqlQueryApplicationService {

    private final MySqlSlowSqlQueryPort queryPort;

    public MySqlSlowSqlQueryApplicationService(MySqlSlowSqlQueryPort queryPort) {
        if (queryPort == null) {
            throw new IllegalArgumentException("MYSQL_SLOW_SQL_QUERY_PORT_REQUIRED");
        }
        this.queryPort = queryPort;
    }

    public MySqlSlowSqlQueryResult query(MySqlSlowSqlQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("MYSQL_SLOW_SQL_QUERY_REQUIRED");
        }
        MySqlSlowSqlQueryResult result = queryPort.query(query);
        return result == null
                ? new MySqlSlowSqlQueryResult(
                        false,
                        "mysql.slow_log/performance_schema",
                        "MySQL 慢 SQL 查询未返回结果。",
                        "mysql slow sql query returned null",
                        java.util.List.of())
                : result;
    }
}
