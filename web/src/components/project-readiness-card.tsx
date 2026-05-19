import React from 'react';
import styled from 'styled-components';
import { Button, Space, Tag, Typography } from '@douyinfe/semi-ui';

import { OpsProjectReadiness } from '../services/ops-project-service';
import { theme } from '../styles/theme';

const { Paragraph, Text } = Typography;

const Card = styled.div`
  border: 1px solid ${theme.colors.border.secondary};
  border-radius: ${theme.borderRadius.base};
  background: ${theme.colors.bg.secondary};
  padding: ${theme.spacing.base};
  min-width: 0;
`;

const CheckRow = styled.div`
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr);
  gap: 8px;
  align-items: start;
  padding: 8px 0;
  border-bottom: 1px solid ${theme.colors.border.secondary};

  &:last-of-type { border-bottom: 0; }
  .detail { overflow-wrap: anywhere; }
`;

export interface ProjectReadinessCardProps {
  title: string;
  readiness?: OpsProjectReadiness;
  fallbackAction: string;
  onAction: () => void;
}

const levelLabel = (level?: OpsProjectReadiness['checks'][number]['level']) => ({
  NOT_CONFIGURED: '未配置',
  CONFIGURED: '已配置',
  CONNECTIVITY_VERIFIED: '连通已验证',
  QUERY_VERIFIED: '真实查询已验证',
  AUTHORIZED: '权限已验证',
  ENVIRONMENT_VALIDATED: '环境已验收',
} as Record<string, string>)[String(level || '')] || '';

export const ProjectReadinessCard: React.FC<ProjectReadinessCardProps> = ({
  title,
  readiness,
  fallbackAction,
  onAction,
}) => (
  <Card>
    <Space wrap style={{ justifyContent: 'space-between', width: '100%', marginBottom: 8 }}>
      <Text strong>{title}</Text>
      <Tag color={readiness?.ready ? 'green' : 'orange'}>{readiness?.ready ? '已就绪' : '未就绪'}</Tag>
    </Space>
    {(readiness?.checks || []).map((check) => (
      <CheckRow key={check.key}>
        <Text>{check.ready ? '✓' : '×'}</Text>
        <div>
          <Space wrap spacing={6}>
            <Text strong={check.ready}>{check.label}</Text>
            {check.level && <Tag color={check.ready ? 'green' : check.level === 'NOT_CONFIGURED' ? 'grey' : 'orange'}>{levelLabel(check.level)}</Tag>}
          </Space>
          {check.detail && <div className="detail"><Text type="tertiary" size="small">{check.detail}</Text></div>}
        </div>
      </CheckRow>
    ))}
    {!!readiness?.missing?.length && (
      <Paragraph type="tertiary" style={{ marginTop: 10, marginBottom: 8 }}>
        还缺：{readiness.missing.join('、')}
      </Paragraph>
    )}
    <Button theme={readiness?.ready ? 'solid' : 'borderless'} type={readiness?.ready ? 'primary' : 'tertiary'} onClick={onAction}>
      {readiness?.nextAction || fallbackAction}
    </Button>
  </Card>
);
