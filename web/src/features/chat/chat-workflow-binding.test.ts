import { expect, it } from 'vitest';
import { chatWorkflowBinding, latestWorkflowForNewChat } from './chat-workflow-binding';

it('keeps a manually created session on v2 after the published head advances to v3', () => {
  expect(chatWorkflowBinding({ sessionId: 's', agentId: 'a', agentVersion: 2, engine: 'GRAPH', metadata: { executionType: 'WORKFLOW' } },
    { agentId: 'a', version: 3 })).toEqual({ agentDefinitionId: 'a', agentVersion: 2, engine: 'GRAPH' });
});
it('keeps the session identity when an execution selector changes before session creation succeeds', () => {
  expect(chatWorkflowBinding({ sessionId: 's', agentId: 'a', agentVersion: 2, metadata: { executionType: 'WORKFLOW' } },
    { agentId: 'b', version: 7 })).toEqual({ agentDefinitionId: 'a', agentVersion: 2, engine: 'GRAPH' });
});
it('uses the selected definition for a newly created workflow conversation', () => {
  expect(chatWorkflowBinding(undefined, { agentId: 'b', version: 7 })).toEqual({ agentDefinitionId: 'b', agentVersion: 7, engine: 'GRAPH' });
});

it('pins a new chat from a freshly fetched head and keeps an existing chat frozen', async () => {
  const latest = await latestWorkflowForNewChat('a', async () => ({ code: '0000', data: [
    { agentId: 'a', version: 5, definitionKind: 'SPECIALIZED_WORKFLOW', lifecycle: 'PUBLISHED' },
  ] }));
  expect(latest.version).toBe(5);
  expect(chatWorkflowBinding({ sessionId: 's', agentId: 'a', agentVersion: 2, metadata: { executionType: 'WORKFLOW' } }, latest).agentVersion).toBe(2);
});
it('does not create a new chat from cached authority when the current catalog rejects or withdraws it', async () => {
  await expect(latestWorkflowForNewChat('a', async () => ({ code: '0002', data: [] }))).rejects.toThrow('刷新');
  await expect(latestWorkflowForNewChat('a', async () => ({ code: '0000', data: [
    { agentId: 'a', version: 5, definitionKind: 'SPECIALIZED_WORKFLOW', lifecycle: 'DRAFT' },
  ] }))).rejects.toThrow('发布版本');
});
