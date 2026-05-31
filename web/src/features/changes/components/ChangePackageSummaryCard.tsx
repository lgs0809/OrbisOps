import React from 'react';
import styled from 'styled-components';
import { Button, Space, Tag, Typography } from '@douyinfe/semi-ui';

import { theme } from '../../../styles/theme';
import type { ChangePackageSummary } from '../model/change-package-summary';

const { Text } = Typography;

const Card = styled.div`
  padding: ${theme.spacing.sm} ${theme.spacing.base};
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.secondary};
  overflow-wrap: anywhere;
`;

const Meta = styled.div`
  margin-top: 4px;
  color: ${theme.colors.text.tertiary};
  font-size: 12px;
`;

type Props = {
  summary: ChangePackageSummary;
  actionLabel?: string;
  onOpen?: () => void;
};

export const ChangePackageSummaryCard: React.FC<Props> = ({ summary, actionLabel = '查看执行包', onOpen }) => (
  <Card>
    <Space vertical align="start" spacing="tight" style={{ width: '100%' }}>
      <Space wrap style={{ width: '100%', justifyContent: 'space-between' }}>
        <div>
          <Text strong>{summary.title}</Text>
          <Meta>{summary.packageId} · v{summary.version}{summary.targetEnvironment ? ` · ${summary.targetEnvironment}` : ''}</Meta>
        </div>
        <Space wrap>
          <Tag color={summary.statusColor as any}>{summary.statusLabel}</Tag>
          {summary.riskLevel && <Tag color={summary.riskColor as any}>{summary.riskLabel}</Tag>}
        </Space>
      </Space>
      {summary.reasonCode && <Text type="tertiary" size="small">{summary.reasonLabel}</Text>}
      {summary.updateTime && <Text type="tertiary" size="small">更新时间：{summary.updateTime}</Text>}
      {onOpen && <Button theme="borderless" onClick={onOpen}>{actionLabel}</Button>}
    </Space>
  </Card>
);
