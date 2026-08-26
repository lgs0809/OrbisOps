import { useRef, useState } from 'react';
import { Button, Typography } from '@douyinfe/semi-ui';
import { getStoredUserInfo } from '../../../services/auth-session';
import { opsAdminService, type OpsAgentDefinition } from '../../../services/ops-admin-service';
import { userFacingError } from '../../../utils/user-facing-error';

export const WorkflowManualTestButton = ({ projectId, definition, disabled, onNavigate, compact = false }: {
  projectId: string;
  definition: OpsAgentDefinition;
  disabled?: boolean;
  onNavigate: (url: string) => void;
  compact?: boolean;
}) => {
  const creating = useRef(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const published = Boolean(projectId && definition.projectId === projectId && definition.agentId
    && Number.isInteger(definition.version) && Number(definition.version) > 0 && definition.lifecycle === 'PUBLISHED');

  const open = async () => {
    if (!published || disabled || creating.current) return;
    creating.current = true;
    setBusy(true);
    setError('');
    try {
      const response = await opsAdminService.createChatSession({
        userId: getStoredUserInfo().username,
        projectId,
        agentId: definition.agentId,
        agentVersion: definition.version,
        title: `${definition.name || definition.agentId} · v${definition.version}${compact ? '' : ' 手动测试'}`,
        mode: 'AGENT',
        engine: definition.engine || 'GRAPH',
        metadata: { executionType: 'WORKFLOW', executionName: definition.name || definition.agentId },
      });
      if (response.code !== '0000') throw new Error(response.info || '创建测试对话失败。');
      if (typeof response.data !== 'string' || !response.data.trim()) throw new Error('服务端未返回测试会话，请重试。');
      onNavigate(`/chat?${new URLSearchParams({ projectId, sessionId: response.data })}`);
    } catch (cause) {
      setError(userFacingError(cause, '创建测试对话失败，请稍后重试。'));
    } finally {
      creating.current = false;
      setBusy(false);
    }
  };

  return <>
    <Button onClick={() => void open()} disabled={!published || disabled || busy} loading={busy}>{compact ? '使用工作流' : '打开对话手动执行'}</Button>
    {!compact && <Typography.Text type="tertiary">{published
      ? `新建绑定 v${definition.version} 已发布版本的对话，进入后手动发送测试输入。`
      : '请先校验并发布当前工作流，再创建测试对话。'}</Typography.Text>}
    {error && <div role="alert">{error}</div>}
  </>;
};
