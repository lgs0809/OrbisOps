import { describe, expect, it } from 'vitest';
import { chatRunIds, scopedChatLocation, scopedChatSession } from './chat-route';

describe('project-bound chat routes', () => {
  const sessions = [{ sessionId: 'a1', projectId: 'a' }, { sessionId: 'b1', projectId: 'b' }, { sessionId: 'a2', projectId: 'a' }];
  it('preserves a valid explicitly requested session before the remembered session', () => {
    expect(scopedChatSession(sessions, 'a', 'a2', 'a1')).toBe('a2');
  });
  it('never selects another project session from either the URL or remembered state', () => {
    expect(scopedChatSession(sessions, 'a', 'b1', 'b1')).toBe('a1');
    expect(scopedChatSession(sessions, 'missing', 'b1', 'a1')).toBe('');
  });
  it('does not carry a foreign run into the replacement conversation', () => {
    const source = new URLSearchParams('projectId=b&sessionId=b1&runId=rb&acceptance=trace');
    expect(scopedChatLocation(source, 'a', 'a1').toString()).toBe('projectId=a&sessionId=a1&acceptance=trace');
    expect(source.get('runId')).toBe('rb');
  });
  it('waits for actual scoped history to reject a run and preserves a valid older run', () => {
    const route = new URLSearchParams('projectId=a&sessionId=a1&runId=older');
    expect(scopedChatLocation(route, 'a', 'a1').get('runId')).toBe('older');
    expect(scopedChatLocation(route, 'a', 'a1', ['older', 'newer']).get('runId')).toBe('older');
    expect(scopedChatLocation(route, 'a', 'a1', ['different']).has('runId')).toBe(false);
  });
  it('only trusts run references belonging to the selected session', () => {
    expect(chatRunIds([{ messageId: '1', sessionId: 'a1', role: 'assistant', content: '', metadata: { runId: 'ra' } },
      { messageId: '2', sessionId: 'b1', role: 'assistant', content: '', metadata: { runId: 'rb' } }], 'a1')).toEqual(['ra']);
  });
});
