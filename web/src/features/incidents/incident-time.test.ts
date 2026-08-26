import { describe, expect, it } from 'vitest';
import { incidentEpoch } from './incident-time';

describe('persisted incident UTC timestamps', () => {
  it('does not add the browser timezone to incident duration', () => {
    const start = incidentEpoch('2026-09-09 02:53:35');
    const now = Date.parse('2026-09-09T11:00:35+08:00');
    expect((now - start) / 60000).toBe(7);
    expect(start).toBe(incidentEpoch('2026-09-09T02:53:35Z'));
    expect(start).toBe(incidentEpoch('2026-09-09T10:53:35+08:00'));
  });

  it('leaves missing and malformed timestamps unknown', () => {
    expect(incidentEpoch(undefined)).toBeNaN();
    expect(incidentEpoch('not-a-time')).toBeNaN();
  });
});
