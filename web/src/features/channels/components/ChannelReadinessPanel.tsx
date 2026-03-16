import React from 'react';
import { Button, Space, Tag, Typography } from '@douyinfe/semi-ui';

import { OpsStatusBadge } from '../../../components/ops-layout';
import type { OpsChannelReadiness, OpsChannelReadinessCheck } from '../../../services/ops-channel-service';

const { Text } = Typography;

const label: Record<OpsChannelReadinessCheck['kind'], string> = {
  CREDENTIALS: 'Credentials',
  CONNECTION: 'Connection',
  INBOUND: 'Inbound',
  OUTBOUND: 'Outbound',
  IDENTITY_ACCESS: 'Identity / Access',
};

const checkTag = (check: OpsChannelReadinessCheck) => {
  const color = check.status === 'PASS' ? 'green' : check.status === 'BLOCKED_EXTERNAL' ? 'red' : 'orange';
  const marker = check.status === 'PASS' ? '✓' : check.status === 'BLOCKED_EXTERNAL' ? '■' : '◷';
  return <Tag color={color}>{marker} {check.status === 'PASS' ? 'Pass' : check.status === 'BLOCKED_EXTERNAL' ? 'Blocked external' : 'Action required'}</Tag>;
};

export const ChannelReadinessPanel: React.FC<{
  readiness?: OpsChannelReadiness;
  loading?: boolean;
  onRefresh?: () => void;
}> = ({ readiness, loading, onRefresh }) => (
  <div data-testid="channel-readiness-panel">
    <Space wrap style={{ marginBottom: 12 }}>
      <OpsStatusBadge
        status={readiness?.ready ? 'VERIFIED' : readiness ? 'BLOCKED' : 'AWAITING_APPROVAL'}
        label={readiness?.ready ? 'Channel Ready' : readiness ? 'Not Ready' : 'Readiness not checked'}
      />
      {onRefresh && <Button size="small" loading={loading} onClick={onRefresh}>Refresh checks</Button>}
    </Space>
    <div style={{ display: 'grid', gap: 10 }}>
      {(readiness?.checks || []).map((check) => (
        <div key={check.kind} style={{ padding: 12, border: '1px solid var(--semi-color-border)', borderRadius: 8 }}>
          <Space wrap style={{ justifyContent: 'space-between', width: '100%' }}>
            <Text strong>{label[check.kind]}</Text>
            {checkTag(check)}
          </Space>
          <Text type="tertiary" size="small" style={{ display: 'block', marginTop: 6 }}>{check.detail || check.reasonCode}</Text>
        </div>
      ))}
      {!readiness && <Text type="tertiary">保存 Channel 后运行真实 readiness 检查。只有五项全部通过才会标记 Ready。</Text>}
    </div>
  </div>
);
