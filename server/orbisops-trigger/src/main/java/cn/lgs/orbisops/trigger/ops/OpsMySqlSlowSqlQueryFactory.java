package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.application.analysis.MySqlSlowSqlQuery;

/** Builds the typed application query from bounded settings and run input. */
final class OpsMySqlSlowSqlQueryFactory {

    MySqlSlowSqlQuery create(
            OpsAgentRunRequestDTO request,
            OpsMySqlSlowSqlSettings settings) {
        OpsMySqlSlowSqlSettings effectiveSettings = settings == null
                ? OpsMySqlSlowSqlSettings.defaults()
                : settings;
        return new MySqlSlowSqlQuery(
                safeRangeMinutes(request),
                effectiveSettings.thresholdMs(),
                effectiveSettings.sampleSize(),
                queryTimeoutSeconds(request),
                effectiveSettings.performanceSchemaFallback());
    }

    private int safeRangeMinutes(OpsAgentRunRequestDTO request) {
        Integer configured = request == null ? null : request.getRangeMinutes();
        return Math.max(1, Math.min(configured == null ? 15 : configured, 1440));
    }

    private int queryTimeoutSeconds(OpsAgentRunRequestDTO request) {
        Integer configured = request == null ? null : request.getNodeTimeoutSeconds();
        return Math.max(1, Math.min(configured == null ? 30 : configured, 300));
    }
}
