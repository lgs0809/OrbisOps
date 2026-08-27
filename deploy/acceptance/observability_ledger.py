"""Deterministic summaries of complete HTTP ledger queries; never supplies SLO verdicts."""
import datetime as dt
import math


def window_summary(query_result, window):
    seconds = window['endEpoch'] - window['startEpoch']
    count, errors = query_result['sampleCount'], query_result['errorCount']
    if any(isinstance(v, bool) or not isinstance(v, int) for v in (count, errors)):
        raise ValueError('LEDGER_COUNTS_INVALID')
    if seconds <= 0 or not 0 <= errors <= count:
        raise ValueError('LEDGER_WINDOW_INVALID')
    p95 = query_result.get('p95Seconds')
    if count and (isinstance(p95, bool) or not isinstance(p95, (int, float))
                  or not math.isfinite(p95) or p95 < 0):
        raise ValueError('LEDGER_PERCENTILE_INVALID')
    return {'scope': dict(window), 'windowSeconds': seconds, 'sampleCount': count,
            'errorCount': errors, 'qps': count / seconds,
            'errorRate': errors / count if count else None,
            'p95Seconds': p95 if count else None,
            'complete': True, 'versions': query_result.get('versions', []),
            'requestCountMethod': 'complete actual HTTP ledger; start inclusive, end exclusive',
            'p95Method': 'nearest rank over every actual HTTP duration; no sample truncation'}


def compare_windows(before, after):
    identity = ('projectId', 'environment', 'serviceId')
    if any(before['scope'][key] != after['scope'][key] for key in identity):
        raise ValueError('LEDGER_COMPARISON_SCOPE_MISMATCH')
    if before['scope']['endEpoch'] > after['scope']['startEpoch']:
        raise ValueError('LEDGER_COMPARISON_WINDOWS_OVERLAP')
    usable = before['sampleCount'] > 0 and after['sampleCount'] > 0
    denominator = before['p95Seconds'] if usable else None
    return {'before': before, 'after': after, 'complete': usable and denominator > 0,
            'p95RelativeIncrease': (after['p95Seconds'] / denominator - 1)
                if usable and denominator > 0 else None,
            'errorRateIncrease': after['errorRate'] - before['errorRate'] if usable else None,
            'causalAttribution': False}


def ledger_query(project, service, window, label):
    # Project/service are connector-owned exact allowlists. Times are already
    # validated finite epochs; free-form SQL and provider verdicts are excluded.
    dates = [dt.datetime.fromtimestamp(epoch, dt.timezone.utc).strftime('%Y-%m-%d %H:%M:%S.%f')
             for epoch in (window['startEpoch'], window['endEpoch'])]
    return ("SELECT '" + label + "' AS window_label,r.* FROM ops04_request r "
            "JOIN acceptance_service s ON s.service_id=r.service_id WHERE s.project_id='" + project
            + "' AND r.service_id='" + service + "' AND r.observed_at >= '" + dates[0]
            + "' AND r.observed_at < '" + dates[1] + "'")


def ledger_summary_sql(scoped_selects):
    return ("WITH scoped AS (" + ' UNION ALL '.join(scoped_selects)
            + "), ranked AS (SELECT scoped.*,ROW_NUMBER() OVER (PARTITION BY window_label "
            "ORDER BY duration_ms,event_id) duration_rank,ROW_NUMBER() OVER (PARTITION BY window_label "
            "ORDER BY sql_duration_ms DESC,event_id) sql_rank,COUNT(*) OVER (PARTITION BY window_label) "
            "sample_total FROM scoped), labels AS (SELECT 'after' label UNION ALL SELECT 'before') "
            "SELECT JSON_OBJECT('label',labels.label,'sampleCount',COUNT(r.event_id),"
            "'errorCount',COALESCE(SUM(r.http_status>=400),0),"
            "'slowSqlCount',COALESCE(SUM(r.sql_duration_ms>1000),0),"
            "'firstSlowOrderId',COALESCE(MIN(IF(r.sql_duration_ms>1000,r.order_id,NULL)),''),"
            "'p95Seconds',MAX(IF(r.duration_rank=CEIL(r.sample_total*0.95),r.duration_ms/1000.0,NULL)),"
            "'rows',(SELECT JSON_ARRAYAGG(JSON_OBJECT('traceId',sr.event_id,'orderId',sr.order_id,"
            "'observedAt',sr.observed_at,'status',sr.http_status,'durationMs',sr.duration_ms,"
            "'sqlDurationMs',sr.sql_duration_ms,'sqlText',sr.sql_text,'version',sr.version)) "
            "FROM ranked sr WHERE sr.window_label=labels.label AND sr.sql_rank<=12),"
            "'versions',(SELECT JSON_ARRAYAGG(v.version) FROM (SELECT DISTINCT window_label,version "
            "FROM scoped) v WHERE v.window_label=labels.label)) FROM labels "
            "LEFT JOIN ranked r ON r.window_label=labels.label GROUP BY labels.label;")
