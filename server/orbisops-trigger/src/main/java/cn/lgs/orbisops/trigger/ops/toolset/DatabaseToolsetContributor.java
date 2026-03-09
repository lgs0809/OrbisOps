package cn.lgs.orbisops.trigger.ops.toolset;

import java.util.List;

public final class DatabaseToolsetContributor implements OpsBuiltInToolsetContributor {

    private final OpsBuiltInToolsetDefinitions definitions = new OpsBuiltInToolsetDefinitions();

    @Override
    public String contributorId() {
        return "database";
    }

    @Override
    public int order() {
        return 200;
    }

    @Override
    public List<OpsToolsetDefinition> definitions() {
        OpsToolDefinitionFactory tools = definitions.tools();
        return List.of(
                definitions.toolset(
                        "db.mysql.readonly",
                        "MySQL 只读",
                        "查询慢 SQL、Explain 和只读 SELECT",
                        "LOCAL_MYSQL",
                        true,
                        List.of(
                                tools.read("mysql_query_readonly", "执行只读 SQL", "LOCAL_MYSQL"),
                                tools.read("mysql_explain", "Explain SELECT", "LOCAL_MYSQL"),
                                tools.read("mysql_show_tables", "列出表", "LOCAL_MYSQL"),
                                tools.read("mysql_show_indexes", "查看索引", "LOCAL_MYSQL"),
                                tools.read("mysql_show_status", "查看状态", "LOCAL_MYSQL"),
                                tools.validation("mysql_sql_dry_run", "MySQL dry-run 占位，不伪造成功"),
                                tools.validation("mysql_transaction_validate", "MySQL 事务验证占位，不伪造成功"),
                                tools.read("mysql_config_precondition_check", "配置表前置条件检查", "LOCAL_MYSQL"))),
                definitions.targetWrite(
                        "db.mysql.change",
                        "MySQL 变更",
                        "mysql_execute_change"));
    }
}
