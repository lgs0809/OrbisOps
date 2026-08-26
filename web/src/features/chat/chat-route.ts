import type { OpsChatMessage, OpsChatSession } from '../../services/ops-admin-service';

/** Resolve only against the authenticated project's loaded catalog. */
export const scopedChatSession = (sessions: OpsChatSession[], projectId: string,
  requestedSession: string, currentSession: string): string => {
  const allowed = sessions.filter((session) => session.projectId === projectId);
  if (allowed.some((session) => session.sessionId === requestedSession)) return requestedSession;
  if (allowed.some((session) => session.sessionId === currentSession)) return currentSession;
  return allowed[0]?.sessionId || '';
};

export const chatRunIds = (messages: OpsChatMessage[], sessionId: string): string[] =>
  messages.filter((message) => message.sessionId === sessionId)
    .map((message) => message.metadata?.runId)
    .filter((value): value is string => typeof value === 'string' && !!value);

/** Catalog and history callers supply validated identities; unrelated query filters are retained. */
export const scopedChatLocation = (search: URLSearchParams, projectId: string, sessionId: string,
  knownRunIds?: string[]): URLSearchParams => {
  const next = new URLSearchParams(search);
  if (projectId) next.set('projectId', projectId); else next.delete('projectId');
  if (!sessionId || next.get('sessionId') !== sessionId) next.delete('runId');
  if (sessionId) next.set('sessionId', sessionId); else next.delete('sessionId');
  if (knownRunIds && !knownRunIds.includes(next.get('runId') || '')) next.delete('runId');
  return next;
};
