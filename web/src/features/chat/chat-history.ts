import type { OpsGraphEvent, OpsRuntimeEvent } from '../../services/ops-admin-service';

const text = (value: unknown) => typeof value === 'string' ? value : '';

export const toRuntimeEvent = (event: OpsGraphEvent): OpsRuntimeEvent => {
  const payload: Record<string, unknown> = {
    ...(event.payload || {}),
    sequence: event.sequence,
    analysisId: event.analysisId,
  };
  if (event.startedAt) payload.startedAt = event.startedAt;
  if (event.finishedAt) payload.finishedAt = event.finishedAt;
  if (event.durationMs !== undefined && event.durationMs !== null) payload.durationMs = event.durationMs;
  return {
    eventType: event.eventType,
    runId: event.runId,
    nodeId: event.nodeId,
    nodeType: event.nodeType,
    agent: event.agent,
    source: event.source,
    status: event.status,
    summary: event.summary,
    sessionId: text(event.payload?.sessionId) || event.analysisId,
    timestamp: event.finishedAt
      || event.startedAt
      || text(event.payload?.observedAt)
      || text(event.payload?.timestamp),
    payload,
  };
};

export const restoreRuntimeEvents = (events: OpsGraphEvent[] | undefined | null): OpsRuntimeEvent[] =>
  [...(events || [])]
    .sort((left, right) => (left.sequence || 0) - (right.sequence || 0))
    .map(toRuntimeEvent);
