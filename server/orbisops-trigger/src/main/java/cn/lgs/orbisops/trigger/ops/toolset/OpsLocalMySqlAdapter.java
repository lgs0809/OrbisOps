package cn.lgs.orbisops.trigger.ops.toolset;

import cn.lgs.orbisops.application.toolset.LocalMySqlApplicationService;
import cn.lgs.orbisops.application.toolset.LocalMySqlExecutionTarget;

import java.util.Map;

/** MySQL local read/precondition tool protocol adapter. */
public final class OpsLocalMySqlAdapter {

    private final LocalMySqlApplicationService service;
    private final OpsLocalAdapterSettings settings;

    public OpsLocalMySqlAdapter(
            LocalMySqlApplicationService service,
            OpsLocalAdapterSettings settings) {
        if (service == null) throw new IllegalArgumentException("LOCAL_MYSQL_SERVICE_REQUIRED");
        if (settings == null) throw new IllegalArgumentException("LOCAL_ADAPTER_SETTINGS_REQUIRED");
        this.service = service;
        this.settings = settings;
    }

    public Map<String, Object> execute(
            String toolName,
            OpsLocalToolArguments args) {
        String projectId = args.text("projectId");
        String resourceId = args.text("resourceId");
        String displayResource = resourceId.isBlank() ? "primary" : resourceId;
        LocalMySqlExecutionTarget target = new LocalMySqlExecutionTarget(
                projectId,
                resourceId,
                settings.maxRows(),
                settings.timeoutSeconds());
        return switch (toolName) {
            case "mysql_query_readonly" -> Map.of(
                    "status", "SUCCEEDED",
                    "resourceId", displayResource,
                    "rows", service.readonlyQuery(
                            target,
                            args.raw("database"),
                            args.raw("sql")));
            case "mysql_explain" -> Map.of(
                    "status", "SUCCEEDED",
                    "resourceId", displayResource,
                    "rows", service.explain(
                            target,
                            args.raw("database"),
                            args.raw("sql")));
            case "mysql_show_tables" -> Map.of(
                    "status", "SUCCEEDED",
                    "resourceId", displayResource,
                    "rows", service.showTables(
                            target,
                            args.raw("database")));
            case "mysql_show_indexes" -> Map.of(
                    "status", "SUCCEEDED",
                    "resourceId", displayResource,
                    "rows", service.showIndexes(
                            target,
                            args.raw("database"),
                            args.raw("table")));
            case "mysql_show_status" -> Map.of(
                    "status", "SUCCEEDED",
                    "resourceId", displayResource,
                    "rows", service.showStatus(
                            target,
                            args.raw("database")));
            case "mysql_sql_dry_run", "mysql_transaction_validate" -> Map.of(
                    "status", "NOT_SUPPORTED",
                    "trustedProof", false,
                    "message", "当前本地 MySQL adapter 不伪造 dry-run；请配置受控 dry-run provider 或 test database。");
            case "mysql_config_precondition_check" -> Map.of(
                    "status", "SUCCEEDED",
                    "preconditionRows", service.precondition(
                            target,
                            args.raw("database"),
                            args.asMap()));
            default -> throw new IllegalArgumentException(
                    "未知 MySQL 工具：" + toolName);
        };
    }
}
