import React from 'react';
import { Button, Space, Tag, Typography } from '@douyinfe/semi-ui';

import { OpsStatusBadge } from '../../../components/ops-layout';
import type { OpsChannelReadiness, OpsChannelReadinessCheck } from '../../../services/ops-channel-service';

const { Text } = Typography;

const label: Record<OpsChannelReadinessCheck['kind'], string> = {
  CREDENTIALS: '凭据',
  CONNECTION: '连接',
  INBOUND: '接收消息',
  OUTBOUND: '发送消息',
  IDENTITY_ACCESS: '身份与访问',
};

const checkTag = (check: OpsChannelReadinessCheck) => {
  const color = check.status === 'PASS' ? 'green' : check.status === 'BLOCKED_EXTERNAL' ? 'red' : 'orange';
  const marker = check.status === 'PASS' ? '✓' : check.status === 'BLOCKED_EXTERNAL' ? '■' : '◷';
  return <Tag color={color}>{marker} {check.status === 'PASS' ? '通过' : check.status === 'BLOCKED_EXTERNAL' ? '外部阻塞' : '需要处理'}</Tag>;
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
        label={readiness?.ready ? 'Channel 已就绪' : readiness ? '尚未就绪' : '尚未检查就绪状态'}
      />
      {onRefresh && <Button size="small" loading={loading} onClick={onRefresh}>刷新检查</Button>}
    </Space>
    <div style={{ display: 'grid', gap: 10 }}>
      {(readiness?.checks || []).map((check) => (
        <div key={check.kind} style={{ padding: 12, border: '1px solid var(--semi-color-border)', borderRadius: 8 }}>
          <Space wrap style={{ justifyContent: 'space-between', width: '100%' }}>
            <Text strong>{label[check.kind]}</Text>
            {checkTag(check)}
          </Space>
          <Text type="tertiary" size="small" style={{ display: 'block', marginTop: 6 }}>{check.detail || '该项检查未通过，请核对 Channel 配置。'}</Text>
        </div>
      ))}
      {!readiness && <Text type="tertiary">请先保存 Channel，再执行真实就绪检查；五项检查全部通过后，Channel 才会标记为已就绪。</Text>}
    </div>
  </div>
);
