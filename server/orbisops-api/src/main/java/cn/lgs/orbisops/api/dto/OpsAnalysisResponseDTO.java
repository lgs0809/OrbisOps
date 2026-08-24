package cn.lgs.orbisops.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 运维 Agent 分析响应。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OpsAnalysisResponseDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String analysisId;
    private String runtimeStatus;
    private String agentDefinitionId;
    private Integer agentVersion;
    private String agentRuntime;
    private Integer rangeMinutes;
    private String promWindow;
    private String generatedAt;
    private DataSourceStatusDTO elasticsearchStatus;
    private DataSourceStatusDTO prometheusStatus;
    private DataSourceStatusDTO mysqlSlowSqlStatus;
    private LogSummaryDTO logSummary;
    private MetricSummaryDTO metricSummary;
    private SlowSqlSummaryDTO slowSqlSummary;
    private List<EndpointMetricDTO> endpointMetrics;
    private List<LogSampleDTO> recentLogs;
    private List<SlowSqlSampleDTO> slowSqlSamples;
    private List<InsightDTO> insights;
    private String aiPrompt;
    private String markdownReport;
    private OpsInvestigationPlanDTO investigationPlan;
    private List<InvestigationResultDTO> investigationResults;
    private List<AgentExecutionStepDTO> agentExecutionSteps;
    private List<String> executionNotes;
    private Map<String, Object> structuredReport;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DataSourceStatusDTO implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String name;
        private String url;
        private Boolean available;
        private String message;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class LogSummaryDTO implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long totalLogs;
        private Long errorLogs;
        private Long warnLogs;
        private Map<String, Long> levelCounts;
        private List<BucketDTO> topLoggers;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MetricSummaryDTO implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Integer instanceTotal;
        private Integer instanceUp;
        private Double totalQps;
        private Double errorQps;
        private Double errorRate;
        private Double heapMemoryUsagePercent;
        private Double processCpuUsagePercent;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SlowSqlSummaryDTO implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private Long totalStatements;
        private Long slowStatements;
        private Double avgQueryTimeMs;
        private Double maxQueryTimeMs;
        private Long rowsExamined;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EndpointMetricDTO implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String uri;
        private String method;
        private String status;
        private Double qps;
        private Double avgResponseMs;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class LogSampleDTO implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String timestamp;
        private String level;
        private String loggerName;
        private String message;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class SlowSqlSampleDTO implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String startTime;
        private String databaseName;
        private String userHost;
        private String digest;
        private String sqlText;
        private Double queryTimeMs;
        private Long rowsExamined;
        private Long rowsSent;
        private Long countStar;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BucketDTO implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String key;
        private Long count;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class InsightDTO implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String level;
        private String title;
        private String detail;
        private String suggestion;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OpsInvestigationPlanDTO implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String intent;
        private String reason;
        private Boolean changeRequested;
        private String changeIntent;
        private List<InvestigationTaskDTO> tasks;
        private List<InvestigationTaskDTO> conditionalTasks;
        private List<InvestigationTaskDTO> skippedTasks;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class InvestigationTaskDTO implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String source;
        private String agent;
        private String goal;
        private String reason;
        private Integer priority;
        private String condition;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class InvestigationResultDTO implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String source;
        private String agent;
        private String status;
        private String summary;
        private List<String> evidence;
        private List<InvestigationAttemptDTO> attempts;
        private List<String> gaps;
        private List<String> suggestedAdjustments;
        private Boolean shouldRetry;
        private Double confidence;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class InvestigationAttemptDTO implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String query;
        private Integer resultCount;
        private String reason;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AgentExecutionStepDTO implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private String eventType;
        private String nodeId;
        private String nodeType;
        private String agent;
        private String source;
        private String status;
        private String summary;
        private String sourceType;
        private String resultId;
        private String evidenceId;
        private String outputHash;
        private Boolean verified;
        private Map<String, Object> outcome;
        private String changePackageBehavior;
        private String startedAt;
        private String finishedAt;
        private Long durationMs;
    }

}
