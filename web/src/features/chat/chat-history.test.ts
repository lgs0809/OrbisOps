import { describe, expect, it } from 'vitest';

import type { OpsGraphEvent } from '../../services/ops-admin-service';
import { restoreRuntimeEvents } from './chat-history';

const graphEvent = (sequence: number, eventType: string, payload: Record<string, unknown> = {}): OpsGraphEvent => ({
  sequence,
  eventType,
  runId: 'run-1',
  analysisId: 'session-1',
  status: 'SUCCEEDED',
  summary: eventType,
  payload,
});

describe('restoreRuntimeEvents', () => {
  it('restores persisted graph events in sequence order and preserves trace evidence', () => {
    const restored = restoreRuntimeEvents([
      graphEvent(58, 'DONE'),
      graphEvent(46, 'SOURCE_QUERY_FINISHED', {
        sourceType: 'PROMETHEUS',
        resultId: 'tool-result-1',
        outputHash: 'hash-1',
        evidenceId: 'evidence-1',
        observedAt: '2026-08-13 09:31:09',
      }),
    ]);

    expect(restored.map((event) => event.eventType)).toEqual(['SOURCE_QUERY_FINISHED', 'DONE']);
    expect(restored[0].sessionId).toBe('session-1');
    expect(restored[0].timestamp).toBe('2026-08-13 09:31:09');
    expect(restored[0].payload).toMatchObject({
      sequence: 46,
      analysisId: 'session-1',
      resultId: 'tool-result-1',
      outputHash: 'hash-1',
      evidenceId: 'evidence-1',
    });
  });
});
