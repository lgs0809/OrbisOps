import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

import { channelQueryKeys } from './api/channel-queries';

describe('Channel Management query boundary', () => {
  it('keeps routed Channel server state behind the feature Query API', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/features/channels/pages/ChannelManagementPage.tsx'), 'utf8');

    expect(source).not.toContain('useEffect(');
    expect(source).not.toContain('useQuery({');
    expect(source).not.toContain('opsChannelService');
    expect(source).not.toContain('opsAdminService');
    expect(source).not.toContain('AdminUserService');
    expect(source).not.toContain('opsProjectService');
    expect(source).toContain('useChannelWorkflowOptionsQuery(scope.projectId)');
    expect(source).toContain('useSaveChannelMutation(scope.projectId)');
    expect(source).toContain("useChannelMessagesQuery(scope.projectId, messageChannel?.channelId || '', 100, Boolean(messageChannel))");
    expect(source).toContain("useChannelMessagesQuery(scope.projectId, identityChannel?.channelId || '', 200, Boolean(identityChannel))");
    expect(source).toContain('useBindChannelIdentityMutation');
    expect(source).toContain('useProcessChannelOutboxMutation');
  });

  it('keeps message history caches distinct by requested limit', () => {
    expect(channelQueryKeys.messages('project-1', 'channel-1', 100))
      .not.toEqual(channelQueryKeys.messages('project-1', 'channel-1', 200));
    expect(channelQueryKeys.messages('project-1', 'channel-1', 100))
      .toEqual(['channels', 'messages', 'project-1', 'channel-1', 100]);
  });

  it('keeps the legacy page as a thin compatibility entry instead of a duplicate implementation', () => {
    const source = readFileSync(resolve(process.cwd(), 'src/pages/channel-management.tsx'), 'utf8');

    expect(source).toContain("from '../features/channels/pages/ChannelManagementPage'");
    expect(source).not.toContain('useState(');
    expect(source).not.toContain('opsChannelService');
  });
});
