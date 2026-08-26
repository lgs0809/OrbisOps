import { describe, expect, it } from 'vitest';
import { serverEpoch, serverLocalTime } from './server-time';

describe('server audit timestamps', () => {
  it('shows a naive UTC database timestamp in the requested local timezone', () => {
    expect(serverEpoch('2026-11-03 04:42:08.0')).toBe(Date.parse('2026-11-03T04:42:08Z'));
    expect(serverLocalTime('2026-11-03 04:42:08.0', 'Asia/Shanghai')).toContain('12:42:08');
  });

  it('preserves explicit offsets and distinct instants during a daylight-saving overlap', () => {
    expect(serverEpoch('2026-11-03T12:42:08+08:00')).toBe(serverEpoch('2026-11-03T04:42:08Z'));
    expect(serverEpoch('2026-11-01T01:30:00-05:00') - serverEpoch('2026-11-01T01:30:00-04:00')).toBe(3600000);
  });

  it('keeps missing or invalid audit values unknown', () => {
    for (const input of [undefined, '', 'not-a-timestamp']) {
      expect(serverEpoch(input)).toBeNaN();
      expect(serverLocalTime(input)).toBe('-');
    }
  });
});
