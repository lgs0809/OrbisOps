package cn.lgs.orbisops.domain.investigation.service;

import cn.lgs.orbisops.domain.investigation.model.InvestigationInsight;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Deterministic operations insight policy for collected investigation evidence. */
public final class InvestigationInsightPolicy {

    public List<InvestigationInsight> assess(Input input) {
        List<InvestigationInsight> insights = new ArrayList<>();
        Source prometheus = input.prometheus();
        Source elasticsearch = input.elasticsearch();
        Source mysqlSlowSql = input.mysqlSlowSql();
        Metrics metrics = input.metrics();
        Logs logs = input.logs();
        SlowSql slowSql = input.slowSql();

        if (prometheus.unavailable()) {
            insights.add(insight(
                    "WARN",
                    "Prometheus 数据不可用",
                    prometheus.message(),
                    "检查 Prometheus 容器、9090 端口和 scrape target。"));
        }
        if (elasticsearch.unavailable()) {
            insights.add(insight(
                    "WARN",
                    "Elasticsearch 数据不可用",
                    elasticsearch.message(),
                    "检查 Elasticsearch 容器、9200 端口和日志索引。"));
        }
        if (mysqlSlowSql.unavailable()) {
            insights.add(insight(
                    "WARN",
                    "MySQL 慢 SQL 数据不可用",
                    mysqlSlowSql.message(),
                    "检查 slow_query_log、performance_schema、数据库权限和连接池。"));
        }

        if (prometheus.queried()
                && metrics.instanceTotal() != null
                && metrics.instanceTotal() > 0
                && !Objects.equals(metrics.instanceTotal(), metrics.instanceUp())) {
            insights.add(insight(
                    "HIGH",
                    "业务实例存在下线",
                    metrics.instanceUp() + "/" + metrics.instanceTotal()
                            + " 个实例处于 UP 状态。",
                    "先检查实例进程、服务发现和 Prometheus target lastError。"));
        }

        if (prometheus.queried()
                && metrics.errorRate() != null
                && metrics.errorRate() > 5D) {
            insights.add(insight(
                    "HIGH",
                    "接口错误率偏高",
                    "当前 5xx 错误率约 " + metrics.errorRate() + "%。",
                    "优先查看最近 ERROR 日志和高 QPS 接口调用链。"));
        } else if (prometheus.queried()
                && metrics.errorRate() != null
                && metrics.errorRate() > 1D) {
            insights.add(insight(
                    "WARN",
                    "接口错误率需要关注",
                    "当前 5xx 错误率约 " + metrics.errorRate() + "%。",
                    "关注锁单、结算和 DCC 相关接口是否有异常。"));
        }

        if (elasticsearch.queried()
                && logs.errorLogs() != null
                && logs.errorLogs() > 0) {
            insights.add(insight(
                    "WARN",
                    "最近日志存在 ERROR",
                    "最近 " + input.rangeMinutes() + " 分钟 ERROR 日志 "
                            + logs.errorLogs() + " 条。",
                    "在 Kibana 以 level:ERROR 和 logger_name 过滤定位。"));
        }

        if (prometheus.queried()
                && metrics.heapMemoryUsagePercent() != null
                && metrics.heapMemoryUsagePercent() > 80D) {
            insights.add(insight(
                    "WARN",
                    "堆内存使用率较高",
                    "当前 heap 使用率约 "
                            + metrics.heapMemoryUsagePercent() + "%。",
                    "检查缓存、定时任务批量数据和 JVM GC 指标。"));
        }

        if (mysqlSlowSql.queried()
                && slowSql.slowStatements() != null
                && slowSql.slowStatements() > 0) {
            String level = slowSql.maxQueryTimeMs() != null
                    && slowSql.maxQueryTimeMs() > 3000D
                    ? "HIGH"
                    : "WARN";
            insights.add(insight(
                    level,
                    "发现 MySQL 慢 SQL",
                    "命中 " + slowSql.slowStatements()
                            + " 条慢 SQL，最高耗时约 "
                            + slowSql.maxQueryTimeMs() + "ms。",
                    "优先检查 Top SQL 的索引、扫描行数、执行计划和业务调用路径。"));
        }

        if (prometheus.queried()
                && elasticsearch.queried()
                && metrics.totalQps() != null
                && metrics.totalQps() <= 0D
                && logs.totalLogs() != null
                && logs.totalLogs() > 0) {
            insights.add(insight(
                    "INFO",
                    "当前业务流量较低",
                    "Prometheus rate 窗口内接口 QPS 接近 0，但 ES 仍有定时任务日志。",
                    "如需验证接口指标，可从商品页或运营后台发起一次请求。"));
        }

        if (insights.isEmpty()) {
            addFallbackInsight(insights, input);
        }
        return insights;
    }

    private void addFallbackInsight(List<InvestigationInsight> insights, Input input) {
        if (input.prometheus().queried() && input.elasticsearch().queried()) {
            insights.add(insight(
                    "OK",
                    "业务系统当前状态平稳",
                    "实例在线、错误率、资源指标和日志窗口未发现明显异常。",
                    "保持 Prometheus 和 ELK 持续采集，后续可基于业务峰值建立阈值。"));
        } else if (input.prometheus().queried()) {
            insights.add(insight(
                    "OK",
                    "监控指标未发现明显异常",
                    "本轮只查询了 Prometheus，实例、错误率和资源指标未发现明显异常。",
                    "如用户需要定位具体请求或异常堆栈，再触发 ES 日志查询。"));
        } else if (input.elasticsearch().queried()) {
            insights.add(insight(
                    "OK",
                    "日志窗口未发现明显异常",
                    "本轮只查询了 Elasticsearch，当前日志窗口没有 ERROR/WARN 级别证据。",
                    "如用户关心趋势、实例或资源状态，再触发 Prometheus 查询。"));
        } else if (input.mysqlSlowSql().queried()) {
            insights.add(insight(
                    "OK",
                    "MySQL 慢 SQL 未发现明显异常",
                    "本轮只查询了 MySQL 慢 SQL，当前窗口未发现高耗时 SQL。",
                    "如用户关心接口影响面，再触发 Prometheus 或 ES 查询。"));
        } else if (input.realtimeResultQueried()) {
            insights.add(insight(
                    "INFO",
                    "实时数据已由子 Agent 查询",
                    "当前运行已产生实时数据源 observation，但没有生成旧版结构化指标摘要。",
                    "以子 Agent 证据和最终报告为准；需要图表指标时可为该节点增加结构化输出映射。"));
        } else {
            insights.add(insight(
                    "INFO",
                    "本轮未查询实时运行数据",
                    "主 Agent 判断本轮问题不需要读取 ES/Prometheus，或实时数据源未被触发。",
                    "如需运行态判断，请补充时间窗口、接口、traceId 或指标问题。"));
        }
    }

    private InvestigationInsight insight(
            String level,
            String title,
            String detail,
            String suggestion) {
        return new InvestigationInsight(level, title, detail, suggestion);
    }

    public record Source(
            boolean queried,
            boolean unavailable,
            String message) {
    }

    public record Metrics(
            Integer instanceTotal,
            Integer instanceUp,
            Double totalQps,
            Double errorRate,
            Double heapMemoryUsagePercent) {
    }

    public record Logs(
            Long totalLogs,
            Long errorLogs) {
    }

    public record SlowSql(
            Long slowStatements,
            Double maxQueryTimeMs) {
    }

    public record Input(
            Source prometheus,
            Source elasticsearch,
            Source mysqlSlowSql,
            Metrics metrics,
            Logs logs,
            SlowSql slowSql,
            Integer rangeMinutes,
            boolean realtimeResultQueried) {
    }
}
