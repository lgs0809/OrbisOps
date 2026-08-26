import { useEffect, useState } from 'react';
import { opsAdminService, type OpsChatSession } from '../../services/ops-admin-service';

type Access = 'loading' | 'writable' | 'readonly' | 'unavailable';

/** A UI capability projection; the authenticated server remains authoritative on every write. */
export function useChatSessionAccess(session: OpsChatSession | undefined, actor: string, selectedSessionId: string) {
  const key = JSON.stringify([session?.projectId, selectedSessionId, session?.userId, actor, session?.stateVersion]);
  const owner = Boolean(actor && session?.userId === actor);
  const [resolved, setResolved] = useState<{ key: string; access: Access }>();
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    if (!selectedSessionId || !session || owner || !actor) return;
    let active = true;
    setResolved({ key, access: 'loading' });
    void opsAdminService.listChatSessionParticipants(session.sessionId).then((response) => {
      if (!active) return;
      if (response.code !== '0000' || !Array.isArray(response.data)) throw new Error('读取协作权限失败');
      const participant = response.data.find((item) => item.userId === actor && item.status === 'ACTIVE');
      setResolved({ key, access: participant && ['OWNER', 'EDITOR'].includes(participant.role) ? 'writable' : 'readonly' });
    }).catch(() => { if (active) setResolved({ key, access: 'unavailable' }); });
    return () => { active = false; };
  }, [actor, key, owner, revision, selectedSessionId, session]);
  const access: Access = !selectedSessionId ? 'writable' : owner ? 'writable'
    : !actor ? 'readonly' : resolved?.key === key ? resolved.access : 'loading';
  return { access, canWrite: access === 'writable', retry: () => setRevision((current) => current + 1) };
}
