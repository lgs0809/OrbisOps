import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { Button, Space, Tag, Typography } from '@douyinfe/semi-ui';
import { OpsSectionCard } from '../../../components/ops-layout';
import { opsAdminService } from '../../../services/ops-admin-service';

const reasons: Record<string, string> = {
  SHARED_CORRELATION_ID: '共享故障标识', SHARED_TRACE_ID: '共享调用链', SHARED_RESOURCE: '共享资源',
  SHARED_CHANGE_ON_DEPENDENT_ENTITIES: '关联服务受到同次变更影响', SAME_SERVICE_INSTANCE: '同一服务实例',
  DEPENDENCY_AND_NEARBY_ONSET: '存在依赖关系且异常时间接近',
  ANOMALOUS_COMMON_DEPENDENCY_NEIGHBOUR: '共同依赖节点也发生异常',
  EXISTING_OCCURRENCE: '同次故障的后续告警', INDEPENDENT_INVESTIGATION: '事件起点',
};

export const AlertCorrelationPanel: React.FC<{
  projectId: string; scope: 'admin' | 'user'; onOpen: (incidentId: string) => void;
}> = ({ projectId, scope, onOpen }) => {
  const query = useQuery({
    queryKey: ['alert-correlations', projectId, scope], enabled: Boolean(projectId), refetchInterval: 15000,
    queryFn: async () => {
      const response = await opsAdminService.listAlertCorrelations(projectId, scope);
      if (response.code !== '0000') throw new Error(response.info || '读取事件归组失败');
      return response.data;
    },
  });
  const groups = (query.data || []).filter(group => group.members.length > 1);
  return <OpsSectionCard title="自动归组的事件">
    <Typography.Paragraph type="tertiary">
      根据依赖关系、异常时间和观测证据自动归组，调查无需等待人工确认。每条告警保留自己的调查与恢复记录；归组不代表根因已经证实。
    </Typography.Paragraph>
    {query.isError && <Typography.Text type="danger">事件归组暂时不可用，原有告警调查继续执行。</Typography.Text>}
    {!query.isError && groups.length === 0 && <Typography.Text type="tertiary">
      {query.isLoading ? '正在读取事件归组…' : '当前尚无满足关联条件的多告警事件。'}
    </Typography.Text>}
    {groups.map(group => <div key={group.groupId} style={{ borderTop: '1px solid var(--semi-color-border)', padding: '12px 0', overflowWrap: 'anywhere' }}>
      <Space wrap><Typography.Text strong>{group.anchor.title}</Typography.Text>
        <Tag color="blue">{group.members.length} 条关联告警</Tag><Tag>{group.environment || '环境未标识'}</Tag>
        <Tag>根因待证实</Tag></Space>
      {group.members.map(member => <div key={member.signal.incidentId} style={{ marginTop: 8 }}>
        <Button theme="borderless" onClick={() => onOpen(member.signal.incidentId)}>{member.signal.title}</Button>
        <Typography.Text type="tertiary">{member.signal.entityId} · {reasons[member.decision.reason] || member.decision.reason}
          {' · '}{member.occurrenceCount} 次通知{member.signal.recovery ? ' · 已收到恢复信号' : ''}</Typography.Text>
        <details><summary>归组依据</summary><Typography.Text size="small">
          {member.decision.evidenceRefs.join('、') || '首条告警建立事件；后续关联记录说明加入原因。'}
        </Typography.Text></details>
      </div>)}
    </div>)}
  </OpsSectionCard>;
};
